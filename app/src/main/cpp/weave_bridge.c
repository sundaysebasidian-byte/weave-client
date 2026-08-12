#include <android/log.h>
#include <jni.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define TAG "WeaveCore"
#define CORE_REVISION "e26714a"

typedef const char *c_string;

extern void coreInit(c_string home, c_string version_name, c_string git_version, int sdk_version);
extern void reset(void);
extern void load(void *completion, c_string path);
extern void validateConfiguration(void *completion, c_string path);
extern int startTun(
    int fd,
    c_string stack,
    c_string gateway,
    c_string portal,
    c_string dns,
    void *callback
);
extern void stopTun(void);
extern void notifyInstalledAppsChanged(c_string uids);
extern void queryNow(uint64_t *upload, uint64_t *download);
extern void queryTotal(uint64_t *upload, uint64_t *download);
extern char *queryGroup(c_string name, c_string sort_mode);
extern void healthCheck(void *completion, c_string name);

/*
 * CMFA owns the exported callback wrappers and the memory passed to them.
 * A host bridge must register implementations through these function pointers;
 * defining functions named complete(), mark_socket(), etc. would interpose
 * CMFA's wrappers and leave their function pointers unset.
 */
extern void (*mark_socket_func)(void *tun_interface, int fd);
extern int (*query_socket_uid_func)(
    void *tun_interface,
    int protocol,
    const char *source,
    const char *target
);
extern void (*complete_func)(void *completable, const char *exception);
extern void (*fetch_report_func)(void *fetch_callback, const char *status_json);
extern void (*fetch_complete_func)(void *fetch_callback, const char *error);
extern int (*logcat_received_func)(void *logcat_interface, const char *payload);
extern int (*open_content_func)(const char *url, char *error, int error_length);
extern void (*release_object_func)(void *object);

static JavaVM *java_vm;

static JNIEnv *attach_thread(int *must_detach) {
    JNIEnv *env = NULL;
    *must_detach = 0;
    if ((*java_vm)->GetEnv(java_vm, (void **) &env, JNI_VERSION_1_6) == JNI_OK) {
        return env;
    }
    if ((*java_vm)->AttachCurrentThread(java_vm, &env, NULL) != JNI_OK) {
        return NULL;
    }
    *must_detach = 1;
    return env;
}

static void detach_thread(int must_detach) {
    if (must_detach) {
        (*java_vm)->DetachCurrentThread(java_vm);
    }
}

static int clear_pending_exception(JNIEnv *env) {
    if (!(*env)->ExceptionCheck(env)) {
        return 0;
    }
    (*env)->ExceptionDescribe(env);
    (*env)->ExceptionClear(env);
    return 1;
}

static jstring new_utf8_string(JNIEnv *env, const char *value) {
    if (value == NULL) {
        return NULL;
    }
    size_t length = strlen(value);
    if (length > INT32_MAX) {
        return NULL;
    }
    jbyteArray bytes = (*env)->NewByteArray(env, (jsize) length);
    if (bytes == NULL) {
        return NULL;
    }
    (*env)->SetByteArrayRegion(env, bytes, 0, (jsize) length, (const jbyte *) value);
    jclass string_class = (*env)->FindClass(env, "java/lang/String");
    jmethodID constructor = string_class == NULL
        ? NULL
        : (*env)->GetMethodID(
            env,
            string_class,
            "<init>",
            "([BLjava/lang/String;)V"
        );
    jstring charset = (*env)->NewStringUTF(env, "UTF-8");
    jstring result = constructor == NULL || charset == NULL
        ? NULL
        : (jstring) (*env)->NewObject(env, string_class, constructor, bytes, charset);
    if (charset != NULL) {
        (*env)->DeleteLocalRef(env, charset);
    }
    if (string_class != NULL) {
        (*env)->DeleteLocalRef(env, string_class);
    }
    (*env)->DeleteLocalRef(env, bytes);
    return result;
}

static jmethodID find_callback_method(
    JNIEnv *env,
    jobject callback,
    const char *name,
    const char *signature
) {
    jclass callback_class = (*env)->GetObjectClass(env, callback);
    if (callback_class == NULL) {
        clear_pending_exception(env);
        return NULL;
    }
    jmethodID method = (*env)->GetMethodID(env, callback_class, name, signature);
    (*env)->DeleteLocalRef(env, callback_class);
    if (method == NULL) {
        clear_pending_exception(env);
    }
    return method;
}

static void weave_mark_socket(void *callback, int fd) {
    int must_detach = 0;
    JNIEnv *env = attach_thread(&must_detach);
    if (env != NULL && callback != NULL) {
        jmethodID method = find_callback_method(env, callback, "markSocket", "(I)V");
        if (method != NULL) {
            (*env)->CallVoidMethod(env, (jobject) callback, method, (jint) fd);
            clear_pending_exception(env);
        }
    }
    detach_thread(must_detach);
}

static int weave_query_socket_uid(
    void *callback,
    int protocol,
    const char *source,
    const char *target
) {
    int result = -1;
    int must_detach = 0;
    JNIEnv *env = attach_thread(&must_detach);
    if (env != NULL && callback != NULL) {
        jmethodID method = find_callback_method(
            env,
            callback,
            "querySocketUid",
            "(ILjava/lang/String;Ljava/lang/String;)I"
        );
        if (method != NULL) {
            jstring java_source = source == NULL ? NULL : (*env)->NewStringUTF(env, source);
            jstring java_target = target == NULL ? NULL : (*env)->NewStringUTF(env, target);
            if (java_source != NULL && java_target != NULL) {
                result = (*env)->CallIntMethod(
                    env,
                    (jobject) callback,
                    method,
                    (jint) protocol,
                    java_source,
                    java_target
                );
            }
            clear_pending_exception(env);
            if (java_source != NULL) {
                (*env)->DeleteLocalRef(env, java_source);
            }
            if (java_target != NULL) {
                (*env)->DeleteLocalRef(env, java_target);
            }
        }
    }
    detach_thread(must_detach);
    return result;
}

static void weave_complete(void *callback, const char *error) {
    int must_detach = 0;
    JNIEnv *env = attach_thread(&must_detach);
    if (env != NULL && callback != NULL) {
        jmethodID method = find_callback_method(
            env,
            callback,
            "complete",
            "(Ljava/lang/String;)V"
        );
        if (method != NULL) {
            jstring java_error = new_utf8_string(env, error);
            (*env)->CallVoidMethod(env, (jobject) callback, method, java_error);
            clear_pending_exception(env);
            if (java_error != NULL) {
                (*env)->DeleteLocalRef(env, java_error);
            }
        }
    }
    detach_thread(must_detach);
}

static void weave_release_object(void *object) {
    int must_detach = 0;
    JNIEnv *env = attach_thread(&must_detach);
    if (env != NULL && object != NULL) {
        (*env)->DeleteGlobalRef(env, (jobject) object);
    }
    detach_thread(must_detach);
}

static void weave_fetch_complete(void *callback, const char *error) {
    (void) callback;
    (void) error;
}

static void weave_fetch_report(void *callback, const char *payload) {
    (void) callback;
    (void) payload;
}

static int weave_logcat_received(void *callback, const char *payload) {
    (void) callback;
    (void) payload;
    return 1;
}

static int weave_open_content(const char *url, char *error, int error_length) {
    (void) url;
    if (error != NULL && error_length > 0) {
        snprintf(error, (size_t) error_length, "resource URLs are disabled by Weave");
    }
    return -1;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void) reserved;
    JNIEnv *env = NULL;
    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    java_vm = vm;
    mark_socket_func = &weave_mark_socket;
    query_socket_uid_func = &weave_query_socket_uid;
    complete_func = &weave_complete;
    fetch_report_func = &weave_fetch_report;
    fetch_complete_func = &weave_fetch_complete;
    logcat_received_func = &weave_logcat_received;
    open_content_func = &weave_open_content;
    release_object_func = &weave_release_object;
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL
Java_io_weave_client_core_bridge_NativeBridge_nativeInit(
    JNIEnv *env,
    jobject instance,
    jstring home,
    jstring version_name,
    jint sdk_version
) {
    (void) instance;
    const char *native_home = (*env)->GetStringUTFChars(env, home, NULL);
    const char *native_version = (*env)->GetStringUTFChars(env, version_name, NULL);
    if (native_home == NULL || native_version == NULL) {
        return;
    }
    coreInit(native_home, native_version, CORE_REVISION, sdk_version);
    (*env)->ReleaseStringUTFChars(env, home, native_home);
    (*env)->ReleaseStringUTFChars(env, version_name, native_version);
}

JNIEXPORT void JNICALL
Java_io_weave_client_core_bridge_NativeBridge_nativeReset(
    JNIEnv *env,
    jobject instance
) {
    (void) env;
    (void) instance;
    reset();
}

JNIEXPORT void JNICALL
Java_io_weave_client_core_bridge_NativeBridge_nativeLoad(
    JNIEnv *env,
    jobject instance,
    jobject completion,
    jstring path
) {
    (void) instance;
    jobject global_completion = (*env)->NewGlobalRef(env, completion);
    const char *native_path = (*env)->GetStringUTFChars(env, path, NULL);
    if (global_completion == NULL || native_path == NULL) {
        if (global_completion != NULL) {
            (*env)->DeleteGlobalRef(env, global_completion);
        }
        return;
    }
    load(global_completion, native_path);
    (*env)->ReleaseStringUTFChars(env, path, native_path);
}

JNIEXPORT void JNICALL
Java_io_weave_client_core_bridge_NativeBridge_nativeValidateConfiguration(
    JNIEnv *env,
    jobject instance,
    jobject completion,
    jstring path
) {
    (void) instance;
    jobject global_completion = (*env)->NewGlobalRef(env, completion);
    const char *native_path = (*env)->GetStringUTFChars(env, path, NULL);
    if (global_completion == NULL || native_path == NULL) {
        if (global_completion != NULL) {
            (*env)->DeleteGlobalRef(env, global_completion);
        }
        return;
    }
    validateConfiguration(global_completion, native_path);
    (*env)->ReleaseStringUTFChars(env, path, native_path);
}

JNIEXPORT jint JNICALL
Java_io_weave_client_core_bridge_NativeBridge_nativeStartTun(
    JNIEnv *env,
    jobject instance,
    jint fd,
    jstring stack,
    jstring gateway,
    jstring portal,
    jstring dns,
    jobject callback
) {
    (void) instance;
    const char *native_stack = (*env)->GetStringUTFChars(env, stack, NULL);
    const char *native_gateway = (*env)->GetStringUTFChars(env, gateway, NULL);
    const char *native_portal = (*env)->GetStringUTFChars(env, portal, NULL);
    const char *native_dns = (*env)->GetStringUTFChars(env, dns, NULL);
    jobject global_callback = (*env)->NewGlobalRef(env, callback);
    if (
        native_stack == NULL ||
        native_gateway == NULL ||
        native_portal == NULL ||
        native_dns == NULL ||
        global_callback == NULL
    ) {
        if (global_callback != NULL) {
            (*env)->DeleteGlobalRef(env, global_callback);
        }
        return 1;
    }
    int result = startTun(
        fd,
        native_stack,
        native_gateway,
        native_portal,
        native_dns,
        global_callback
    );
    (*env)->ReleaseStringUTFChars(env, stack, native_stack);
    (*env)->ReleaseStringUTFChars(env, gateway, native_gateway);
    (*env)->ReleaseStringUTFChars(env, portal, native_portal);
    (*env)->ReleaseStringUTFChars(env, dns, native_dns);
    return result;
}

JNIEXPORT void JNICALL
Java_io_weave_client_core_bridge_NativeBridge_nativeStopTun(
    JNIEnv *env,
    jobject instance
) {
    (void) env;
    (void) instance;
    stopTun();
}

JNIEXPORT void JNICALL
Java_io_weave_client_core_bridge_NativeBridge_nativeNotifyInstalledAppsChanged(
    JNIEnv *env,
    jobject instance,
    jstring apps
) {
    (void) instance;
    const char *native_apps = (*env)->GetStringUTFChars(env, apps, NULL);
    if (native_apps == NULL) {
        return;
    }
    notifyInstalledAppsChanged(native_apps);
    (*env)->ReleaseStringUTFChars(env, apps, native_apps);
}

JNIEXPORT jlongArray JNICALL
Java_io_weave_client_core_bridge_NativeBridge_nativeQueryTraffic(
    JNIEnv *env,
    jobject instance,
    jboolean total
) {
    (void) instance;
    uint64_t upload = 0;
    uint64_t download = 0;
    if (total) {
        queryTotal(&upload, &download);
    } else {
        queryNow(&upload, &download);
    }
    jlong values[2] = {(jlong) upload, (jlong) download};
    jlongArray result = (*env)->NewLongArray(env, 2);
    if (result != NULL) {
        (*env)->SetLongArrayRegion(env, result, 0, 2, values);
    }
    return result;
}

JNIEXPORT jstring JNICALL
Java_io_weave_client_core_bridge_NativeBridge_nativeQueryGroup(
    JNIEnv *env,
    jobject instance,
    jstring name
) {
    (void) instance;
    const char *native_name = (*env)->GetStringUTFChars(env, name, NULL);
    if (native_name == NULL) {
        return NULL;
    }
    char *response = queryGroup(native_name, "Default");
    (*env)->ReleaseStringUTFChars(env, name, native_name);
    if (response == NULL) {
        return NULL;
    }
    jstring result = new_utf8_string(env, response);
    free(response);
    return result;
}

JNIEXPORT void JNICALL
Java_io_weave_client_core_bridge_NativeBridge_nativeHealthCheck(
    JNIEnv *env,
    jobject instance,
    jobject completion,
    jstring name
) {
    (void) instance;
    jobject global_completion = (*env)->NewGlobalRef(env, completion);
    const char *native_name = (*env)->GetStringUTFChars(env, name, NULL);
    if (global_completion == NULL || native_name == NULL) {
        if (global_completion != NULL) {
            (*env)->DeleteGlobalRef(env, global_completion);
        }
        return;
    }
    healthCheck(global_completion, native_name);
    (*env)->ReleaseStringUTFChars(env, name, native_name);
}
