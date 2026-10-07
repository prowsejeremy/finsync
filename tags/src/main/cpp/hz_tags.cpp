#include "hz_tags.h"

// Standard library
#include <algorithm>
#include <cctype>
#include <memory>

// TagLib
#include <aifffile.h>
#include <apefile.h>
#include <apetag.h>
#include <flacfile.h>
#include <id3v1tag.h>
#include <id3v2tag.h>
#include <infotag.h>
#include <mp4file.h>
#include <mp4itemfactory.h>
#include <mpegfile.h>
#include <oggflacfile.h>
#include <opusfile.h>
#include <tfilestream.h>
#include <tpropertymap.h>
#include <vorbisfile.h>
#include <wavfile.h>
#include <wavpackfile.h>
#include <xiphcomment.h>

namespace hz::tags {
namespace {

constexpr int kBitsPerKilobit = 1000;
constexpr unsigned int kId3v23 = 3;

enum class Format { Mp3, Flac, Mp4, Ogg, Ape, WavPack, Wav, Aiff };

// The extensions BASS plays with hz's bundled add-ons. Anything else isn't opened.
std::optional<Format> formatOf(const std::string &extension) {
  static const std::map<std::string, Format> formats = {
      {"mp3", Format::Mp3}, {"flac", Format::Flac},   {"m4a", Format::Mp4},
      {"m4b", Format::Mp4}, {"ogg", Format::Ogg},     {"opus", Format::Ogg},
      {"ape", Format::Ape}, {"wv", Format::WavPack},  {"wav", Format::Wav},
      {"aif", Format::Aiff}, {"aiff", Format::Aiff}};
  std::string lower(extension);
  std::transform(lower.begin(), lower.end(), lower.begin(),
                 [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
  const auto found = formats.find(lower);
  if (found == formats.end()) return std::nullopt;
  return found->second;
}

// An .ogg file can hold Vorbis, Opus or FLAC, so its content decides.
std::unique_ptr<TagLib::File> oggFileFor(TagLib::IOStream *stream, bool readProperties) {
  if (TagLib::Ogg::Opus::File::isSupported(stream)) {
    return std::make_unique<TagLib::Ogg::Opus::File>(stream, readProperties);
  }
  if (TagLib::Ogg::Vorbis::File::isSupported(stream)) {
    return std::make_unique<TagLib::Ogg::Vorbis::File>(stream, readProperties);
  }
  if (TagLib::Ogg::FLAC::File::isSupported(stream)) {
    return std::make_unique<TagLib::Ogg::FLAC::File>(stream, readProperties);
  }
  return nullptr;
}

bool otherFormatClaims(TagLib::IOStream *stream) {
  return TagLib::FLAC::File::isSupported(stream) || TagLib::MP4::File::isSupported(stream) ||
         TagLib::Ogg::Vorbis::File::isSupported(stream) ||
         TagLib::Ogg::Opus::File::isSupported(stream) ||
         TagLib::Ogg::FLAC::File::isSupported(stream) || TagLib::APE::File::isSupported(stream) ||
         TagLib::WavPack::File::isSupported(stream) ||
         TagLib::RIFF::WAV::File::isSupported(stream) ||
         TagLib::RIFF::AIFF::File::isSupported(stream);
}

// TagLib opens almost any bytes as MPEG, and a write would put an ID3v2 tag in front of them.
// MPEG::File::isSupported only looks 1 KB past the first ID3v2 tag, though, so it refuses real
// MP3s with a second tag or padding. Those still open, unless another format hz plays claims
// the content, as long as TagLib finds an MPEG frame.
std::unique_ptr<TagLib::File> mp3FileFor(TagLib::IOStream *stream, bool readProperties) {
  if (!TagLib::MPEG::File::isSupported(stream) && otherFormatClaims(stream)) return nullptr;
  auto mp3 = std::make_unique<TagLib::MPEG::File>(stream, readProperties);
  if (mp3->firstFrameOffset() < 0) return nullptr;
  return mp3;
}

// A file opens as the format its extension names only when its content agrees, so a write can't
// damage another format's bytes.
bool contentMatches(Format format, TagLib::IOStream *stream) {
  switch (format) {
    case Format::Mp3: return true;  // mp3FileFor checks the content itself
    case Format::Flac: return TagLib::FLAC::File::isSupported(stream);
    case Format::Mp4: return TagLib::MP4::File::isSupported(stream);
    case Format::Ogg: return true;  // oggFileFor checks the content itself
    case Format::Ape: return TagLib::APE::File::isSupported(stream);
    case Format::WavPack: return TagLib::WavPack::File::isSupported(stream);
    case Format::Wav: return TagLib::RIFF::WAV::File::isSupported(stream);
    case Format::Aiff: return TagLib::RIFF::AIFF::File::isSupported(stream);
  }
  return false;
}

std::unique_ptr<TagLib::File> fileFor(Format format, TagLib::IOStream *stream,
                                      bool readProperties) {
  if (!contentMatches(format, stream)) return nullptr;
  switch (format) {
    case Format::Mp3:
      return mp3FileFor(stream, readProperties);
    case Format::Flac:
      return std::make_unique<TagLib::FLAC::File>(stream, readProperties);
    case Format::Mp4:
      return std::make_unique<TagLib::MP4::File>(stream, readProperties);
    case Format::Ogg:
      return oggFileFor(stream, readProperties);
    case Format::Ape:
      return std::make_unique<TagLib::APE::File>(stream, readProperties);
    case Format::WavPack:
      return std::make_unique<TagLib::WavPack::File>(stream, readProperties);
    case Format::Wav:
      return std::make_unique<TagLib::RIFF::WAV::File>(stream, readProperties);
    case Format::Aiff:
      return std::make_unique<TagLib::RIFF::AIFF::File>(stream, readProperties);
  }
  return nullptr;
}

// TagLib's File reads through the stream without owning it, so the file is declared last and
// destroyed first.
struct OpenFile {
  Format format = Format::Mp3;
  std::unique_ptr<TagLib::FileStream> stream;
  std::unique_ptr<TagLib::File> file;
};

std::optional<OpenFile> open(const std::string &path, const std::string &extension,
                             bool readOnly, bool readProperties) {
  const auto format = formatOf(extension);
  if (!format) return std::nullopt;
  OpenFile opened;
  opened.format = *format;
  opened.stream = std::make_unique<TagLib::FileStream>(path.c_str(), readOnly);
  if (!opened.stream->isOpen()) return std::nullopt;
  opened.file = fileFor(*format, opened.stream.get(), readProperties);
  if (!opened.file || !opened.file->isValid()) return std::nullopt;
  return opened;
}

std::string utf8(const TagLib::String &value) { return value.to8Bit(true); }

std::optional<int32_t> positive(int value) {
  if (value <= 0) return std::nullopt;
  return value;
}

void setLossless(AudioDetails &audio, const char *codec, int bitsPerSample) {
  audio.codec = codec;
  audio.bitDepth = positive(bitsPerSample);
}

void describeMp4(const TagLib::MP4::Properties &properties, AudioDetails &audio) {
  using Codec = TagLib::MP4::Properties::Codec;
  switch (properties.codec()) {
    case Codec::AAC: audio.codec = "aac"; break;
    case Codec::ALAC: setLossless(audio, "alac", properties.bitsPerSample()); break;
    case Codec::FLAC: setLossless(audio, "flac", properties.bitsPerSample()); break;
    case Codec::Opus: audio.codec = "opus"; break;
    case Codec::AC3: audio.codec = "ac3"; break;
    case Codec::EAC3: audio.codec = "eac3"; break;
    case Codec::DTS: audio.codec = "dts"; break;
    case Codec::Unknown: break;
  }
}

void describeOgg(TagLib::File &file, AudioDetails &audio) {
  if (dynamic_cast<TagLib::Ogg::Opus::File *>(&file)) {
    audio.codec = "opus";
  } else if (dynamic_cast<TagLib::Ogg::Vorbis::File *>(&file)) {
    audio.codec = "vorbis";
  } else if (auto *flac = dynamic_cast<TagLib::Ogg::FLAC::File *>(&file)) {
    setLossless(audio, "flac", flac->audioProperties()->bitsPerSample());
  }
}

// Codec names follow the app's existing lowercase names. Bit depth is only given for lossless
// audio, where it describes the source.
void describeCodec(const OpenFile &opened, AudioDetails &audio) {
  TagLib::File &file = *opened.file;
  switch (opened.format) {
    case Format::Mp3:
      audio.codec = "mp3";
      break;
    case Format::Flac:
      setLossless(audio, "flac",
                  static_cast<TagLib::FLAC::File &>(file).audioProperties()->bitsPerSample());
      break;
    case Format::Mp4:
      describeMp4(*static_cast<TagLib::MP4::File &>(file).audioProperties(), audio);
      break;
    case Format::Ogg:
      describeOgg(file, audio);
      break;
    case Format::Ape:
      setLossless(audio, "ape",
                  static_cast<TagLib::APE::File &>(file).audioProperties()->bitsPerSample());
      break;
    case Format::WavPack: {
      const auto *properties = static_cast<TagLib::WavPack::File &>(file).audioProperties();
      audio.codec = "wavpack";
      if (properties->isLossless()) audio.bitDepth = positive(properties->bitsPerSample());
      break;
    }
    case Format::Wav:
      setLossless(audio, "pcm",
                  static_cast<TagLib::RIFF::WAV::File &>(file).audioProperties()->bitsPerSample());
      break;
    case Format::Aiff:
      setLossless(audio, "pcm",
                  static_cast<TagLib::RIFF::AIFF::File &>(file).audioProperties()->bitsPerSample());
      break;
  }
}

AudioDetails audioOf(const OpenFile &opened) {
  AudioDetails audio;
  const TagLib::AudioProperties *properties = opened.file->audioProperties();
  if (!properties) return audio;
  if (const auto length = positive(properties->lengthInMilliseconds())) audio.durationMs = *length;
  audio.sampleRate = positive(properties->sampleRate());
  if (const auto kilobits = positive(properties->bitrate())) {
    audio.bitrate = *kilobits * kBitsPerKilobit;
  }
  describeCodec(opened, audio);
  return audio;
}

// The file's tags, main tag first. Older tag types (ID3v1, APE in an MP3, RIFF INFO) are only
// included when the file already has them, so a write never adds one. [createMain] creates the
// main tag when it's missing.
std::vector<TagLib::Tag *> tagsOf(const OpenFile &opened, bool createMain) {
  TagLib::File &file = *opened.file;
  std::vector<TagLib::Tag *> tags;
  const auto add = [&tags](TagLib::Tag *tag) {
    if (tag) tags.push_back(tag);
  };
  switch (opened.format) {
    case Format::Mp3: {
      auto &mp3 = static_cast<TagLib::MPEG::File &>(file);
      add(mp3.ID3v2Tag(createMain));
      if (mp3.hasAPETag()) add(mp3.APETag());
      if (mp3.hasID3v1Tag()) add(mp3.ID3v1Tag());
      break;
    }
    case Format::Flac: {
      auto &flac = static_cast<TagLib::FLAC::File &>(file);
      add(flac.xiphComment(createMain));
      if (flac.hasID3v2Tag()) add(flac.ID3v2Tag());
      if (flac.hasID3v1Tag()) add(flac.ID3v1Tag());
      break;
    }
    case Format::Ape: {
      auto &ape = static_cast<TagLib::APE::File &>(file);
      add(ape.APETag(createMain));
      if (ape.hasID3v1Tag()) add(ape.ID3v1Tag());
      break;
    }
    case Format::WavPack: {
      auto &wavPack = static_cast<TagLib::WavPack::File &>(file);
      add(wavPack.APETag(createMain));
      if (wavPack.hasID3v1Tag()) add(wavPack.ID3v1Tag());
      break;
    }
    case Format::Wav: {
      auto &wav = static_cast<TagLib::RIFF::WAV::File &>(file);
      add(wav.ID3v2Tag());
      if (wav.hasInfoTag()) add(wav.InfoTag());
      break;
    }
    case Format::Mp4:
    case Format::Ogg:
    case Format::Aiff:
      add(file.tag());
      break;
  }
  return tags;
}

// A later tag only adds the fields the earlier ones don't have. TagLib's own File::properties()
// reads just the first non-empty tag, so an ID3v2 tag holding only a cover would hide a WAV's
// INFO fields.
std::map<std::string, std::vector<std::string>> fieldsOf(const std::vector<TagLib::Tag *> &tags) {
  std::map<std::string, std::vector<std::string>> fields;
  for (const TagLib::Tag *tag : tags) {
    for (const auto &[key, values] : tag->properties()) {
      const auto [entry, added] = fields.try_emplace(utf8(key));
      if (!added) continue;
      for (const auto &value : values) entry->second.push_back(utf8(value));
    }
  }
  return fields;
}

// Each tag is updated from its own fields. Writing one merged map into every tag would delete
// the fields only one of them holds.
void replaceFields(TagLib::Tag &tag, const TagLib::PropertyMap &ours) {
  TagLib::PropertyMap properties = tag.properties();
  for (const auto &[key, values] : ours) properties.replace(key, values);
  tag.setProperties(properties);
}

// MP4's setProperties rebuilds every item under upper-cased names, so a freeform atom such as
// iTunSMPB would gain an ITUNSMPB twin. For MP4, only our items are set.
void replaceMp4Fields(TagLib::MP4::Tag &tag, const TagLib::PropertyMap &ours) {
  const TagLib::MP4::ItemFactory *factory = TagLib::MP4::ItemFactory::instance();
  for (const auto &[key, values] : ours) {
    const auto [name, item] = factory->itemFromProperty(key, values);
    if (item.isValid()) tag.setItem(TagLib::String(name, TagLib::String::Latin1), item);
  }
}

std::vector<Chapter> chaptersOf(const TagLib::MP4::ChapterList &list) {
  std::vector<Chapter> chapters;
  for (const auto &chapter : list) {
    chapters.push_back({utf8(chapter.title()), chapter.startTime()});
  }
  return chapters;
}

// An existing ID3v2.3 tag stays at 2.3, so players that only read 2.3 keep working. Everything
// else is saved as TagLib's default, 2.4.
TagLib::ID3v2::Version id3v2VersionOf(const TagLib::ID3v2::Tag *tag) {
  if (tag && tag->header()->majorVersion() == kId3v23) return TagLib::ID3v2::v3;
  return TagLib::ID3v2::v4;
}

// Saves the tag types the file already had, plus ID3v2 where that's the main tag. TagLib's
// plain save() would add an ID3v1 tag to MP3s that never had one.
bool save(const OpenFile &opened) {
  TagLib::File &file = *opened.file;
  switch (opened.format) {
    case Format::Mp3: {
      auto &mp3 = static_cast<TagLib::MPEG::File &>(file);
      int tags = TagLib::MPEG::File::ID3v2;
      if (mp3.hasID3v1Tag()) tags |= TagLib::MPEG::File::ID3v1;
      if (mp3.hasAPETag()) tags |= TagLib::MPEG::File::APE;
      return mp3.save(tags, TagLib::File::StripNone, id3v2VersionOf(mp3.ID3v2Tag()),
                      TagLib::File::DoNotDuplicate);
    }
    case Format::Wav: {
      auto &wav = static_cast<TagLib::RIFF::WAV::File &>(file);
      const auto tags = wav.hasInfoTag() ? TagLib::RIFF::WAV::File::AllTags
                                         : TagLib::RIFF::WAV::File::ID3v2;
      return wav.save(tags, TagLib::File::StripNone, id3v2VersionOf(wav.ID3v2Tag()));
    }
    case Format::Aiff: {
      auto &aiff = static_cast<TagLib::RIFF::AIFF::File &>(file);
      return aiff.save(id3v2VersionOf(aiff.tag()));
    }
    default:
      return file.save();
  }
}

}  // namespace

std::optional<FileTags> read(const std::string &path, const std::string &extension) {
  const auto opened = open(path, extension, true, true);
  if (!opened) return std::nullopt;
  FileTags tags;
  tags.fields = fieldsOf(tagsOf(*opened, false));
  tags.audio = audioOf(*opened);
  if (opened->format == Format::Mp4) {
    auto &mp4 = static_cast<TagLib::MP4::File &>(*opened->file);
    tags.neroChapters = chaptersOf(mp4.neroChapters());
    tags.quickTimeChapters = chaptersOf(mp4.qtChapters());
  }
  return tags;
}

bool write(const std::string &path, const std::string &extension,
           const std::map<std::string, std::string> &fields) {
  const auto opened = open(path, extension, false, false);
  if (!opened || opened->file->readOnly()) return false;
  TagLib::PropertyMap ours;
  for (const auto &[key, value] : fields) {
    // An empty value keeps the file's own (D2). Kotlin skips blanks, but a string that isn't
    // valid UTF-16, such as a lone surrogate, only becomes empty here.
    if (value.empty()) continue;
    ours.replace(TagLib::String(key, TagLib::String::UTF8),
                 TagLib::StringList(TagLib::String(value, TagLib::String::UTF8)));
  }
  if (opened->format == Format::Mp4) {
    TagLib::MP4::Tag *tag = static_cast<TagLib::MP4::File &>(*opened->file).tag();
    if (!tag) return false;
    replaceMp4Fields(*tag, ours);
  } else {
    const auto tags = tagsOf(*opened, true);
    if (tags.empty()) return false;
    for (TagLib::Tag *tag : tags) replaceFields(*tag, ours);
  }
  return save(*opened);
}

std::optional<std::vector<uint8_t>> readCover(const std::string &path,
                                              const std::string &extension) {
  const auto opened = open(path, extension, true, false);
  if (!opened) return std::nullopt;
  const TagLib::List<TagLib::VariantMap> pictures = opened->file->complexProperties("PICTURE");
  if (pictures.isEmpty()) return std::nullopt;
  const auto front = std::find_if(pictures.begin(), pictures.end(), [](const auto &picture) {
    return picture.value("pictureType").toString() == "Front Cover";
  });
  const TagLib::VariantMap &chosen = front != pictures.end() ? *front : pictures.front();
  const TagLib::ByteVector data = chosen.value("data").toByteVector();
  if (data.isEmpty()) return std::nullopt;
  return std::vector<uint8_t>(data.begin(), data.end());
}

}  // namespace hz::tags
