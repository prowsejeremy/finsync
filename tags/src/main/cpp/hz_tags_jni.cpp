// JNI glue between com.jpd.hz.tags.TagLibBridge and hz_tags.h. Strings cross as UTF-16, because
// JNI's own UTF-8 calls use a modified encoding that breaks on emoji.
#include <jni.h>

// Standard library
#include <exception>
#include <new>
#include <string>

// Android
#include <android/log.h>

// TagLib
#include <tstring.h>

// Local
#include "hz_tags.h"

namespace {

constexpr const char *kLogTag = "hztags";

// One slot per string in the array nativeRead returns; see TagLibBridge's NativeTags for the
// layout. An empty slot becomes a Java null.
using Slots = std::vector<std::optional<std::string>>;

std::string utf8Of(JNIEnv *env, jstring value) {
  const jsize length = env->GetStringLength(value);
  const jchar *chars = env->GetStringChars(value, nullptr);
  if (!chars) throw std::bad_alloc();
  const TagLib::ByteVector utf16(reinterpret_cast<const char *>(chars),
                                 static_cast<unsigned int>(length * sizeof(jchar)));
  env->ReleaseStringChars(value, chars);
  return TagLib::String(utf16, TagLib::String::UTF16LE).to8Bit(true);
}

jstring javaStringOf(JNIEnv *env, const std::string &utf8) {
  const TagLib::ByteVector utf16 =
      TagLib::String(utf8, TagLib::String::UTF8).data(TagLib::String::UTF16LE);
  return env->NewString(reinterpret_cast<const jchar *>(utf16.data()),
                        static_cast<jsize>(utf16.size() / sizeof(jchar)));
}

template <typename T>
std::optional<std::string> decimal(const std::optional<T> &value) {
  if (!value) return std::nullopt;
  return std::to_string(*value);
}

void appendChapters(Slots &slots, const std::vector<hz::tags::Chapter> &chapters) {
  slots.emplace_back(std::to_string(chapters.size()));
  for (const auto &chapter : chapters) {
    slots.emplace_back(chapter.title);
    slots.emplace_back(std::to_string(chapter.startMs));
  }
}

Slots slotsOf(const hz::tags::FileTags &tags) {
  Slots slots = {tags.audio.codec, decimal(tags.audio.durationMs),
                 decimal(tags.audio.sampleRate), decimal(tags.audio.bitDepth),
                 decimal(tags.audio.bitrate)};
  size_t valueCount = 0;
  for (const auto &[key, values] : tags.fields) valueCount += values.size();
  slots.emplace_back(std::to_string(valueCount));
  for (const auto &[key, values] : tags.fields) {
    for (const auto &value : values) {
      slots.emplace_back(key);
      slots.emplace_back(value);
    }
  }
  appendChapters(slots, tags.neroChapters);
  appendChapters(slots, tags.quickTimeChapters);
  return slots;
}

// Null when Java is out of memory; the pending OutOfMemoryError is thrown on return.
jobjectArray javaArrayOf(JNIEnv *env, const Slots &slots) {
  jclass stringClass = env->FindClass("java/lang/String");
  if (!stringClass) return nullptr;
  jobjectArray array = env->NewObjectArray(static_cast<jsize>(slots.size()), stringClass, nullptr);
  env->DeleteLocalRef(stringClass);
  if (!array) return nullptr;
  for (size_t index = 0; index < slots.size(); ++index) {
    if (!slots[index]) continue;
    jstring value = javaStringOf(env, *slots[index]);
    if (!value) return nullptr;
    env->SetObjectArrayElement(array, static_cast<jsize>(index), value);
    env->DeleteLocalRef(value);
  }
  return array;
}

std::map<std::string, std::string> fieldsOf(JNIEnv *env, jobjectArray keysAndValues) {
  std::map<std::string, std::string> fields;
  const jsize length = env->GetArrayLength(keysAndValues);
  for (jsize index = 0; index + 1 < length; index += 2) {
    auto key = static_cast<jstring>(env->GetObjectArrayElement(keysAndValues, index));
    auto value = static_cast<jstring>(env->GetObjectArrayElement(keysAndValues, index + 1));
    fields[utf8Of(env, key)] = utf8Of(env, value);
    env->DeleteLocalRef(key);
    env->DeleteLocalRef(value);
  }
  return fields;
}

// A C++ exception must never cross into Java, where it would abort the app. TagLib doesn't
// throw for bad files, so this only catches failures such as running out of memory.
template <typename Result, typename Body>
Result guarded(const char *call, Result fallback, Body body) {
  try {
    return body();
  } catch (const std::exception &error) {
    __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s failed: %s", call, error.what());
  } catch (...) {
    __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s failed", call);
  }
  return fallback;
}

}  // namespace

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_jpd_hz_tags_TagLibBridge_nativeRead(JNIEnv *env, jobject, jstring path,
                                             jstring extension) {
  return guarded<jobjectArray>("read", nullptr, [&]() -> jobjectArray {
    const auto tags = hz::tags::read(utf8Of(env, path), utf8Of(env, extension));
    if (!tags) return nullptr;
    return javaArrayOf(env, slotsOf(*tags));
  });
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_jpd_hz_tags_TagLibBridge_nativeWrite(JNIEnv *env, jobject, jstring path,
                                              jstring extension, jobjectArray keysAndValues) {
  return guarded<jboolean>("write", JNI_FALSE, [&]() -> jboolean {
    const bool written =
        hz::tags::write(utf8Of(env, path), utf8Of(env, extension), fieldsOf(env, keysAndValues));
    return written ? JNI_TRUE : JNI_FALSE;
  });
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_jpd_hz_tags_TagLibBridge_nativeReadCover(JNIEnv *env, jobject, jstring path,
                                                  jstring extension) {
  return guarded<jbyteArray>("readCover", nullptr, [&]() -> jbyteArray {
    const auto cover = hz::tags::readCover(utf8Of(env, path), utf8Of(env, extension));
    if (!cover) return nullptr;
    jbyteArray bytes = env->NewByteArray(static_cast<jsize>(cover->size()));
    if (!bytes) return nullptr;
    env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(cover->size()),
                            reinterpret_cast<const jbyte *>(cover->data()));
    return bytes;
  });
}
