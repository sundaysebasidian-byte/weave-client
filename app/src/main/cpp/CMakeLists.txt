cmake_minimum_required(VERSION 3.22.1)

project(weave_bridge C)

add_library(clash SHARED IMPORTED)
set_target_properties(
    clash
    PROPERTIES
    IMPORTED_LOCATION "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libclash.so"
    IMPORTED_NO_SONAME TRUE
)

add_library(bridge SHARED weave_bridge.c)

target_compile_options(
    bridge
    PRIVATE
    -O2
    -Wall
    -Wextra
    -Werror
    -fstack-protector-strong
    -D_FORTIFY_SOURCE=2
)

target_link_options(
    bridge
    PRIVATE
    "-Wl,-z,relro"
    "-Wl,-z,now"
    "-Wl,--no-undefined"
)

target_link_libraries(bridge PRIVATE clash log)
