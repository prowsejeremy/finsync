# hz: architecture and feature reference

Updated 2026-10-09, on `feature/fragment`, with T4 of the player and adapter split (Settings,
launch and sign-out) done and checked on the phone. Queue editing (2026-10-10) brings `:app` to
474 unit tests in 71 suites; `:tags` has 41 in 6, plus 95 instrumented tests, which run on a
phone.

Start here before extending the app. This document summarises what is built and the rules every
change follows. The specs hold the full reasoning behind each decision.

> **Planning docs are local only.** `docs/` and `.superpowers/` are gitignored. The specs, plans
> and handovers named below exist only on the machine they were written on. This file is the
> tracked summary of them.

## Contents

1. [What hz is](#what-hz-is)
2. [Status by sub-project](#status-by-sub-project)
3. [Ground rules for new work](#ground-rules-for-new-work)
4. [Code map](#code-map)
5. [Data](#data)
6. [Sync](#sync)
7. [Playback](#playback)
8. [Equaliser](#equaliser)
9. [Screens and navigation](#screens-and-navigation)
10. [Appearance and colour](#appearance-and-colour)
11. [Build, test and alpha builds](#build-test-and-alpha-builds)
12. [How work is planned and run](#how-work-is-planned-and-run)
13. [Recipes](#recipes)
14. [Known issues and loose ends](#known-issues-and-loose-ends)
15. [What's next](#whats-next)

## What hz is

A native Android music and audiobook player. It plays the audio files in one Library folder and
builds its whole library from their tags, artwork files and playlist files. A Jellyfin adapter
syncs chosen music and books from a server into that folder and tags them. Playback always works
offline.

It was called Finsync until 2026-10-06. The rename changed the application ID to `com.jpd.hz`, so
hz installs beside Finsync instead of upgrading it. The local specs, plans and handovers predate
the rename and still say Finsync and `com/jpd/finsync`.

- **Sign-in** to Jellyfin 12+, from Settings → Adapters → Jellyfin, by Quick Connect (the
  default) or username and password. hz opens and plays without one. Credentials are kept in
  `EncryptedSharedPreferences`.
- **Sync** of chosen albums, playlists and audiobooks. Sync is incremental, runs in a foreground
  service with a notification, and can repeat on a schedule (WorkManager).
- **The Library folder** (`Media/hz` by default) is scanned when the app opens, after each sync
  and on Rescan. Files added by hand show up like synced ones.
- **Library browsing** from Home, with six categories the user can reorder and hide: Albums,
  Album Artists, Genres, Songs, Playlists and Audio Books. Each has a list screen and a detail
  page.
- **Playback** through BASS, behind Media3's session layer:
  - gapless albums;
  - repeat and shuffle;
  - a queue sheet that moves and removes tracks, and Android's output switcher;
  - notification, lock screen and Bluetooth controls;
  - the queue restored after a force-stop, as it was left.
- **Mini-player and Player.** Swipe the mini-player, or the Player's art and title, to change
  track. A Player title too long for one line scrolls sideways.
- **Audiobooks** play as one queue item each. They have chapters, −15 s and +30 s skips, a
  pitch-preserving speed setting (0.8× to 2.0×) and a saved position per book.
- **A 10-band equaliser** with nine built-in presets, one Custom slot and presets the user saves
  under their own names. It applies to music and books alike.
- **Settings:** Library, Adapters, Appearance, Home screen and Equaliser rows. Adapters →
  Jellyfin holds the server pill, the Sync card and the sync choices.
- **Appearance:** Dark, Light or System mode, and five accent colours.

## Status by sub-project

The overhaul that turned the sync tool into a player was split into sub-projects. Each one has
its own spec, plan and alpha build, and each left the app working.

| # | Sub-project | State | Commits | Spec (in `docs/superpowers/specs/`) |
|---|---|---|---|---|
| — | Overview and shared rules | — | — | `2026-10-03-playback-overhaul-overview.md` |
| 1 | App shell and Settings | Done | `4be28ce` | `2026-10-03-app-shell-settings-design.md` |
| 2 | Playback, Player and Albums | Done | `7540151`, `b1097ac`, `20bc9d9` | `2026-10-04-playback-albums-design.md` |
| 3a | Album Artists, Genres and Songs | Done | `74dc7cb`, `8baf693` | `2026-10-05-album-artists-genres-songs-design.md` |
| 3b | Playlists and Audio Books | Done | `1568ca6`, `37c49f3`, `3d84132` | `2026-10-05-playlists-audiobooks-design.md` |
| 3b+ | Refinements: sync counts, mini-player, title, Home layout | Done | `59fd363`, `12c042b` | `2026-10-05-sp3b-refinements-design.md` |
| — | Settings restructure and Appearance | Done | `cecf0d8`, `81f5c39`, `16e5a63`, `93e3ec6` | `2026-10-05-settings-appearance-design.md` |
| 5 | Equaliser, plus swiping the Player to change track | Done | `3863dee` | `2026-10-05-equaliser-design.md` |
| 5+ | Equaliser saved presets | Done | `a2dd48d` | `2026-10-05-equaliser-saved-presets-design.md` |
| T1 | Player and adapter split, T1: the tag engine (`:tags`) | Done | `4a62e00`, `23bcac1` | `2026-10-07-player-adapter-split-design.md` |
| T2 | Player and adapter split, T2: the adapter writes the format | Done | `3fa93a8`, `2ca5ece` | `2026-10-07-player-adapter-split-design.md` |
| T3 | Player and adapter split, T3: the player reads the Library folder | Done | `5be7d56`, `6849e60`, `af84426` | `2026-10-07-player-adapter-split-design.md` |
| T4 | Player and adapter split, T4: Settings, launch and sign-out | Done | `d56fcd5`, `44af185` | `2026-10-07-player-adapter-split-design.md` |
| — | The adapter harness: any number of platforms and storage devices (Plex step 1) | Done | `9cbdc4e` | `2026-10-10-adapter-harness-design.md` |
| 4 | Search | Not designed | — | Overview row 4 only |

Sub-project 5 was built before 4 at the user's request. The plans are in `docs/superpowers/plans/`
under the same date and name, without `-design`.

## Ground rules for new work

These rules hold across the whole app. A change that breaks one needs a deliberate decision.

### The player and its adapters

The player and adapter split (spec `2026-10-07-player-adapter-split-design.md`) replaced the
overview's streaming seams 1 and 3 when T3 landed. These four rules hold:

1. **The player reads only the Library folder** (D1). Everything it shows comes from files:
   tags, `folder.jpg` and `artist.jpg`, and `.m3u8` playlists. An adapter's only link to it is
   the shell's rescan hook (`requestLibraryRescan` in `Hz.kt`), which carries no data.
2. **Screens read library data only through the repositories** (`LibraryRepository`,
   `PlaylistRepository`, `BookRepository`), never from Room DAOs directly.
3. **Stable IDs, relative paths** (spec "T3 amendment" A1–A2): an audio file's ID is a `fileId`
   given when a scan first sees it, and survives moves, renames and re-tags; its path is a field,
   relative to the Library folder. Artists and genres are keyed by normalised name, albums by album
   artists and name, playlists by path. The Library folder is the only full path hz saves. The
   play queue and book progress hold `fileId`s, and `TrackResolver` turns each into the file in
   today's Library folder just before it plays.
4. **Media3 supplies the session; BASS decodes and equalises.** The same engine runs on every
   platform, so formats and the EQ behave the same everywhere.

Streaming, if it comes, becomes an adapter feature.

### The adapter harness

The adapter side is a harness with platforms plugged into it (spec
`2026-10-10-adapter-harness-design.md`, H1–H9):

1. **The harness owns the write side; each platform owns the read side.** `adapter/` runs one
   generic sync for every connection (`SyncRun`): where a file lands, `.part` → tag → rename,
   records, the keep set, cleanup, folder moves, sync state, the service, schedules and
   sign-out's order. A platform supplies a `Source`: what's there, where each item lands, its
   bytes, its tags (or none) and its extras. A platform never writes file-handling code; if one
   doesn't fit, the harness gains a call.
2. **Connections, not platforms.** Each signed-in source is a `Connection`, `<platform>:<source
   ID>` (`jellyfin:3f2a…`), with its own folder, choices, records, catalogue, sync state and
   schedule. Connections sync one at a time under `FolderSetup.lock`. The UI allows one per
   platform for now.
3. **The boundary is a test.** `ArchitectureBoundaryTest` reads every main file's imports: the
   player imports nothing of `adapter/` or `platform/`; the harness imports no platform, and of
   the player only `LibraryFolderStore`; the shell's screens import no platform (only `Hz.kt`
   installs them); a platform imports nothing of the player. `appearance/`, `R`, view binding and
   `:tags` are shared. The harness's code names no platform.
4. **Every choice is a group; empty means none; `all` means every group.** Albums, playlists,
   books and folders alike. A connection with nothing chosen doesn't run, so an empty plan never
   reaches cleanup.
5. **Future installs are fresh installs.** `SyncDatabase` is rebuilt on a schema change, and no
   saved setting is converted (the user, 2026-10-10).

### Server and auth

- **hz never changes anything on a server.** Every HTTP adapter's clients add the harness's
  `ReadOnlyInterceptor` (`adapter/net/ReadOnlyInterceptor.kt`). It allows GET and HEAD, plus the
  POSTs its platform lists for its sign-in client only, and throws on anything else. Jellyfin's
  are `JellyfinClient.SIGN_IN_PATHS`: `Users/AuthenticateByName`, `QuickConnect/Initiate` and
  `Users/AuthenticateWithQuickConnect`. `QuickConnect/Authorize`, which approves another device's
  code, stays blocked. There is no play reporting. Adding some (play counts, now playing, book
  positions) would need a deliberate exception.
- **Jellyfin 12 auth.** Every request sends `Authorization: MediaBrowser Client=…, Token=…`
  (`JellyfinClient.buildAuthHeader`). Its `DeviceId` is the install's, a UUID kept in
  `noBackupFilesDir/device_id` (`platform/jellyfin/api/DeviceIdentity.kt`): Jellyfin ends a
  device's other sessions when it signs in again. Jellyfin 12 disables the old Emby headers and
  `api_key` URLs by default, so streaming must send this header too.
- New requests should use the documented `GET /Items?userId=…` routes, as 3b's do. A local copy
  of Jellyfin 12.1.0's OpenAPI is at `.superpowers/jellyfin-openapi-stable.json`.

### Portability (a possible iOS port)

- Keep rules and value tables in plain Kotlin with **no Android imports**: palettes, enums,
  saved-setting keys and pure decision functions. Kotlin Multiplatform could then reuse them, or
  they're short to rewrite in Swift. Examples: `appearance/Appearance.kt`, `appearance/Palette.kt`,
  `equaliser/Equaliser.kt`, `home/HomeLayout.kt`, `adapter/run/SyncPlan.kt`,
  `adapter/choices/ChoiceRules.kt`, `playback/QueueOrder.kt` and `ui/Settings/SyncDisplay.kt`.
- Android wiring sits beside the rules, usually as `*Store.kt` for SharedPreferences and
  `*Android.kt` for framework mapping.
- Name resources by role (`surface_2`, `status_good`), not by colour.
- Don't bend Android code out of shape for portability. The UI would be rewritten for iOS anyway.

### Code conventions

- Rethrow `CancellationException` in new code. Some older code still catches it; see
  [Known issues](#known-issues-and-loose-ends).
- Navigate with `navigateSafely()` (`ui/NavigationExtensions.kt`), which guards against
  double taps.
- Put text in `strings.xml`, with plurals for counts. Durations are `m:ss`, or `h:mm:ss` from an
  hour. Time left reads "8 h 28 min".
- Every UI file uses the package `com.jpd.hz.ui`, whatever its folder (`ui/Home`,
  `ui/Library`, `ui/Player`, `ui/Settings`, `ui/Equaliser`, `ui/Login`).
- Album selections: **an empty selection means every album**, which is the original behaviour.
  For playlists and books, **an empty selection means none**, so a fresh install doesn't download
  every audiobook on the server.

## Code map

All paths are under `app/src/main/java/com/jpd/hz/`.

```
Hz.kt                     Application: applies the saved night mode, installs the platforms
                          (Platforms.install), the rescan hook
adapter/                  THE HARNESS (names no platform): the contract (Platform, Connection,
                          Source, SourceCatalogue, SourceItem, ChoiceGroup, ChoiceKind)
  choices/                ChoiceRules (pure: empty = none, all = every group), ChoiceStore
  db/                     Room: SyncDatabase v10, records and catalogue keyed by connection
  files/                  .part → tag → rename (AdapterFiles), FileTagger, cleanup, playlist
                          files, LibraryLayout (shared paths for server platforms)
  folders/                adapter_folders, each connection's folder (ConnectionFolders), the
                          Library folder and its changes (FolderSetup, FolderMoves), the guard
  net/                    ReadOnlyInterceptor, with each platform's own sign-in POSTs
  run/                    SyncRun (the generic run), its pure plan, keep set, stored catalogue
                          and counts, Catalogue, SyncStates, extras, ConnectionSignOut
  service/                SyncService (the queue), SyncScheduler and SyncWorker, AutoSync
platform/jellyfin/        JELLYFIN: JellyfinPlatform, JellyfinSource, its catalogue mapping,
                          layout and tag mapping, JellyfinRepository (every server call)
  api/                    Retrofit interface, OkHttp client, auth header and device ID, SignInStatus
                          (a refused sign-in), ServerCheck, QuickConnect, the server DTOs
  ui/                     JellyfinSignInActivity and its view model (Quick Connect, password)
auth/                     CredentialStore (encrypted prefs): Jellyfin's, but agents can't read it,
                          so it stays here with model/ServerConfig.kt (spec H8)
library/                  Repositories for screens and playback, plus pure rules: grouping,
                          ordering, book chapters; the Library folder setting
library/db/               Room: the player's LibraryDatabase (what the scan found, and book
                          progress)
library/scan/             LibraryScanner and its parts: walk, file rules, TagLib reads, playlist
                          reading, deriving albums and artists, embedded covers
playback/                 PlaybackService, BassPlayer, BassEngine, TrackResolver, QueueOrder,
                          resume, audio focus, book controls, speed and progress
equaliser/                EQ rules and presets (pure) and EqualiserStore
appearance/               Palette and ThemeMode/Accent (pure), AppearanceStore, Android mapping
home/                     HomeCategory and HomeLayout (pure), HomeLayoutStore
ui/                       Activities and fragments; see "Screens and navigation"
com/un4seen/bass/         BASS, BASSmix and BASS_FX Java bindings (package fixed by JNI names)
```

Native libraries are in `app/src/main/jniLibs/<abi>/` for arm64-v8a, armeabi-v7a, x86 and x86_64:
`libbass`, `libbassmix`, `libbass_fx`, plus the format add-ons `libbassflac`, `libbassalac`,
`libbass_aac`, `libbassopus`, `libbassape` and `libbasswv`. BASS is free for non-commercial use
only, and hz is never sold.

The tag engine is its own Gradle module, `tags/` (package `com.jpd.hz.tags`), which the app
depends on. Sync writes tags through it (T2), always behind `adapter/files/FileTagger.kt`: the
JVM has no `libhztags.so`, so code that unit tests reach takes the interface and tests pass
fakes. The scanner reads through `library/scan/TagSource.kt` the same way.
- `src/main/cpp/`:
  - TagLib 2.3.2, vendored unmodified in `taglib/` with its licences;
  - hz's bridge: `hz_tags.cpp` holds the logic, and `hz_tags_jni.cpp` is the JNI glue;
  - CMake builds them into `libhztags.so` for the same four ABIs.
- `TagLibBridge`: `read`, `write` and `readCover`. Each takes a path plus the real extension, so
  a `.part` download works.
- Plain-Kotlin rules with no Android imports:
  - `TagField`, our fields;
  - `TagWriting`, `TagReading` and `Normalising`, which makes IDs;
  - `TagFingerprint`;
  - `BookChapters`, where Nero chapters win.

## Data

### The Library folder (`library_folder`)

`Media/hz` by default, or `Media/hz` in the app's external files folder without all-files
access. The scanner reads every file in it (spec "The Library folder format"):

| What | Rule |
|---|---|
| Audio | `mp3 flac m4a m4b ogg opus ape wv wav aif aiff`, any case. Hidden files and folders, and folders holding `.nomedia`, are skipped. |
| Books | Audio with a folder named `Audiobooks` (any case) anywhere in its path (D7). |
| Playlists | Every `.m3u8` and `.m3u`. Entries resolve from the playlist's own folder; entries outside the Library folder, or matching no song, are skipped. |
| Album and book art | `folder.jpg`, `folder.png`, `cover.jpg`, `cover.png` beside the first track (lowest disc, then track, then path), else the embedded cover, cached in `filesDir/embedded_art/`. |
| Artist photos | `artist.jpg` or `artist.png` in the folder above an album's, for its first album artist. |
| Playlist covers | `<playlist name>.jpg` or `.png` beside the playlist. |

Tags are read as the spec's "Our tags" says (`com.jpd.hz.tags.TagReading`): several values per
field split on `;` (D5), album artists fall back to the first artist, and an album is its album
artists plus its name, normalised.

Each adapter syncs into its own folder inside the Library folder, named after the server
(`kurage/`), and its cleanup never leaves it (D4).

### Room databases

| Database | Tables | Notes |
|---|---|---|
| `LibraryDatabase` (`hz_library.db`, v1, player) | `library_files` (`fileId`, relative `path`, stamps), `library_tracks`, `library_albums`, `library_artists`, `library_album_artists`, `library_track_artists`, `library_genres`, `library_track_genres`, `library_playlists`, `library_playlist_items`, `library_books`, `library_book_chapters`, `book_progress` | What the last scan found, and book progress by `fileId`. A scan updates it in place: one transaction replaces the scan's tables and keeps progress, except for books that have gone. **Every schema change needs a real migration** (A3). |
| `SyncDatabase` (`hz_sync.db`, v10, the adapters') | `synced_files`, `catalogue_items`, `catalogue_groups`, `catalogue_group_items`, every row keyed by `connectionId` | Each connection's sync records (a `synced_files` row per file a run wrote: path, `version`, size after tagging, `tagFingerprint`), and its copy of its source's catalogue: items, and the groups the user chooses from (albums, playlists, books, folders). A run plans from it; the Sync card and the choice screens read it. Rebuilt on a schema change (spec H5). |

- **Scanning** (`library/scan/LibraryScanner.kt`, one app-wide instance): walk the folder, stat
  each audio file (size, mtime, ctime, inode), and read only new or changed ones through TagLib,
  eight at a time (1,477 files in about 2.5 s on the user's Pixel 8; a pass with nothing to read
  takes about 1.3 s). A file keeps its `fileId` at the same path; at a new path, when its stamps
  match a file that vanished (a move or rename), or else when its tag key matches exactly one
  (a copy, a re-download; a book's authors and title, a song's album, disc, number and title).
  Rows of files that are gone are dropped. A file TagLib can't open is added if BASS can decode
  it, titled from its name.
  Playlists are parsed every pass. Only files really gone are dropped: a known file that can't be
  stamped or read keeps its rows, and so does every known file under a folder that can't be
  listed. A Library folder that can't be listed leaves the library as it was ("Can't read"), and
  so does one that holds no audio while the library has files, if it's the folder the library
  was scanned from (`scanned_folder`). An empty folder chosen in its place is simply empty: Home
  shows its empty state (fixed in T4's device check). A pass that ran across a move of files
  (`LibraryFolderStore.noteFolderChange`) writes nothing and runs again. A new file whose read
  throws is left out of that pass only; Rescan also re-reads unreadable files.
  Triggers: the app opens, a sync ends, the Library folder changes, and Rescan. One scan runs at
  a time; a request during one runs one more pass.
- **Migrations.** `SyncDatabase` has none: every version change rebuilds it
  (`fallbackToDestructiveMigration()`, spec H5), and the next sync re-links the files already on
  disk without downloading them, re-tagging each once.
  - **No effort goes into preserving data from earlier versions** (the user, 2026-10-08 and
    2026-10-10): future installs are treated as fresh. `LibraryDatabase` is the exception from T3
    on: it holds book progress, so every change to it gets a real migration.

### SharedPreferences

| File | Keys | Owner |
|---|---|---|
| `settings` | `choices:<connectionId>:<kind>` (string sets of group IDs, or `all`; kinds `album`, `playlist`, `book`, `folder`). Empty means none. `auto_sync:<connectionId>`: an interval in hours, or `disabled`. Sign-out keeps the choices and sets its `auto_sync` to `disabled`. | `adapter/choices/ChoiceStore.kt`, `adapter/service/AutoSync.kt` |
| `settings` | `adapter_folders` (T2): one `<connectionId>\|<path>` per line, the path relative to the Library folder (`kurage`), kept on sign-out. | `adapter/folders/AdapterFolderStore.kt`, `ConnectionFolders.folderFor` |
| `settings` | `library_folder` (T3): the Library folder's full path, the only one hz saves. `library_move`: a change of folder begun and not yet finished. `scanned_folder` (T4): the folder the last scan wrote the library from, saved just before the write; only it keeps the library when it looks empty. | `library/LibraryFolderStore.kt`, `adapter/folders/FolderSetup.kt`, `library/scan/LibraryScanner.kt` |
| `settings` | `theme_mode` (`dark`, `light`, `system`), `accent` (`green`, `blue`, `purple`, `pink`, `red`) | `appearance/AppearanceStore.kt` |
| `settings` | `home_order` (comma-separated keys), `home_hidden` (string set) | `home/HomeLayoutStore.kt` |
| `playback` | `resume_state` (source IDs, queue, index, position, repeat, shuffle), `book_speed` | `playback/ResumeStore.kt`, `playback/BookSpeedStore.kt` |
| `equaliser` | `enabled`, `preset` (a built-in key, `custom` or `saved_<id>`), `custom_gains`, `saved_presets` (one `<id>\|<gains>\|<name>` per line) | `equaliser/EqualiserStore.kt` |
| encrypted | server URL, user and token | `auth/CredentialStore.kt` |

Saved values name the choice, not its look (`accent=blue`, not a colour), so later palette
changes need no migration. Unknown values read as the default.

The EQ has its own file so that the 10-second resume saves in `playback` don't wake the playback
service's EQ listener.

### Files outside the Library folder

- `filesDir/embedded_art/`: covers found only inside audio files, one per album or book, written
  as found (Glide and Media3 decode by content). An empty file records "no cover". A scan
  deletes the ones it no longer needs.
- T3 deleted the private `filesDir/artist_images/` and `filesDir/playlist_images/`. Photos and
  covers now live in the Library folder.
- `noBackupFilesDir/device_id`: the install's Jellyfin device ID. Android never backs it up, so
  a restored backup can't give two installs one ID.

## Sync

Every connection syncs through the harness's one run, `adapter/run/SyncRun.kt` (spec
`2026-10-10-adapter-harness-design.md`, "The sync run"). Entry points: `SyncService` for a manual
sync (a foreground service of type `dataSync`), which queues connections and runs them one at a
time, and `SyncWorker` for each connection's schedule (unique work `hz_sync:<connectionId>`,
`adapter/service/SyncScheduler.kt`; WorkManager keeps it across reboots). Both call the shell's
rescan hook after each run, whatever the result. Each connection's state is in `SyncStates`,
which its page, its Adapters row, the Settings row and Home's ring read.

A run holds `FolderSetup.lock` from start to end, so no connection's folder moves under it; a
run waiting for it shows **Waiting**. Once it holds the lock, it reads the connection again and
runs nothing if it has signed out, and first finishes a change of Library folder cut short.

**Signing out** (`adapter/run/ConnectionSignOut.kt`) clears the connection's sign-in (through its
platform), its catalogue and its schedule. It keeps the files, its `adapter_folders` entry, its
records and its choices, so signing in again to the same source downloads and re-tags nothing and
finds the same choices.
- The sign-in goes first, so nothing new starts; then the schedule. It then waits for
  `FolderSetup.lock`, stopping that connection's run if it goes meanwhile (another connection's
  run finishes first), and clears its catalogue, unless the user has signed in again meanwhile.
  A new sign-in always refreshes the catalogue, so a sign-out cut short leaves nothing wrong.
- A run re-reads its connection once it holds the lock and runs nothing if it has gone, and a
  catalogue refresh doesn't write once its sign-in has gone. So a sign-out is never undone.
- **Each connection has its own choices** (`choices:<connectionId>:<kind>`) and its own records
  (`synced_files` by connection). A run's record pass drops only its own connection's records of
  missing files.

One run (Jellyfin's calls in brackets):

1. **Checks.** Still signed in; **something chosen** (a connection with nothing chosen stops here
   with "Choose what to sync first.", so an empty plan never reaches cleanup); the Library folder
   settled.
2. **Fetch and store the catalogue** before any download (`Source.catalogue()`; Jellyfin's whole
   music library from `Users/{userId}/Items`, `Audio`, paged 500 at a time, then the playlists
   and their entries, then `AudioBook` items with chapters, people and genres). A kind or group
   whose fetch failed keeps its previous rows. A failure of the whole fetch ends the run with its
   message.
3. **Plan** (`planOf`, pure): chosen albums' and folders' items in the source's order, then items
   only chosen playlists need, then chosen books, so new music isn't stuck behind a large book.
   An empty choice means none; `all` means every group of the kind.
4. **Download and tag** whatever is missing or changed (T2). Each file downloads to
   `<file>.part` (`Source.open`; Jellyfin's `Audio/{itemId}/stream?static=true`), is tagged there
   with the item's fields, if it has any (Jellyfin's `JellyfinTagMapping`, through `FileTagger`),
   then is renamed into place (`adapter/files/AdapterFiles.kt`). Its record keeps the size after
   tagging, the source's `version` and the `tagFingerprint`, so a tagged file never downloads
   again; a changed `version` downloads it again. A path that's absolute, holds `..`, or repeats
   an earlier item's (ignoring case) is never written and counts as failed.
   - A file that is on disk but has no record is re-linked without downloading.
   - A file on disk whose fingerprint differs from the server's fields is **re-tagged**, with no
     download: copied to `.part`, tagged there and renamed over the original. That's an edit on
     the server, or a file that was never tagged. The first sync after upgrading to T2 re-tags
     every file once: about 2–3 minutes for 21 GB on a Pixel 8.
   - Jellyfin's stream route fetches the original file with no transcoding.
5. **Clean up** (`mayCleanUp`), only when step 2 stored the catalogue, no part the user chose
   failed to load, and the plan isn't empty. Every file in the connection's folder outside the
   keep set (`keepSetOf`) is deleted, by `adapter/files/AdapterCleanup.kt`, which never touches
   anything outside the folder. Unchoosing an album, playlist or book therefore removes its files
   on the next full sync. A chosen part that didn't load would otherwise lose its files: after a
   sign-out or a rebuilt database, nothing else may know them.
   The keep set also holds a `folder.jpg` beside each planned album or book item, each extra
   (Jellyfin's `artist.jpg`) and each chosen playlist's file and cover.
6. **Covers, extras and playlist files** (`adapter/run/Extras.kt`), straight into the folder,
   beside files on the phone: each album's and book's missing cover beside its first planned
   item (`Source.openGroupImage`; Jellyfin's primary image), each missing extra, each chosen
   playlist's missing cover, and the `.m3u8` files. A failure is logged and retried next sync; it
   never fails the sync.

**Failures.** A failed item is skipped, not fatal. The sync ends **incomplete** ("Sync
incomplete: 2 items couldn't sync. They'll retry next sync."), and cleanup never deletes a file
a failed item might own. A file that couldn't be tagged plays with its own tags, and the next
sync tries again. When TagLib's write fails on a new download, that download is deleted, as it
may be half-written, and a fresh one is kept untagged. The Sync card adds "2 files couldn't be
tagged.", and that never makes the sync incomplete.

**On disk** (Jellyfin's, through the shared `LibraryLayout`; a storage platform keeps its
source's own paths):

| What | Where |
|---|---|
| Music | `<syncDir>/Music/<album artist>/<album>/<track>`, with `folder.jpg` |
| Artist photos (T2) | `<syncDir>/Music/<album artist>/artist.jpg`, for the album's first album artist |
| Books | `<syncDir>/Audiobooks/<author>/<title>/`, the `.m4b` plus `folder.jpg` |
| Playlists (T2) | `<syncDir>/Playlists/<name>.m3u8`, plus `<name>.jpg` for the cover |

**The folder** (`ConnectionFolders.folderFor`) is the connection's `adapter_folders` entry, fixed
the first time it's needed.
- A new connection gets `<its name>` in the Library folder, or beside the folder of a connection
  whose folder is the library or holds it, so no adapter's folder is ever inside another's. When
  that folder holds anything, or holds another connection's, it gets `<name> (<Platform>)`, then
  `(<Platform> 2)` (D4).
- Without all-files access, the folder is `Media/hz/<name>` in the app's own external files
  folder, and it isn't saved until access is granted. Public `Media/hz` is created only while no
  Library folder is saved; a fresh install's first settle saves it.
- **Settings → Library** changes the Library folder (`FolderSetup.plan`, `changeLibrary`; A4),
  for every saved connection folder. A folder inside one is refused. A connection's folder still
  where it was is kept if it's inside the new one, and otherwise renamed into it, after asking
  ("kurage's files will move to …"; to a free name: a target that exists, even empty, is taken).
  One moved by hand is found in the new folder by that connection's own records, signed in or
  not: 90% of them at their paths and sizes, and 90% of its audio recorded, so the user's own
  music is never taken for it. One found by searching is confirmed first, since sync's cleanup
  works there. A signed-in connection's folder not found stops the change ("Can't find kurage's
  files"); a signed-out one's stays as saved, and its next sync's folder guard stops it. Paths
  are relative, so no record is rewritten, and a failed rename changes nothing. While a sync
  runs, the change waits for it.
- **A sync also refuses** a folder that is, or holds, the Library folder, and a saved folder that's
  gone while the records name files in it (`syncFolderProblemOf`): recreating it would download
  everything again.

**Sync card.** `ui/Settings/SyncDisplay.kt` is a pure, ordered rule table that turns a
connection's state (`ConnectionUiState`) into a status: SIGN_IN_AGAIN, OFFLINE, WAITING, SYNCING,
STOPPED, NOTHING_CHOSEN, FAILED, INCOMPLETE, SYNCED or NOT_SYNCED. The counts come from
`adapter/run/SyncCounts.kt`, which applies the same choice rules as `planOf` to the stored
catalogue, so choosing a new playlist shows that a sync is needed. `ConnectionMonitor` keeps a
connection's state live for its page and rows. `SyncRowSummary.kt` builds the one-line summary
on the Adapters rows (`AdapterViews.kt`).

## Playback

```
Fragments ──▶ PlaybackViewModel (activity-scoped, wraps one MediaController)
                     │  MainActivity builds the controller in onStart and releases it in onStop
                     ▼
              MediaSession ──▶ PlaybackService (MediaSessionService, foreground "mediaPlayback")
                                   ├─ TrackResolver   IDs → MediaItems, via the repositories
                                   ├─ ResumeStore      saves the queue, restores it paused
                                   ├─ PlaybackFocus    audio focus, ducking, becoming-noisy
                                   ├─ BookProgressWriter → book_progress (LibraryDatabase)
                                   ├─ EqualiserStore listener → engine.setEqualiser
                                   └─ BassPlayer (Media3 SimpleBasePlayer: queue, repeat, shuffle)
                                          └─ BassEngine
                                               BASS → BASSmix queued mixer (48 kHz, float)
                                                       ├─ music: one decoder per track, next one queued
                                                       ├─ book: BASS_FX tempo stream round the decoder
                                                       └─ BASS_FX PEAKEQ on the mixer (10 bands)
```

- **Resolving.** Queue items carry only a `fileId` as `mediaId`. `TrackResolver` resolves a batch
  at a time, kept under SQLite's 999-parameter limit, through `LibraryRepository.playableTracks`
  and `BookRepository.playableBooks`, as the file at its relative path in today's Library folder
  (`LibraryFiles`). It adds the file URI, metadata and extras: codec, bit depth, sample
  rate, bitrate, size, and a book's chapters (`playback/TrackExtras.kt`). An ID with no library
  row or no file is skipped. If nothing resolves, the queue stays as it was and the UI shows
  "Files missing. Rescan your library."
- **Gapless.** The next track is queued in the mixer (`BASS_MIXER_QUEUE`), so it starts with no
  gap. Seeking moves the position in place without reopening the file, so book skips are quick.
- **Queue.** Playing from a list (album, All songs, playlist and so on) copies it into the queue,
  and `BassPlayer` keeps the list as the source. The Queue sheet moves tracks by their handle and
  swipes them away (not the playing one); edits change only the queue. Turning shuffle on or off
  rebuilds the queue from the whole source, shuffled or in order, and the playing track carries
  on. Adding and replacing do nothing (spec `2026-10-10-queue-editing-design.md`).
- **Order.** `QueueOrder` (pure) holds the repeat and shuffle rules and the queue's move and
  remove arithmetic, used by `BassPlayer`, the saved queue and the Queue sheet. Shuffle starts
  with the current track. Repeat cycles off → all → one. Previous restarts the track after 3 s.
- **Resume.** The queue is saved on play/pause, track change, seek, repeat and shuffle, a book's
  new chapter, and every 10 s while playing; `ResumeSaves` (pure) holds which events save. The
  save holds the source and the queue, so edits and a shuffled order come back as they were. A
  service starting with an empty queue restores it paused.
  `onPlaybackResumption` gives Bluetooth or lock-screen Play the same queue.
- **Lifecycle.** Swiped away while playing, the app keeps playing; while paused, the service
  stops. Signing out of Jellyfin leaves playback, the queue and book progress alone (D10).
- **Books.** A book replaces the queue and plays alone, with repeat and shuffle hidden.
  - Screens use only `PlaybackViewModel`'s chapter methods: `nextChapter`, `previousChapter`,
    `jumpToChapter`, `skipBack`, `skipForward` and `setSpeed`. Playing chapters as clipped queue
    items could later replace this without changing a screen.
  - While a book plays, the notification, lock screen and headset previous and next buttons skip
    −15 s and +30 s. The mini-player's buttons and swipes change chapter.
  - Progress saves with the resume state. Reaching the end marks the book Finished. One speed,
    in `book_speed`, applies to every book; music always plays at 1.0×.
- **Versions.** `media3-session` is pinned at 1.4.1, the last release that builds on compileSdk
  34. `mediarouter` 1.7.0 provides the Output switcher.

## Equaliser

| Part | Where | What it does |
|---|---|---|
| Rules | `equaliser/Equaliser.kt` (pure) | 10 octave bands centred 31.25 Hz to 16 kHz, 1 octave wide. ±12 dB in 0.1 dB steps (`clampGain`). `EqPreset` with nine built-ins plus `CUSTOM`. `EqChoice` is a built-in (or Custom) or a saved preset's id. `EqSettings` with `withBand` (always selects Custom, never changes a saved preset), `withChoice`, `withPreset`, `withEnabled`, `savedAs`, `withRenamed`, `withDeleted` and `activeGainsDb()`, which is null when off or flat. Also the `custom_gains` codec. `equaliser/SavedPresets.kt` holds `SavedPreset`, the name rules (`checkPresetName`) and the `saved_presets` codec. |
| Storage | `equaliser/EqualiserStore.kt` | `load`, `save` (one `apply()`) and `listen`. It holds the listener strongly, because SharedPreferences holds listeners weakly. |
| Engine | `BassEngine.setEqualiser` | One `BASS_FX_BFX_PEAKEQ` effect on the mixer, so it covers music and books, gapless changes and speed changes. Null removes the effect for a true bypass. The gains are kept and applied again whenever BASS starts. |
| Service | `PlaybackService.onCreate` | Loads the settings before BASS can start, then listens for changes. No session command is needed. |
| Screen | `ui/Equaliser/EqualiserFragment.kt`, `PresetList.kt`, `PresetNameEditor.kt`, `fragment_equaliser.xml` | Header switch, then a card of ten sliders with no coloured fill and a 0 dB mark, then a collapsible Preset card (built-ins, Your presets with ⋮ Rename/Delete, Custom). While Custom is chosen, a Save preset button opens an inline name editor. Delete shows an Undo bar. While the EQ is off, the controls are dimmed but still respond, and touching one turns it on. |
| Entry points | `nav_graph.xml` | `equaliserFragment` is top-level, reached from the Player's header icon (accent-coloured while on) and from the Settings row ("Off" or "On · Bass boost"). |

Two changes were made after the EQ spec, at the user's request. Gains are in tenths of a dB.
There is **no automatic headroom**: boosts make the sound louder and may clip, which the user
accepts. The spec still describes headroom in its body; its "Changed after M2" note at the top
takes precedence.

## Screens and navigation

**Activities.** `PermissionsActivity` is the launcher and goes on to `MainActivity`, signed in
or not (D10). `JellyfinSignInActivity` (`platform/jellyfin/ui/`, the platform's sign-in entry)
opens from Adapters → Jellyfin and returns there. It opens on
Quick Connect: Get code shows a code to approve in a signed-in Jellyfin app, checked every 5 s;
a link swaps in the username and password form. `MainActivity` hosts every other screen through
one `NavHostFragment`, above `miniPlayerContainer`. There's no tab bar: Home's header has a
round Settings button, which also shows a sync progress ring.

**Navigation graph** (`app/src/main/res/navigation/nav_graph.xml`):

```
nav_graph (start: homeFragment)
├── homeFragment
├── albumsFragment ─▶ albumFragment(albumId)
├── albumArtistsFragment, genresFragment ─▶ groupFragment(groupType, groupId) ─▶ allSongsFragment
├── songsFragment
├── playlistsFragment ─▶ playlistFragment(playlistId)
├── audioBooksFragment ─▶ bookFragment(bookId)
├── playerFragment            (global action from the mini-player; slides up)
├── equaliserFragment         (from the Player and from Settings)
└── settings_graph (start: settingsFragment)
    ├── settingsFragment
    ├── librarySettingsFragment   (T3: the Library folder and its scan)
    ├── appearanceFragment
    ├── homeScreenFragment
    └── adapters_graph (start: adaptersFragment)      (T4)
        ├── adaptersFragment                          one row per installed platform
        └── connection_graph(platform) (start: connectionFragment)   a platform's page
            ├── connectionFragment
            ├── choicesFragment(kind)                 albums, playlists, books or folders
            └── autoSyncFragment
```

The sync notification deep-links to `connectionFragment` with the running connection's platform,
with Home → Settings → Adapters behind it. Home's empty-state buttons navigate through the same
screens, one step at a time.

**View models.**
- `MainViewModel` is activity-scoped. At app open it finishes a change of Library folder cut
  short, then starts a scan; it refreshes each connection's empty catalogue, checks each
  connection's source (and again when the network changes) and gives Home's ring the running
  sync.
- `ConnectionViewModel` is scoped to `connection_graph`, with its `platform` argument: the
  page's connection, Sync card state (through `ConnectionMonitor`), choices, Auto-sync and
  sign-out. Its choice screens and the details sheet (`ServerBottomSheet`) share it.
- `PlaybackViewModel` is activity-scoped and mirrors the media controller.
- `SettingsViewModel` is scoped to `settings_graph` with `navGraphViewModels`: Appearance.
- `LibrarySettingsViewModel` is scoped to `settings_graph` too: the Library row and screen.
- Each browse screen has its own view model reading the repositories' Room `Flow`s, so screens
  update when a scan finishes.

**Home.**
- `home/HomeCategory.kt` is the enum of categories, in default order.
- `home/HomeLayout.kt` parses the saved order and hidden set. It drops unknown keys, appends
  missing categories and never hides the last one.
- `ui/Home/HomeCategoryViews.kt` is one `when` table that gives each category its title, icon,
  navigation action and count.
- `HomeLibraryState` covers Building (an empty library before its first scan finishes), Failed
  ("Can't read Media/hz", with Retry) and Ready (with counts, or "No music found in Media/hz."
  with Choose library folder and Set up an adapter, which opens Settings → Adapters).
  `MainViewModel` refreshes each connection's empty catalogue for the Sync card and choice
  screens, without a sync.

**Shared pieces.**
- Layouts: `view_screen_header.xml` (back and title), `view_settings_row.xml` (Settings rows),
  `view_mini_player.xml`, and the `item_*.xml` rows.
- Swipe to change track: `HorizontalSwipeLayout` detects the drag, `SwipeOutcome` (pure)
  decides, and `TrackSwipe` animates. The mini-player and the Player's art and title use them.
- `ScrollingTitleView` and its pure timing in `TitleScroll` scroll the Player's long titles.
- Sheets: `QueueSheet`, `ChaptersSheet`, `SpeedSheet` and `ServerBottomSheet`.

**Styling.** Cards are `surface_1`/`surface_2` with 14dp corners, titles use `hk_extrabold`,
icon boxes are 40dp, and the main action is an accent circle or pill. The design language comes
from the Findroid app.

## Appearance and colour

- **The values live in `appearance/Palette.kt`**, as seven base roles (`bg_primary`, `surface_1`,
  `surface_2`, `text_primary`, `muted`, `status_good`, `error`) and five accents, each with dark and light
  values. `values/colors.xml` (light) and `values-night/colors.xml` (dark) must match it, which
  `PaletteDriftTest` checks. The user tuned these values by hand, so treat them as final.
- **Themes.**
  - `Theme.Hz` has the parent `Theme.Material3.DayNight.NoActionBar`. It sets `colorError` to
    the `error` role (`#FF0040` in both modes), and `colorSurfaceContainer` to `surface_2` for
    popup menus.
  - Five overlays, `ThemeOverlay.Hz.Accent.<Name>`, each set only `colorPrimary` and
    `colorOnPrimary`.
  - `Activity.applyAccentOverlay()` applies the saved accent in each activity before
    `setContentView`.
  - `Hz.onCreate()` applies the saved mode, so there's no flash on launch.
  - Changing the mode lets AppCompat recreate the activities. Changing the accent calls
    `recreate()`. Neither interrupts playback.
- **Rules for any new UI:**
  - For the accent, use `?attr/colorPrimary` in XML, or `Context.accentColor()` in Kotlin
    (`ui/ThemeColors.kt`). Never use the `accent_<key>` colours directly; only the themes use
    them.
  - Labels and icons drawn on an accent fill use `?attr/colorOnPrimary`, never `bg_primary`.
  - Status items stay `status_good` whatever the accent: Connected, Synced, Finished and granted.
  - Icons are white-filled vectors (Material Symbols, Apache 2.0), tinted with a role colour
    wherever they're used.
- Known, accepted contrast: dark-mode `muted` text and light-mode `status_good` are faint.

## Build, test and alpha builds

| | |
|---|---|
| Toolchain | JDK 17, Kotlin 1.9.22, AGP 8.5.2, KSP, Room 2.6.1; NDK 28.2.13676358 and the SDK's CMake 3.22.1 for `:tags` |
| SDK | compileSdk and targetSdk 34, minSdk 26 |
| Version | `versionName "0.0.2"`, `versionCode 1` |
| Release | `minifyEnabled true` (R8) with no signing config, giving `app-release-unsigned.apk` |

Commands, run from the repo root:

```sh
./gradlew :app:testDebugUnitTest --rerun --console=plain   # unit tests
./gradlew :tags:testDebugUnitTest --rerun --console=plain  # the tag engine's unit tests
./gradlew :tags:connectedDebugAndroidTest                  # TagLib on a connected phone
./gradlew :app:assembleDebug                               # debug APK
./gradlew :app:assembleRelease                             # checks R8 and lintVitalRelease
```

- **Always pass `--rerun`.** Without it Gradle can report `testDebugUnitTest UP-TO-DATE` and run
  nothing. Check the timestamps of the XML reports in
  `app/build/test-results/testDebugUnitTest/`.
- **What is unit-tested.**
  - Pure rules use plain JUnit.
  - Room queries use Robolectric 4.13 with an in-memory database. `robolectric.properties` pins
    SDK 34, because SDK 35 needs JDK 21.
  - BASS, the service and the screens aren't unit-tested. Each spec's device checklist covers
    them.
  - TagLib can't load on the JVM, so `:tags` has instrumented tests in `tags/src/androidTest/`.
    On a phone, they write our fields into one sample per format (`assets/samples/`, described
    in `SAMPLES.md`) and check that everything else survives.
- **New features and fixes get tests.** Logic that can be pulled out into a pure function
  should be, so it can be tested on the JVM.
- **Alpha builds** install beside the release app as `com.jpd.hz.alpha`, "hz Alpha":

  ```sh
  ./gradlew -q -I .superpowers/alpha.init.gradle :app:assembleDebug
  ```

  The init script is local and gitignored. Copy the APK from `app/build/outputs/apk/debug/` to
  the repo root under a unique suffix, because every alpha of one version has the same name.
  **Before the alpha's first sync, set a different Library folder.** Both apps default to the
  same folder, and each one's orphan cleanup deletes the other's files.

## How work is planned and run

1. **Brainstorm** the design with the user. Mockups go in `.superpowers/brainstorm/`.
2. **Spec:** `docs/superpowers/specs/YYYY-MM-DD-<name>-design.md`. Every spec uses the same
   sections: Goal; Out of scope; Decisions, with the rejected options; Data; Screens; Navigation;
   Behaviour and edge cases; Testing; Device checklist; Needs the user's approval; Later ideas;
   Noticed, not in scope.
3. **Plan:** `docs/superpowers/plans/YYYY-MM-DD-<name>.md`. Plans carry full code and stop
   conditions, so the implementer never has to guess. Work is grouped into a few milestones (M1,
   M2, M3).
4. **Execute.** Each milestone gets one implementer run, which builds and runs the tests, then
   one code review against the plan and spec. Critical and Important findings are fixed. Minor
   ones, and anything that would change the plan, go to the user.
5. **Check and commit.** The user installs an alpha, runs the device checklist and commits.
   **Agents never commit or push.**
6. **Handovers** in `.superpowers/handover-*.md` carry state between sessions.

**Needs the user's OK first:**
- new dependencies;
- database migrations or rebuilds;
- deleting files;
- downloading native libraries;
- fetching Jellyfin documentation online. Only the 12.0 release post is pre-approved.

## Recipes

### Add a Home category

1. Add an entry to `HomeCategory` with a new, stable key. Saved layouts pick it up
   automatically, because `homeLayoutOf` appends missing categories.
2. Add its count to `HomeLibraryState.Ready`, and fill it in `ui/Home/LibraryViewModel.kt` from a
   repository `Flow`.
3. Add its row to the `when` table in `ui/Home/HomeCategoryViews.kt`: title, icon and action.
4. Add the destination and the action from `homeFragment` in `nav_graph.xml`, and build the screen
   from an existing list screen.
5. Extend `HomeLayoutTest` for the new entry.

### Add a library query or screen

- Add the SQL to `library/db/LibraryDao.kt`. Expose it from the right repository as a `Flow`.
  Never call the DAO from a screen. Everything in the Library folder shows; selections only
  drive the adapter's sync.
- Pure ordering and grouping go in `library/` with JUnit tests. Join queries get a Robolectric
  test, like `LibraryRepositoryTest`.
- Start playback through `PlaybackViewModel.playTracks(itemIds, startIndex, shuffle)` with
  `fileId`s.

### Add an adapter (a platform)

1. Make `platform/<name>/` with a `Platform` (its key, name, choice kinds, network need, sign-in
   screen, connections from its own saved sign-ins, `clearSignIn`) and a `Source` (availability,
   catalogue, `open`, group images, extras). Build server paths with `LibraryLayout`; a storage
   source keeps its own paths and gives no fields.
2. Its sign-in screen and any other screen of its own live in `platform/<name>/ui/`. An HTTP
   platform's clients add `ReadOnlyInterceptor` with its own sign-in POSTs.
3. Add it to `Platforms.install` in `Hz.kt`: the Adapters list, its page, the choice screens,
   the service, schedules and the Library screen pick it up.
4. Test its mapping on the JVM (as `JellyfinCatalogueMappingTest`), and run
   `ArchitectureBoundaryTest`. If it needs the harness to change, change the harness, so every
   adapter keeps the protections; never write file handling in a platform.

### Add a server request (Jellyfin)

- Add a GET to `platform/jellyfin/api/JellyfinApi.kt` that takes the `Authorization` header as a
  parameter, and call it from `JellyfinRepository`. Prefer `GET Items` with `userId`.
- `ReadOnlyInterceptor` refuses anything else. A non-GET needs the user's decision and an
  explicit exception.
- The player never sees server data. If the player needs it, sync writes it into the files
  (a tag, an image or a playlist). If only the adapter needs it, add it to
  `JellyfinCatalogueMapping`; a new field in the harness's catalogue changes `SyncDatabase`, which
  is rebuilt (H5).

### Add a setting

- Put the pure value and its parsing (an enum with `fromKey` and a default) in plain Kotlin, and
  the SharedPreferences read and write in a small `*Store.kt`.
- On the Settings screen, include `view_settings_row.xml` and refresh its summary in `onResume`.
  Put a connection's screens in `connection_graph` and other settings in `settings_graph`.
- If the playback service needs the value, use a separate prefs file and a listener, as
  `EqualiserStore` does.

## Known issues and loose ends

Each item was verified at `3863dee` or comes from a spec's "Noticed, not in scope" section.
None is scheduled.

- **Old library route.** `getAudioItems` uses `GET Users/{userId}/Items`, which Jellyfin
  12.1.0's OpenAPI doesn't list. It still works, but a later release may remove it. Move it to
  `GET Items?userId=`. (`getAlbums` and `getAlbumTracks` went with the harness: Albums to Sync
  reads the catalogue.)
- **Dead code.** `JellyfinApi.downloadAudio` is unused; real downloads use the client in
  `JellyfinClient`.
- **HTTP logging** is always on at BASIC level (`JellyfinRepository`, `debug = true`).
- **Cancellation.** `JellyfinRepository.safeCall` catches `CancellationException`; the
  harness's `Catalogue.refresh` works around it with `ensureActive()`. The fix is to rethrow it
  there.
- **Downloader.** The file downloader builds its own OkHttp client without
  `ReadOnlyInterceptor`. Its only request is a hard-coded GET, so it's safe, but routing it
  through the interceptor would add a backstop.
- **Stop waits for the current file** (found in T4). Stop cancels the run's coroutine, but the
  copy of the file being downloaded blocks until it ends. So Stop and sign-out wait for the
  current file to finish downloading, which is then thrown away and fetched again next sync. With
  Jellyfin's `readTimeout(0)`, a server that stalls mid-file holds `FolderSetup.lock` until it
  resumes. A server that hasn't answered yet doesn't: the wait for its reply is cancelled at once
  (`Call.executeCancellable`, `adapter/net/`).
- **One connection per platform** in the UI: the harness allows several, and the page would then
  take a connection ID, with "Add" on the Adapters screen.
- **Not seen yet.** Home may open twice on the first Android 12+ launch after an upgrade. If it
  happens, the fix is `CLEAR_TOP|SINGLE_TOP` in `PermissionsActivity.proceed()`.
- **README is stale** in places:
  - "No in-app playback" is no longer true;
  - the API table lists `/Audio/{id}/file`;
  - the architecture tree and the screenshots predate the overhaul.

## What's next

- **The player and adapter split** (spec `2026-10-07-player-adapter-split-design.md`). T1 to T4
  are done, and the user's release app was upgraded on 2026-10-09: its first launch moved
  `Media/hz`'s `Music/` and `Audiobooks/` into `Media/hz/kurage` (all 1,609 files), and its
  first sync re-tagged every file with nothing downloaded or deleted, adding artist photos and
  playlists. Next, the optional T5, Gradle modules. Search builds after it.
- **Sign-in health** (spec `2026-10-09-sign-in-health-design.md`): built; the phone check is
  next.
- **The adapter harness** (spec `2026-10-10-adapter-harness-design.md`): done; the phone check
  passed on 2026-10-11. Next, Plex: step 2 (its logic) and step 3 (its screens), once the user's
  Plex server with test data is ready.
- **Queue editing** (spec `2026-10-10-queue-editing-design.md`): built; the phone check is next.
- **Quick Connect sign-in** (spec `2026-10-10-quick-connect-sign-in-design.md`): built; the
  phone check is next.
- **Sub-project 4, Search.** Not designed yet. It will search albums, artists, songs, playlists
  and audiobooks. Points to settle:
  - Search reads the library through the repositories.
  - Home's header has room reserved for a Search button.
  - Browse sub-screens don't repeat the button for now.
- **Streaming,** if it's added. The overview sizes each piece:
  - network playback through `BASS_StreamCreateURL` with the `Authorization` header (small);
  - browsing the whole library online and downloads only offline (small to medium);
  - a quality setting with server transcoding (medium);
  - refreshing metadata on app open (small);
  - artwork for albums that aren't downloaded (small).
- **Later ideas from the specs:** an Artists category, Appears on, sort options, an A–Z index, a
  sleep timer, rewinding on resume, marking a book finished by hand, books split into several
  files, lyrics, colours from artwork, EQ settings per output, a manual preamp,
  and a smaller playback buffer if slider lag matters.
