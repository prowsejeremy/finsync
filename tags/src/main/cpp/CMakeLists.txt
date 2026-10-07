# Builds libhztags.so: TagLib, linked statically, plus hz's bridge.
cmake_minimum_required(VERSION 3.22.1)
project(hztags LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# TagLib with only the formats hz plays. Its tests, bindings and examples aren't built, and its
# symbols stay hidden, so the linker can drop whatever the bridge doesn't use.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(BUILD_BINDINGS OFF CACHE BOOL "" FORCE)
set(BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(VISIBILITY_HIDDEN ON CACHE BOOL "" FORCE)
set(WITH_ASF OFF CACHE BOOL "" FORCE)
set(WITH_DSF OFF CACHE BOOL "" FORCE)
set(WITH_MATROSKA OFF CACHE BOOL "" FORCE)
set(WITH_MOD OFF CACHE BOOL "" FORCE)
set(WITH_SHORTEN OFF CACHE BOOL "" FORCE)
set(WITH_TRUEAUDIO OFF CACHE BOOL "" FORCE)
add_subdirectory(taglib EXCLUDE_FROM_ALL)

# Debug builds would otherwise compile TagLib unoptimised, which slows every scan in alpha builds.
target_compile_options(tag PRIVATE -O2)

set(TAGLIB_SOURCE "${CMAKE_CURRENT_SOURCE_DIR}/taglib/taglib")

add_library(hztags SHARED hz_tags.cpp hz_tags_jni.cpp)
set_target_properties(hztags PROPERTIES CXX_VISIBILITY_PRESET hidden)
target_include_directories(hztags PRIVATE
  "${TAGLIB_SOURCE}"
  "${TAGLIB_SOURCE}/toolkit"
  "${TAGLIB_SOURCE}/mpeg"
  "${TAGLIB_SOURCE}/mpeg/id3v1"
  "${TAGLIB_SOURCE}/mpeg/id3v2"
  "${TAGLIB_SOURCE}/mpeg/id3v2/frames"
  "${TAGLIB_SOURCE}/flac"
  "${TAGLIB_SOURCE}/mp4"
  "${TAGLIB_SOURCE}/ogg"
  "${TAGLIB_SOURCE}/ogg/vorbis"
  "${TAGLIB_SOURCE}/ogg/opus"
  "${TAGLIB_SOURCE}/ogg/flac"
  "${TAGLIB_SOURCE}/ape"
  "${TAGLIB_SOURCE}/wavpack"
  "${TAGLIB_SOURCE}/riff"
  "${TAGLIB_SOURCE}/riff/wav"
  "${TAGLIB_SOURCE}/riff/aiff")
target_link_libraries(hztags PRIVATE tag log)
target_link_options(hztags PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL)
