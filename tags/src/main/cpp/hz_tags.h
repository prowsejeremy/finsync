// hz's tag engine: TagLib behind a small, platform-free C++ interface. The Android JNI bridge
// (hz_tags_jni.cpp) wraps it; an iOS port would wrap the same functions.
#pragma once

#include <cstdint>
#include <map>
#include <optional>
#include <string>
#include <vector>

namespace hz::tags {

// Units match the app's existing audio details: bits per second, hertz and milliseconds.
struct AudioDetails {
  std::optional<int64_t> durationMs;
  std::optional<int32_t> sampleRate;
  std::optional<int32_t> bitDepth;
  std::optional<int32_t> bitrate;
  std::optional<std::string> codec;
};

struct Chapter {
  std::string title;
  int64_t startMs;
};

// Every value is UTF-8. Field names are TagLib's property names, such as "ALBUMARTIST".
struct FileTags {
  std::map<std::string, std::vector<std::string>> fields;
  AudioDetails audio;
  std::vector<Chapter> neroChapters;
  std::vector<Chapter> quickTimeChapters;
};

// [extension] is the file's real format, without the dot and in any case, so a ".part" file
// can be opened. Returns nothing when TagLib can't open the file as that format.
std::optional<FileTags> read(const std::string &path, const std::string &extension);

// Replaces only the given fields and keeps every other tag. False on failure, which can leave
// the file partly written.
bool write(const std::string &path, const std::string &extension,
           const std::map<std::string, std::string> &fields);

// The front cover's bytes, else the first picture's.
std::optional<std::vector<uint8_t>> readCover(const std::string &path,
                                              const std::string &extension);

}  // namespace hz::tags
