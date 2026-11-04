#ifndef WEAVE_HTTP_H
#define WEAVE_HTTP_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <sys/socket.h>

typedef struct WeaveHTTPRequest WeaveHTTPRequest;
typedef bool (*WeaveAddressFilter)(const struct sockaddr *, socklen_t);

// Optional injected transport dependencies for isolated TLS tests. The app uses NULL defaults.
// No setting disables certificate/hostname verification or enables proxy environment variables.
typedef struct {
    const char *certificate_authority_file;
    const char *resolve_entry;
    WeaveAddressFilter address_filter;
} WeaveHTTPOptions;

typedef enum {
    WEAVE_HTTP_OK,
    WEAVE_HTTP_CANCELLED,
    WEAVE_HTTP_TOO_LARGE,
    WEAVE_HTTP_PRIVATE_ADDRESS,
    WEAVE_HTTP_TLS_FAILED,
    WEAVE_HTTP_TIMEOUT,
    WEAVE_HTTP_FAILED
} WeaveHTTPResult;

WeaveHTTPRequest *weave_http_create(const WeaveHTTPOptions *options);
void weave_http_destroy(WeaveHTTPRequest *request);
void weave_http_cancel(WeaveHTTPRequest *request);
bool weave_http_cancelled(const WeaveHTTPRequest *request);
// Only perform and read a request on one worker at a time. Cancellation is atomic and concurrent.
WeaveHTTPResult weave_http_perform(WeaveHTTPRequest *request, const char *url, long timeout_ms, const char *resolve_entry);
const uint8_t *weave_http_body(const WeaveHTTPRequest *request);
size_t weave_http_body_size(const WeaveHTTPRequest *request);
long weave_http_status(const WeaveHTTPRequest *request);
const char *weave_http_redirect(const WeaveHTTPRequest *request);
bool weave_http_is_public_address(const struct sockaddr *address, socklen_t length);
bool weave_http_is_public_ip(const char *literal);

#endif
