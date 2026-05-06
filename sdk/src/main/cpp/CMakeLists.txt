cmake_minimum_required(VERSION 3.10.2)
project(sdk)

add_library(
        lib-crash-report
        SHARED
        lib-crash-report.cpp
)

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
        log-lib
        log)

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
        lib-crash-report

        # Links the target library to the log library
        # included in the NDK.
        ${log-lib}

        # Link against libunwind and libdl
        unwind
        dl)

set(ALIGNMENT_OPTIONS "-Wl,-z,max-page-size=16384" "-Wl,--hash-style=both")

target_link_options(lib-crash-report PRIVATE ${ALIGNMENT_OPTIONS})