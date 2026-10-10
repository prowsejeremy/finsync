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

### Server and auth

- **hz never changes anything on the server.** API calls pass through `ReadOnlyInterceptor`
  (`app/src/main/java/com/jpd/hz/api/JellyfinClient.kt`). It allows GET and HEAD, plus three
  sign-in POSTs on the sign-in client only (`Users/AuthenticateByName`, `QuickConnect/Initiate`,
  `Users/AuthenticateWithQuickConnect`; `ReadOnlyInterceptor.allows`), and throws on anything
  else. `QuickConnect/Authorize`, which approves another device's code, stays blocked. There is
  no play reporting. Adding some (play counts, now playing, book positions) would need a
  deliberate exception.
- **Jellyfin 12 auth.** Every request sends `Authorization: MediaBrowser Client=…, Token=…`
  (`JellyfinClient.buildAuthHeader`). Its `DeviceId` is the install's, a UUID kept in
  `noBackupFilesDir/device_id` (`api/DeviceIdentity.kt`): Jellyfin ends a device's other
  sessions when it signs in again. Jellyfin 12 disables the old Emby headers and `api_key`
  URLs by default, so streaming must send this header too.
- New requests should use the documented `GET /Items?userId=…` routes, as 3b's do. A local copy
  of Jellyfin 12.1.0's OpenAPI is at `.superpowers/jellyfin-openapi-stable.json`.

### Portability (a possible iOS port)

- Keep rules and value tables in plain Kotlin with **no Android imports**: palettes, enums,
  saved-setting keys and pure decision functions. Kotlin Multiplatform could then reuse them, or
  they're short to rewrite in Swift. Examples: `appearance/Appearance.kt`, `appearance/Palette.kt`,
  `equaliser/Equaliser.kt`, `home/HomeLayout.kt`, `sync/SyncPlan.kt`, `sync/SyncCounts.kt`,
  `playback/QueueOrder.kt` and `ui/Settings/SyncDisplay.kt`.
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
Hz.kt                     Application: applies the saved night mode before any activity starts
api/                      Retrofit interface, OkHttp client, auth header and device ID,
                          ReadOnlyInterceptor, SignInStatus (a refused sign-in), ServerCheck,
                          QuickConnect (a code's start and poll)
auth/                     CredentialStore (encrypted prefs), JellyfinRepository (every server call)
model/Models.kt           Server DTOs (MediaItem, MediaStream…), ServerConfig, SyncState
db/                       Room: the Jellyfin adapter's SyncDatabase v9 (sync records, catalogue)
library/                  Repositories for screens and playback, plus pure rules: grouping,
                          ordering, book chapters; the Library folder setting
library/db/               Room: the player's LibraryDatabase (what the scan found, and book
                          progress)
library/scan/             LibraryScanner and its parts: walk, file rules, TagLib reads, playlist
                          reading, deriving albums and artists, embedded covers
adapter/                  Code any adapter shares (T2, D12): folder naming and adapter_folders,
                          FileTagger in front of TagLibBridge, tag-then-rename, playlist files,
                          cleanup scoped to the adapter's folder, folder moves (T3)
sync/                     SyncEngine and its pure parts: SyncPlan, SyncPaths, SyncCounts,
                          JellyfinTagMapping; JellyfinCatalogue; artist photos, covers and
                          playlist files; FolderSetup (the Library folder and its moves);
                          JellyfinSignOut (T4)
service/                  SyncService (foreground sync), BootReceiver and SyncWorker (WorkManager)
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
depends on. Sync writes tags through it (T2), always behind `adapter/FileTagger.kt`: the JVM has
no `libhztags.so`, so code that unit tests reach takes the interface and tests pass fakes. The
scanner reads through `library/scan/TagSource.kt` the same way.
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
| `SyncDatabase` (`hz_sync.db`, v9, Jellyfin adapter) | `synced_tracks`, `synced_albums`, `catalogue_tracks`, `catalogue_playlists`, `catalogue_playlist_items`, `catalogue_books` | Sync records (a `synced_tracks` row per file sync wrote, with `tagFingerprint`), and the server's catalogue, which sync plans from and the Sync card and choice screens read. |

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
- **Migrations.** `SyncDatabase`: `MIGRATION_4_5`, `MIGRATION_7_8` (T2, `tagFingerprint`) and
  `MIGRATION_8_9` (T3, drops the eight tables the adapter no longer reads, `book_progress`
  among them). Versions 6 and 7 rebuilt it through `fallbackToDestructiveMigration()`. The next
  sync re-links files already on disk without downloading them again.
  - **No effort goes into preserving data from earlier versions** (the user, 2026-10-08): hz has
    one user, so T3 carried no queue or book progress over. The schema isn't exported, so
    `SyncDatabaseMigrationTest` builds old database files by hand. `LibraryDatabase` is the
    exception from T3 on: it holds book progress, so every change to it gets a real migration.

### SharedPreferences

| File | Keys | Owner |
|---|---|---|
| `settings` | `selected_albums`, `selected_playlists`, `selected_books` (string sets); `auto_sync_interval`, `auto_sync_on_boot`. Sign-out keeps the selections and sets `auto_sync_interval` to `disabled` (T4). `selections_server`: whose selections they are; another server's wait under `selected_albums:<serverId>` and so on (T4). | Settings view models, `library/SyncSelections.kt`, `SyncEngine`, `BootReceiver`, `sync/JellyfinSignOut.kt` |
| `settings` | `adapter_folders` (T2): one `<adapter>:<serverId>\|<path>` per line, kept on sign-out. From T3 the path is relative to the Library folder (`kurage`); a full path is a pre-T3 one, until the first launch settles it. `sync_directory` (before T2) is read only to fill the first entry, and removed once settled. | `adapter/AdapterFolderStore.kt`, `SyncEngine.getSyncDirectory` |
| `settings` | `library_folder` (T3): the Library folder's full path, the only one hz saves. `library_move`: a change of folder begun and not yet finished. `scanned_folder` (T4): the folder the last scan wrote the library from, saved just before the write; only it keeps the library when it looks empty. | `library/LibraryFolderStore.kt`, `sync/FolderSetup.kt`, `library/scan/LibraryScanner.kt` |
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

Entry points: `SyncService` for a manual sync (a foreground service of type `dataSync`), and
`SyncWorker` for scheduled syncs (in `service/BootReceiver.kt`, unique work
`hz_periodic_sync`). Both run `SyncEngine.syncLibrary`, then call the shell's rescan hook,
whatever the result. Its state flows into `MainViewModel.uiState`, which every screen that shows
sync or server state reads.

A sync holds `FolderSetup.lock` from start to end, so Jellyfin's folder never moves under it, and
first settles the Library folder if that hasn't happened (see "The folder" below).

**Signing out** (`sync/JellyfinSignOut.kt`, T4) clears the sign-in, the catalogue and the
auto-sync schedule. It keeps the files, `adapter_folders`, the sync records and the selections:
an empty album selection means every album and an empty book selection none, so clearing them
would make the next sync download the whole server and delete every book (the user's change to
the spec, 2026-10-09). Signing in again to the same server downloads and re-tags nothing.
- The sign-in goes first, so nothing new starts; then the schedule. It then waits for
  `FolderSetup.lock`, stopping any sync that runs meanwhile, and clears the catalogue, unless the
  user has signed in again meanwhile. A new sign-in always refreshes the catalogue, so a
  sign-out cut short leaves nothing wrong.
- A sync re-reads the sign-in once it holds the lock and runs nothing if it has changed, and a
  catalogue refresh doesn't write once its sign-in has gone. So a sign-out is never undone.
- **Each server has its own selections** (`SyncSelections.useFor`, at sign-in and at each sync's
  start): another server's are put aside under `<key>:<serverId>`, and come back when it signs
  in again. A server never signed in to starts as a fresh install does. Selections are server
  IDs, so one server's in force for another would plan none of its albums, and cleanup would
  delete its files.
- **A sync's record pass** drops only the signed-in server's records of missing files, so another
  server's stay, and signing in there again re-tags nothing. `synced_tracks.localPath` is unique,
  so a relative path both servers record is held once; that file is re-tagged once.

One run:

1. **Fetch** the whole music library (`Users/{userId}/Items`, `Audio`, paged 500 at a time),
   then the playlists and their entries, then `AudioBook` items with chapters, people and
   genres.
2. **Write the catalogue** (`JellyfinCatalogue`) before any download, so a cancelled sync still
   leaves it current. A failed playlist or book fetch keeps that part of the old catalogue.
3. **Plan** with `syncPlanOf`: selected albums' tracks, then tracks only selected playlists need,
   then selected books. Books go last so new music isn't stuck behind a large book.
4. **Download and tag** whatever is missing or changed (T2). Each file downloads to
   `<file>.part`, is tagged there with our fields (`JellyfinTagMapping`, through `FileTagger`),
   then is renamed into place (`adapter/AdapterFiles.kt`). Its record keeps the size after
   tagging and the `tagFingerprint`, so a tagged file never downloads again.
   - A file that is on disk but has no record is re-linked without downloading
     (`SyncEngine.needsDownload`).
   - A file on disk whose fingerprint differs from the server's fields is **re-tagged**, with no
     download: copied to `.part`, tagged there and renamed over the original. That's an edit on
     the server, or a file that was never tagged. The first sync after upgrading to T2 re-tags
     every file once: about 2–3 minutes for 21 GB on a Pixel 8.
   - Downloads use `Audio/{itemId}/stream?static=true`, which fetches the original file with no
     transcoding. Each album's `folder.jpg` downloads alongside its tracks.
5. **Clean up.** Every file in the sync folder outside `filesToKeep` (`sync/SyncPaths.kt`) is
   deleted, by `adapter/AdapterCleanup.kt`, which never touches anything outside the folder.
   Deselecting an album, playlist or book therefore removes its files on the next sync. The keep
   set also holds each album artist's `artist.jpg` and each selected playlist's file and cover.
6. **Artist photos, covers and playlist files,** straight into the sync folder:
   `ArtistPhotoSync` fetches each missing `artist.jpg`, `CoverSync` each missing playlist cover and
   book `folder.jpg`, and `PlaylistFileSync` writes the `.m3u8` files. A failed photo, cover or
   file is logged and retried next sync; it never fails the sync.

**Failures.** A failed item is skipped, not fatal. The sync ends **incomplete** ("Sync
incomplete: 2 items couldn't sync. They'll retry next sync."), and cleanup never deletes a file
a failed item might own. A file that couldn't be tagged plays with its own tags, and the next
sync tries again. When TagLib's write fails on a new download, that download is deleted, as it
may be half-written, and a fresh one is kept untagged. The Sync card adds "2 files couldn't be
tagged.", and that never makes the sync incomplete.

**On disk:**

| What | Where |
|---|---|
| Music | `<syncDir>/Music/<album artist>/<album>/<track>`, with `folder.jpg` |
| Artist photos (T2) | `<syncDir>/Music/<album artist>/artist.jpg`, for the album's first album artist |
| Books | `<syncDir>/Audiobooks/<author>/<title>/`, the `.m4b` plus `folder.jpg` |
| Playlists (T2) | `<syncDir>/Playlists/<name>.m3u8`, plus `<name>.jpg` for the cover |

**The folder** (`SyncEngine.getSyncDirectory`) is the server's `adapter_folders` entry, fixed
the first time it's needed.
- A new server gets `<server name>` in the Library folder, or beside the folder of a server whose
  folder is the library or holds it, so no adapter's folder is ever inside another's. When that
  folder holds anything, or holds another server's, it gets `<server name> (Jellyfin)`, then
  `(Jellyfin 2)` (D4).
- Without all-files access, the folder is `Media/hz/<server name>` in the app's own external files
  folder, and it isn't saved until access is granted. Public `Media/hz` is created only while no
  Library folder is saved.
- **The first launch after T3** (`FolderSetup.settle`, at app open or a sync's start) settles the
  Library folder from where Jellyfin synced before. A folder in `Media/hz` keeps `Media/hz` as
  the library. A folder picked by hand, as the user's `Media/hz` was, becomes the library, and
  its `Music/`, `Audiobooks/` and `Playlists/` are renamed into `<it>/<server name>`. The same
  step makes the sync records relative to Jellyfin's folder (A1). The change is saved
  (`library_move`) before the first rename, so one cut short is finished by the next settle; a
  failed rename puts the others back and saves nothing. Until the folder is settled, syncs stop
  with an error, and so does a change of folder. Each sync also makes any record still saved with
  a full path relative to its folder, as a backstop.
- **Settings → Library** changes the Library folder (`FolderSetup.plan`, `changeLibrary`; A4). A
  folder inside Jellyfin's is refused. Jellyfin's folder still where it was is kept if it's inside
  the new one, and otherwise renamed into it, after asking (to a free name: a target that exists,
  even empty, is taken). One moved by hand is found in the new folder by its files: 90% of the
  records at their paths and sizes, and 90% of its audio recorded, so the user's own music is never
  taken for it. Signed out, only a lone saved folder is looked for, by every record (T4). One
  found by searching is confirmed first, since sync's cleanup works there. Not
  found, the change says "Can't find Jellyfin's files" and changes nothing. Paths are relative, so
  no record is rewritten, and a failed rename changes nothing. While a sync runs, the change waits
  for it.
- **A sync also refuses** a folder that is, or holds, the Library folder, and a saved folder that's
  gone while the records name files in it (`syncFolderProblemOf`): recreating it would download
  everything again.

**Sync card.** `ui/Settings/SyncDisplay.kt` is a pure, ordered rule table that turns the state
into a status: OFFLINE, SYNCING, STOPPED, FAILED, INCOMPLETE, SYNCED or NOT_SYNCED. The counts
come from `sync/SyncCounts.kt`, which applies the same selection rules as `syncPlanOf`, so
choosing a new playlist shows that a sync is needed. `SyncRowSummary.kt` builds the one-line
summary on the Adapters rows (`AdapterViews.kt`).

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
or not (D10). `LoginActivity` opens from Adapters → Jellyfin and returns there. It opens on
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
        ├── adaptersFragment
        └── sync_graph (start: syncSettingsFragment)  Jellyfin's page
            ├── syncSettingsFragment
            ├── albumSelectionFragment, playlistSelectionFragment, bookSelectionFragment
            └── autoSyncFragment
```

The sync notification deep-links to `syncSettingsFragment`, with Home → Settings → Adapters behind
it. Home's empty-state buttons navigate through the same screens, one step at a time.

**View models.**
- `MainViewModel` is activity-scoped. It holds the sign-in, server and sync state, sync counts
  and sign-out, and at app open settles the Library folder, then starts a scan. Its `jellyfin` is
  the `Adapter` (name, status `Flow`, folder, sign-out; `ui/Settings/Adapter.kt`, D12) that
  Settings → Adapters lists.
- `PlaybackViewModel` is activity-scoped and mirrors the media controller.
- `SettingsViewModel` is scoped to `settings_graph` with `navGraphViewModels`.
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
  with Choose library folder and Set up Jellyfin, T4).
  `MainViewModel` refreshes an empty catalogue for the Sync card and choice screens, without a
  sync.

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

### Add a server request

- Add a GET to `api/JellyfinApi.kt` that takes the `Authorization` header as a parameter, and
  call it from `JellyfinRepository`. Prefer `GET Items` with `userId`.
- `ReadOnlyInterceptor` refuses anything else. A non-GET needs the user's decision and an
  explicit exception.
- The player never sees server data. If the player needs it, sync writes it into the files
  (a tag, an image or a playlist). If only the adapter needs it, add it to `JellyfinCatalogue`'s
  write and `CatalogueDao.replaceCatalogue`, bump `SyncDatabase` and decide about a migration.

### Add a setting

- Put the pure value and its parsing (an enum with `fromKey` and a default) in plain Kotlin, and
  the SharedPreferences read and write in a small `*Store.kt`.
- On the Settings screen, include `view_settings_row.xml` and refresh its summary in `onResume`.
  Put sync-related screens in `sync_graph` and other settings in `settings_graph`.
- If the playback service needs the value, use a separate prefs file and a listener, as
  `EqualiserStore` does.

## Known issues and loose ends

Each item was verified at `3863dee` or comes from a spec's "Noticed, not in scope" section.
None is scheduled.

- **Old library routes.** `getAudioItems`, `getAlbums` and `getAlbumTracks` use
  `GET Users/{userId}/Items`, which Jellyfin 12.1.0's OpenAPI doesn't list. It still works, but a
  later release may remove it. Move them to `GET Items?userId=`.
- **Dead code.** `JellyfinApi.downloadAudio` is unused; real downloads use the client in
  `JellyfinClient`.
- **HTTP logging** is always on at BASIC level (`JellyfinRepository`, `debug = true`).
- **Cancellation.** `JellyfinRepository.safeCall` catches `CancellationException`;
  `JellyfinCatalogue.refresh` works around it with `ensureActive()`. The fix is to rethrow it
  there.
- **Unticking every album** saves an empty selection, which means every album
  (`selectsEveryAlbum`), so the next sync downloads the whole server. Needs a decision.
- **Downloader.** The file downloader builds its own OkHttp client without
  `ReadOnlyInterceptor`. Its only request is a hard-coded GET, so it's safe, but routing it
  through the interceptor would add a backstop.
- **Artwork.** `SyncEngine` swallows album-art download failures.
- **Strings.** `fragment_album_selection.xml` hard-codes its English text.
- **Stop waits for the current file** (found in T4). `cancelAudioDownload` cancels the download
  of the `JellyfinRepository` it's called on, but a sync downloads through its own, and even
  that one is registered only until the response's headers arrive. So Stop and sign-out wait for
  the current file to finish downloading, which is then thrown away and fetched again next sync.
  With `readTimeout(0)`, a server that stalls mid-file holds `FolderSetup.lock` until it resumes.
- **`auto_sync_on_boot`** is read by `BootReceiver` but nothing writes it.
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
