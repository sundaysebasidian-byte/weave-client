#include "WeaveHTTP.h"
#include <arpa/inet.h>
#include <curl/curl.h>
#include <fcntl.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#define BODY_LIMIT (5U * 1024U * 1024U)
#define HEADER_LIMIT (64U * 1024U)

struct WeaveHTTPRequest {
    atomic_bool cancelled;
    uint8_t *body;
    size_t body_size, capacity, header_size;
    long status;
    char *redirect, *ca_file, *resolve_entry;
    bool too_large, blocked_address;
    WeaveAddressFilter address_filter;
};

static pthread_once_t curl_once = PTHREAD_ONCE_INIT;
static CURLcode curl_initialization;
static void initialize_curl(void) { curl_initialization = curl_global_init(CURL_GLOBAL_DEFAULT); }

static bool public_ipv4(const uint8_t *b) {
    return !(b[0] == 0 || b[0] == 10 || b[0] == 127 || b[0] >= 224 ||
             (b[0] == 100 && (b[1] & 0xC0) == 64) ||
             (b[0] == 169 && b[1] == 254) || (b[0] == 172 && (b[1] & 0xF0) == 16) ||
             (b[0] == 192 && (b[1] == 168 ||
                 (b[1] == 0 && (b[2] == 0 || b[2] == 2)) || (b[1] == 88 && b[2] == 99))) ||
             (b[0] == 198 && (b[1] == 18 || b[1] == 19 || (b[1] == 51 && b[2] == 100))) ||
             (b[0] == 203 && b[1] == 0 && b[2] == 113));
}

bool weave_http_is_public_address(const struct sockaddr *address, socklen_t length) {
    if (!address || length < offsetof(struct sockaddr, sa_family) + sizeof(address->sa_family)) return false;
    if (address->sa_family == AF_INET && length >= sizeof(struct sockaddr_in)) {
        return public_ipv4((const uint8_t *)&((const struct sockaddr_in *)address)->sin_addr);
    }
    if (address->sa_family != AF_INET6 || length < sizeof(struct sockaddr_in6)) return false;
    const struct sockaddr_in6 *v6 = (const struct sockaddr_in6 *)address;
    const uint8_t *b = v6->sin6_addr.s6_addr;
    if (v6->sin6_scope_id != 0) return false;
    static const uint8_t nat64[12] = {0x00, 0x64, 0xff, 0x9b};
    if (IN6_IS_ADDR_V4MAPPED(&v6->sin6_addr) || memcmp(b, nat64, 12) == 0) return public_ipv4(b + 12);
    // Restrict native IPv6 to global unicast; exclude transition and special-use ranges.
    if ((b[0] & 0xE0) != 0x20) return false;
    if (b[0] == 0x20 && b[1] == 0x02) return false; // 6to4 can embed a private IPv4 target.
    if (b[0] == 0x20 && b[1] == 0x01 &&
        (b[2] < 2 || (b[2] == 0x0d && b[3] == 0xb8))) return false;
    if (b[0] == 0x3f && b[1] == 0xff && (b[2] & 0xf0) == 0) return false; // Documentation /20.
    return true;
}

bool weave_http_is_public_ip(const char *literal) {
    if (!literal) return false;
    struct sockaddr_in v4 = {.sin_family = AF_INET};
    if (inet_pton(AF_INET, literal, &v4.sin_addr) == 1) return weave_http_is_public_address((struct sockaddr *)&v4, sizeof(v4));
    struct sockaddr_in6 v6 = {.sin6_family = AF_INET6};
    if (inet_pton(AF_INET6, literal, &v6.sin6_addr) == 1) return weave_http_is_public_address((struct sockaddr *)&v6, sizeof(v6));
    return false;
}

WeaveHTTPRequest *weave_http_create(const WeaveHTTPOptions *options) {
    WeaveHTTPRequest *request = calloc(1, sizeof(*request));
    if (!request) return NULL;
    atomic_init(&request->cancelled, false);
    request->address_filter = weave_http_is_public_address;
    if (options) {
        if (options->certificate_authority_file) request->ca_file = strdup(options->certificate_authority_file);
        if (options->resolve_entry) request->resolve_entry = strdup(options->resolve_entry);
        if ((options->certificate_authority_file && !request->ca_file) || (options->resolve_entry && !request->resolve_entry)) {
            weave_http_destroy(request);
            return NULL;
        }
        if (options->address_filter) request->address_filter = options->address_filter;
    }
    return request;
}

void weave_http_destroy(WeaveHTTPRequest *request) {
    if (!request) return;
    free(request->body);
    free(request->redirect);
    free(request->ca_file);
    free(request->resolve_entry);
    free(request);
}

void weave_http_cancel(WeaveHTTPRequest *request) { atomic_store(&request->cancelled, true); }
bool weave_http_cancelled(const WeaveHTTPRequest *request) { return atomic_load(&request->cancelled); }
const uint8_t *weave_http_body(const WeaveHTTPRequest *request) { return request->body; }
size_t weave_http_body_size(const WeaveHTTPRequest *request) { return request->body_size; }
long weave_http_status(const WeaveHTTPRequest *request) { return request->status; }
const char *weave_http_redirect(const WeaveHTTPRequest *request) { return request->redirect; }

static curl_socket_t open_public_socket(void *context, curlsocktype purpose, struct curl_sockaddr *address) {
    WeaveHTTPRequest *request = context;
    if (weave_http_cancelled(request) || purpose != CURLSOCKTYPE_IPCXN || address->socktype != SOCK_STREAM) return CURL_SOCKET_BAD;
    if (!request->address_filter(&address->addr, address->addrlen)) {
        request->blocked_address = true;
        return CURL_SOCKET_BAD;
    }
    // This same sockaddr is subsequently passed to connect by libcurl; no second DNS lookup.
    int fd = socket(address->family, address->socktype, address->protocol);
    if (fd >= 0 && fcntl(fd, F_SETFD, FD_CLOEXEC) == -1) { close(fd); return CURL_SOCKET_BAD; }
    return fd;
}

static size_t receive_body(char *data, size_t size, size_t count, void *context) {
    WeaveHTTPRequest *request = context;
    if (weave_http_cancelled(request)) return 0;
    if (size && count > (BODY_LIMIT - request->body_size) / size) { request->too_large = true; return 0; }
    size_t length = size * count, needed = request->body_size + length;
    if (needed > request->capacity) {
        size_t capacity = request->capacity ? request->capacity * 2 : 16384;
        if (capacity < needed) capacity = needed;
        if (capacity > BODY_LIMIT) capacity = BODY_LIMIT;
        uint8_t *buffer = realloc(request->body, capacity);
        if (!buffer) return 0;
        request->body = buffer;
        request->capacity = capacity;
    }
    if (length) memcpy(request->body + request->body_size, data, length);
    request->body_size = needed;
    return length;
}

static size_t receive_header(char *data, size_t size, size_t count, void *context) {
    (void)data;
    WeaveHTTPRequest *request = context;
    if (weave_http_cancelled(request)) return 0;
    if (size && count > (HEADER_LIMIT - request->header_size) / size) { request->too_large = true; return 0; }
    request->header_size += size * count;
    return size * count;
}

static int progress(void *context, curl_off_t total, curl_off_t now, curl_off_t upload_total, curl_off_t upload_now) {
    (void)total; (void)now; (void)upload_total; (void)upload_now;
    return weave_http_cancelled(context) ? 1 : 0;
}

WeaveHTTPResult weave_http_perform(WeaveHTTPRequest *request, const char *url, long timeout_ms, const char *resolve_entry) {
    if (weave_http_cancelled(request)) return WEAVE_HTTP_CANCELLED;
    if (!url || timeout_ms <= 0) return WEAVE_HTTP_TIMEOUT;
    pthread_once(&curl_once, initialize_curl);
    if (curl_initialization != CURLE_OK) return WEAVE_HTTP_FAILED;
    request->body_size = request->header_size = 0;
    request->status = 0;
    request->too_large = request->blocked_address = false;
    free(request->redirect);
    request->redirect = NULL;
    CURL *curl = curl_easy_init();
    if (!curl) return WEAVE_HTTP_FAILED;
    struct curl_slist *resolve = NULL;
    struct curl_slist *headers = NULL;
    CURLcode code = CURLE_OK;
    char *redirect = NULL;
    #define SET(option, value) do { code = curl_easy_setopt(curl, option, value); if (code != CURLE_OK) goto done; } while (0)
    SET(CURLOPT_URL, url);
    SET(CURLOPT_PROTOCOLS_STR, "https");
    SET(CURLOPT_REDIR_PROTOCOLS_STR, "https");
    SET(CURLOPT_FOLLOWLOCATION, 0L); // Swift validates each redirect before issuing the next request.
    SET(CURLOPT_DISALLOW_USERNAME_IN_URL, 1L);
    SET(CURLOPT_PROXY, "");
    SET(CURLOPT_NETRC, (long)CURL_NETRC_IGNORED);
    SET(CURLOPT_NOSIGNAL, 1L);
    SET(CURLOPT_CONNECTTIMEOUT_MS, timeout_ms < 10000 ? timeout_ms : 10000L);
    SET(CURLOPT_TIMEOUT_MS, timeout_ms);
    SET(CURLOPT_SSL_VERIFYPEER, 1L);
    SET(CURLOPT_SSL_VERIFYHOST, 2L);
    SET(CURLOPT_SSLVERSION, (long)CURL_SSLVERSION_TLSv1_2);
    SET(CURLOPT_HTTP_VERSION, (long)CURL_HTTP_VERSION_2TLS);
    SET(CURLOPT_USERAGENT, "Weave/0.1 (ClashMetaForAndroid compatible)");
    headers = curl_slist_append(NULL, "Accept: application/yaml,text/yaml,text/plain;q=0.9,*/*;q=0.1");
    if (!headers) { code = CURLE_OUT_OF_MEMORY; goto done; }
    SET(CURLOPT_HTTPHEADER, headers);
    SET(CURLOPT_ACCEPT_ENCODING, "");
    SET(CURLOPT_MAXFILESIZE_LARGE, (curl_off_t)BODY_LIMIT);
    SET(CURLOPT_OPENSOCKETFUNCTION, open_public_socket);
    SET(CURLOPT_OPENSOCKETDATA, request);
    SET(CURLOPT_WRITEFUNCTION, receive_body);
    SET(CURLOPT_WRITEDATA, request);
    SET(CURLOPT_HEADERFUNCTION, receive_header);
    SET(CURLOPT_HEADERDATA, request);
    SET(CURLOPT_XFERINFOFUNCTION, progress);
    SET(CURLOPT_XFERINFODATA, request);
    SET(CURLOPT_NOPROGRESS, 0L);
    if (request->ca_file) SET(CURLOPT_CAINFO, request->ca_file);
    const char *addresses = resolve_entry ? resolve_entry : request->resolve_entry;
    if (addresses) {
        resolve = curl_slist_append(NULL, addresses);
        if (!resolve) { code = CURLE_OUT_OF_MEMORY; goto done; }
        SET(CURLOPT_RESOLVE, resolve);
    }
    code = curl_easy_perform(curl);
    if (code == CURLE_OK) {
        code = curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &request->status);
        if (code != CURLE_OK) goto done;
        code = curl_easy_getinfo(curl, CURLINFO_REDIRECT_URL, &redirect);
        if (redirect) {
            request->redirect = strdup(redirect);
            if (!request->redirect) code = CURLE_OUT_OF_MEMORY;
        }
    }
done:
    curl_easy_cleanup(curl);
    curl_slist_free_all(resolve);
    curl_slist_free_all(headers);
    #undef SET
    if (weave_http_cancelled(request)) return WEAVE_HTTP_CANCELLED;
    if (request->too_large || code == CURLE_FILESIZE_EXCEEDED) return WEAVE_HTTP_TOO_LARGE;
    if (code == CURLE_OK) return WEAVE_HTTP_OK;
    if (code == CURLE_COULDNT_CONNECT && request->blocked_address) return WEAVE_HTTP_PRIVATE_ADDRESS;
    if (code == CURLE_OPERATION_TIMEDOUT) return WEAVE_HTTP_TIMEOUT;
    if (code == CURLE_PEER_FAILED_VERIFICATION || code == CURLE_SSL_CONNECT_ERROR ||
        code == CURLE_SSL_CACERT_BADFILE || code == CURLE_SSL_CERTPROBLEM) return WEAVE_HTTP_TLS_FAILED;
    return WEAVE_HTTP_FAILED;
}
