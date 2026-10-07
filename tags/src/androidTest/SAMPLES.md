# Tag engine test samples

`assets/samples/` holds one small file per format hz plays, for `TagLibBridgeTest`. They were
made on 2026-10-08 with ffmpeg 9.0.1 and TagLib 2.3.2, then checked on a Mac before they were
added. On each one, our fields round-trip, every other tag stays the same, the cover keeps its
bytes, and ffmpeg still decodes the audio.

## Audio and tags

- **Audio:** a 440 Hz sine at 44.1 kHz, mono. Music lasts 1 s and books 2 s. `vorbis.ogg` is
  stereo, because ffmpeg's built-in Vorbis encoder only does stereo. That ffmpeg had no
  libvorbis.
- **Tags another tagger would leave:** every sample except `monkey.ape` starts with a title,
  artist, album, album artist, genre `Rock`, date `1999`, track `3/12`, disc `1/2`, comment,
  lyrics, ReplayGain track gain, MusicBrainz track ID and composer.
  - `aac.m4a` and the three books also carry iTunes' media kind and compilation flag.
- **Images:** `front.jpg` is a 64×64 red JPEG, and `back.png` is a 32×32 blue PNG.

## Each file

| File | ffmpeg encoder and options | Then |
|---|---|---|
| `id3v24.mp3` | `libmp3lame` 128k, ID3v2.4, front cover | — |
| `id3v23.mp3` | as above, `-id3v2_version 3` | — |
| `with-id3v1.mp3` | `libmp3lame` 128k, `-write_id3v1 1`, no cover | — |
| `hires.flac` | `flac`, `-sample_fmt s32 -bits_per_raw_sample 24`, front cover | covers replaced (below) |
| `aac.m4a` | `aac` 128k, faststart, front cover | freeform atoms added (below) |
| `alac.m4a` | `alac`, front cover | — |
| `book-both.m4b` | `aac` 64k, 2 chapters from an FFMETADATA file; ffmpeg writes both formats | covers added |
| `book-nero.m4b` | as above | QuickTime chapters removed |
| `book-quicktime.m4b` | as above | Nero chapters removed |
| `vorbis.ogg` | `vorbis -strict experimental`, `-ac 2` | covers added |
| `opus.opus` | `libopus` 96k | covers added |
| `wavpack.wv` | `wavpack` | covers added |
| `pcm.wav` | `pcm_s16le`; ffmpeg writes a RIFF INFO tag only | covers added, in a new ID3v2 tag |
| `pcm.aiff` | `pcm_s16be`, `-write_id3v2 1`, front cover | — |
| `monkey.ape` | TagLib's own `tests/data/mac-399.ape`, untagged (ffmpeg can't encode APE) | covers added |

The chapters are "Opening" at 0 ms and "Second part" at 1000 ms.

"Covers added" and "covers replaced" mean TagLib's `FileRef::setComplexProperties("PICTURE", …)`
with two pictures: `back.png` as "Back Cover" first, then `front.jpg` as "Front Cover". That
way `readCover` has to choose. MP4 pictures have no type, so the first one wins there.

Removing a chapter format used `MP4::File::setQtChapters({})` or `setNeroChapters({})`,
then `save()`.

`aac.m4a` also carries two freeform atoms in mixed case, `----:com.apple.iTunes:iTunSMPB` and
`----:com.apple.iTunes:replaygain_track_gain`, set with `MP4::Tag::setItem` then `save()`. A
write must leave each one single.

## SHA-256

```
6da3ec22a5a628ffe67c3105336400ed8b415254e716612531fe7ab143ea3691  aac.m4a
75ec5dc3948dfd9f244699ead1fc6c06e694edf1201efcad267d5ad4ca36457e  alac.m4a
c8c1b59060f01e41800153c097a7d3984f4b031214cbc7ccdf8e9d3bd56cf637  back.png
70d18ff70fb9c34d14bcece95c00d57e783e4c2c95fb7af535876014800d92e0  book-both.m4b
b97dc1a115ada5c10b6e5c3fe678a11213ff3c4cde9bc134b175a8cb9d767831  book-nero.m4b
bfeedb93e942689574fb5cdddab0b4c2fc5b9fe658ef7a3c510cf3bc7e365f08  book-quicktime.m4b
452875e3531c77ab6281af75d3412832c3fa97f8fa0dfffa20314154b0f43eaa  front.jpg
3f7311940c4222efe463b5176432d12438ec25a258771650cf826b84390ff121  hires.flac
6da231182ade211c2aff969edda7346b132424049ffac4b5f722cfbe48ad4d97  id3v23.mp3
0d6bf36364ee509822872ff2fcb35efce8736da47daa89648c9cfd8223749a2f  id3v24.mp3
467994b29a8758ba50d05f7f95c2ef03a6e5b87ce5d44aaec20fd3133f2ec16b  monkey.ape
e644c2b4ee0a1d9d80e68099b9068988b83fdc16429bc29a65b04093308c741b  opus.opus
95d3d4bd0b8779480cf08e71dd9bbb6bd65c35bd13024f7dda013826c17c7301  pcm.aiff
708b4deff80601328e4561c32a7d63bfc32d446af6fa3444b610da3dc2be05f7  pcm.wav
d506d1d8dfcbac20125166999080241d529f896e11e718dcf02011c1be131ff8  vorbis.ogg
6bbdf1a188997133923fa72bc1b478689ad74f2c1de355f2b5df4a5f6c1e7ad0  wavpack.wv
1cece205d2d76f84e4326af6ca93aee18601ffceb72429b9e8777c7ee8f49997  with-id3v1.mp3
```

## The ffmpeg commands

Each command below runs with `-hide_banner -loglevel error -y`. The arrays are bash.

```bash
OTHER_TAGS=(
  -metadata title='Original Title' -metadata artist='Original Artist'
  -metadata album='Original Album' -metadata album_artist='Original Album Artist'
  -metadata genre='Rock' -metadata date='1999' -metadata track='3/12' -metadata disc='1/2'
  -metadata comment='keep this comment' -metadata lyrics='la la la'
  -metadata REPLAYGAIN_TRACK_GAIN='-6.50 dB' -metadata MUSICBRAINZ_TRACKID='mb-track-123'
  -metadata composer='Original Composer'
)
MP4_EXTRAS=(-metadata media_type=2 -metadata compilation=1)
TONE=(-f lavfi -i 'sine=frequency=440:duration=1:sample_rate=44100' -ac 1)
BOOK_TONE=(-f lavfi -i 'sine=frequency=440:duration=2:sample_rate=44100' -ac 1)
COVER_IN=(-i front.jpg)
COVER_MAP=(-map 0:a -map 1:v -c:v copy -disposition:v:0 attached_pic)

ffmpeg -f lavfi -i 'color=c=red:s=64x64' -frames:v 1 front.jpg
ffmpeg -f lavfi -i 'color=c=blue:s=32x32' -frames:v 1 back.png
ffmpeg "${TONE[@]}" "${COVER_IN[@]}" "${COVER_MAP[@]}" -c:a libmp3lame -b:a 128k "${OTHER_TAGS[@]}" id3v24.mp3
ffmpeg "${TONE[@]}" "${COVER_IN[@]}" "${COVER_MAP[@]}" -c:a libmp3lame -b:a 128k "${OTHER_TAGS[@]}" -id3v2_version 3 id3v23.mp3
ffmpeg "${TONE[@]}" -c:a libmp3lame -b:a 128k "${OTHER_TAGS[@]}" -write_id3v1 1 with-id3v1.mp3
ffmpeg "${TONE[@]}" "${COVER_IN[@]}" "${COVER_MAP[@]}" -c:a flac -sample_fmt s32 -bits_per_raw_sample 24 "${OTHER_TAGS[@]}" hires.flac
ffmpeg "${TONE[@]}" "${COVER_IN[@]}" "${COVER_MAP[@]}" -c:a aac -b:a 128k "${OTHER_TAGS[@]}" "${MP4_EXTRAS[@]}" -movflags +faststart aac.m4a
ffmpeg "${TONE[@]}" "${COVER_IN[@]}" "${COVER_MAP[@]}" -c:a alac "${OTHER_TAGS[@]}" alac.m4a
ffmpeg "${BOOK_TONE[@]}" -i chapters.txt -map 0:a -map_chapters 1 -c:a aac -b:a 64k "${OTHER_TAGS[@]}" "${MP4_EXTRAS[@]}" book.m4b
ffmpeg "${TONE[@]}" -ac 2 -c:a vorbis -strict experimental "${OTHER_TAGS[@]}" vorbis.ogg
ffmpeg "${TONE[@]}" -c:a libopus -b:a 96k "${OTHER_TAGS[@]}" opus.opus
ffmpeg "${TONE[@]}" -c:a wavpack "${OTHER_TAGS[@]}" wavpack.wv
ffmpeg "${TONE[@]}" -c:a pcm_s16le "${OTHER_TAGS[@]}" pcm.wav
ffmpeg "${TONE[@]}" "${COVER_IN[@]}" "${COVER_MAP[@]}" -c:a pcm_s16be "${OTHER_TAGS[@]}" -write_id3v2 1 pcm.aiff
```

`chapters.txt`:

```
;FFMETADATA1
[CHAPTER]
TIMEBASE=1/1000
START=0
END=1000
title=Opening
[CHAPTER]
TIMEBASE=1/1000
START=1000
END=2000
title=Second part
```

`book.m4b` was then copied to the three `book-*.m4b` names before the TagLib steps above.
