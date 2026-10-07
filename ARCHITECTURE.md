# hz: architecture and feature reference

Updated 2026-10-08, on `feature/fragment` after `23bcac1`, with T2 of the player and adapter
split (the adapter writes the format) in progress. 336 unit tests in 53 suites pass: 295 in 47
for `:app` and 41 in 6 for `:tags`. `:tags` also has 95 instrumented tests, which run on a phone.

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

A native Android music and audiobook player for a Jellyfin server. It syncs chosen music and
books to the device, then plays only those local files. Playback always works offline.

It was called Finsync until 2026-10-06. The rename changed the application ID to `com.jpd.hz`, so
hz installs beside Finsync instead of upgrading it. The local specs, plans and handovers predate
the rename and still say Finsync and `com/jpd/finsync`.

- **Sign-in** to Jellyfin 12+. Credentials are kept in `EncryptedSharedPreferences`.
- **Sync** of chosen albums, playlists and audiobooks. Sync is incremental, runs in a foreground
  service with a notification, and can repeat on a schedule (WorkManager).
- **Library browsing** from Home, with six categories the user can reorder and hide: Albums,
  Album Artists, Genres, Songs, Playlists and Audio Books. Each has a list screen and a detail
  page.
- **Playback** through BASS, behind Media3's session layer:
  - gapless albums;
  - repeat and shuffle;
  - a queue sheet and Android's output switcher;
  - notification, lock screen and Bluetooth controls;
  - the queue restored after a force-stop.
- **Mini-player and Player.** Swipe the mini-player, or the Player's art and title, to change
  track. A Player title too long for one line scrolls sideways.
- **Audiobooks** play as one queue item each. They have chapters, −15 s and +30 s skips, a
  pitch-preserving speed setting (0.8× to 2.0×) and a saved position per book.
- **A 10-band equaliser** with nine built-in presets, one Custom slot and presets the user saves
  under their own names. It applies to music and books alike.
- **Settings:** the server pill, then Sync, Appearance, Home screen and Equaliser rows.
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
| T2 | Player and adapter split, T2: the adapter writes the format | In progress | — | `2026-10-07-player-adapter-split-design.md` |
| 4 | Search | Not designed | — | Overview row 4 only |

Sub-project 5 was built before 4 at the user's request. The plans are in `docs/superpowers/plans/`
under the same date and name, without `-design`.

## Ground rules for new work

These rules hold across the whole app. A change that breaks one needs a deliberate decision.

### The four streaming seams

Streaming from the server is a likely later addition. These four rules keep it cheap:

1. **Store the whole library's metadata.** Sync already fetches it, so the catalogue tables keep
   every track, keyed by Jellyfin ID. A track is downloaded when it has a `synced_tracks` row.
   Screens filter to downloaded items; streaming would lift that filter while online.
2. **Screens read library data only through the repositories** (`LibraryRepository`,
   `PlaylistRepository`, `BookRepository`), never from Room DAOs directly.
3. **The play queue holds Jellyfin item IDs, not file paths.** `TrackResolver` turns an ID into
   something playable just before it plays. Today that is always a local file.
4. **Media3 supplies the session; BASS decodes and equalises.** The same engine runs on every
   platform, so formats and the EQ behave the same everywhere. BASS can stream HTTP with custom
   headers.

### Server and auth

- **hz never changes anything on the server.** API calls pass through `ReadOnlyInterceptor`
  (`app/src/main/java/com/jpd/hz/api/JellyfinClient.kt`). It allows GET and HEAD, plus the
  login POST, and throws on anything else. There is no play reporting. Adding some (play counts,
  now playing, book positions) would need a deliberate exception.
- **Jellyfin 12 auth.** Every request sends `Authorization: MediaBrowser Client=…, Token=…`
  (`JellyfinClient.buildAuthHeader`). Jellyfin 12 disables the old Emby headers and `api_key`
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
api/                      Retrofit interface, OkHttp client, auth header, ReadOnlyInterceptor
auth/                     CredentialStore (encrypted prefs), JellyfinRepository (every server call)
model/Models.kt           Server DTOs (MediaItem, MediaStream…), ServerConfig, SyncState
db/                       Room: SyncDatabase v8, sync tables, catalogue tables, book_progress, DAOs
library/                  Repositories for screens and playback, plus pure rules: visibility,
                          grouping, ordering, mapping server items to rows, book chapters, artwork
adapter/                  Code any adapter shares (T2, D12): folder naming and adapter_folders,
                          FileTagger in front of TagLibBridge, tag-then-rename, playlist files,
                          cleanup scoped to the adapter's folder
sync/                     SyncEngine and its pure parts: SyncPlan, SyncPaths, SyncCounts,
                          JellyfinTagMapping, cover and artist-photo sync
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
depends on. Nothing in the app calls it yet; T2's sync and T3's scanner will.
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

### Room database (`db/SyncDatabase.kt`, version 8)

| Group | Tables | Notes |
|---|---|---|
| Sync records | `synced_tracks`, `synced_albums` | One row per file sync wrote. A `synced_tracks` row is the "downloaded" flag. Books have rows too. From version 8, `tagFingerprint` is the `TagFingerprint` of the fields sync last wrote into the file (null until it's tagged), and `fileSize` is the size after tagging. |
| Catalogue | `catalogue_albums`, `catalogue_tracks`, `catalogue_artists`, `catalogue_album_artists`, `catalogue_track_artists`, `catalogue_genres`, `catalogue_track_genres`, `catalogue_playlists`, `catalogue_playlist_items`, `catalogue_books`, `catalogue_book_chapters` | The whole server library. A single transaction replaces every table on each refresh, and logout clears them. |
| Progress | `book_progress` | `bookId`, `positionMs`, `finished`, `lastPlayedAt`. Refreshes don't touch it; logout clears it. |

- Link tables have no foreign keys. Playlist items are keyed by position, so one song can appear
  twice in a playlist.
- **Visibility.** A downloaded track is visible when its album passes the album selection, or
  it's in a selected playlist. A downloaded track with no album is always visible. An album is
  visible when it has a visible track. A track downloaded only for a playlist therefore makes its
  album a partial album.
- **Migrations.** `MIGRATION_4_5` and `MIGRATION_7_8` (T2, adds `tagFingerprint`) are real
  migrations. Versions 6 and 7 rebuilt the database through `fallbackToDestructiveMigration()`,
  because the user is the only user. The next sync re-links files already on disk without
  downloading them again.
  - **No effort goes into preserving data from earlier versions** (the user, 2026-10-08): hz has
    one user, so a schema change may rebuild the database, even though `book_progress` holds
    data no sync can restore. `MIGRATION_7_8` was approved before that note. The schema isn't
    exported, so `SyncDatabaseMigrationTest` builds a version 7 file by hand and opens it
    through the migration alone.

### SharedPreferences

| File | Keys | Owner |
|---|---|---|
| `settings` | `selected_albums`, `selected_playlists`, `selected_books` (string sets); `sync_directory`; `auto_sync_interval`, `auto_sync_on_boot` | Settings view models, `library/SyncSelections.kt`, `SyncEngine`, `BootReceiver` |
| `settings` | `theme_mode` (`dark`, `light`, `system`), `accent` (`green`, `blue`, `purple`, `pink`, `red`) | `appearance/AppearanceStore.kt` |
| `settings` | `home_order` (comma-separated keys), `home_hidden` (string set) | `home/HomeLayoutStore.kt` |
| `playback` | `resume_state` (queue IDs, index, position, repeat, shuffle), `book_speed` | `playback/ResumeStore.kt`, `playback/BookSpeedStore.kt` |
| `equaliser` | `enabled`, `preset` (a built-in key, `custom` or `saved_<id>`), `custom_gains`, `saved_presets` (one `<id>\|<gains>\|<name>` per line) | `equaliser/EqualiserStore.kt` |
| encrypted | server URL, user and token | `auth/CredentialStore.kt` |

Saved values name the choice, not its look (`accent=blue`, not a colour), so later palette
changes need no migration. Unknown values read as the default.

The EQ has its own file so that the 10-second resume saves in `playback` don't wake the playback
service's EQ listener.

### Files outside the sync folder

- `filesDir/artist_images/<artistId>.jpg` and `filesDir/playlist_images/<playlistId>.jpg`. They
  live in private storage because sync's orphan cleanup deletes unknown files in the music
  folder. A photo exists when its file does. Logout deletes both folders.

## Sync

Entry points: `SyncService` for a manual sync (a foreground service of type `dataSync`), and
`SyncWorker` for scheduled syncs (in `service/BootReceiver.kt`, unique work
`hz_periodic_sync`). Both run `SyncEngine.syncLibrary`. Its state flows into
`MainViewModel.uiState`, which every screen that shows sync or server state reads.

One run:

1. **Fetch** the whole music library (`Users/{userId}/Items`, `Audio`, paged 500 at a time),
   then the playlists and their entries, then `AudioBook` items with chapters and people.
2. **Write the catalogue** before any download, so a cancelled sync still leaves it current. A
   failed playlist or book fetch keeps that part of the old catalogue.
3. **Plan** with `syncPlanOf`: selected albums' tracks, then tracks only selected playlists need,
   then selected books. Books go last so new music isn't stuck behind a large book.
4. **Download** whatever is missing or changed. Each file goes to `.part`, then is renamed. A
   file that is on disk but has no record is re-linked without downloading
   (`SyncEngine.needsDownload`). Downloads use `Audio/{itemId}/stream?static=true`, which fetches
   the original file with no transcoding. Each album's `folder.jpg` downloads alongside its tracks.
5. **Clean up.** Every file in the sync folder outside `filesToKeep` (`sync/SyncPaths.kt`) is
   deleted. Deselecting an album, playlist or book therefore removes its files on the next sync.
6. **Artist photos, then covers.** `ArtistPhotoSync` runs, then `CoverSync` fetches playlist and
   book covers. A failed photo or cover is logged and retried next sync; it never fails the sync.

**Failures.** A failed item is skipped, not fatal. The sync ends **incomplete** ("Sync
incomplete: 2 items couldn't sync. They'll retry next sync."), and cleanup never deletes a file
a failed item might own.

**On disk:**

| What | Where |
|---|---|
| Music | `<syncDir>/Music/<album artist>/<album>/<track>`, with `folder.jpg` |
| Books | `<syncDir>/Audiobooks/<author>/<title>/`, the `.m4b` plus `folder.jpg` |

The default `syncDir` is public `Media/hz/<server name>`. If that isn't writable (no all-files
access) it falls back to `Media/hz/<server name>` in the app's own external files folder. The
user can pick another in Settings → Sync.

**Sync card.** `ui/Settings/SyncDisplay.kt` is a pure, ordered rule table that turns the state
into a status: OFFLINE, SYNCING, STOPPED, FAILED, INCOMPLETE, SYNCED or NOT_SYNCED. The counts
come from `sync/SyncCounts.kt`, which applies the same selection rules as `syncPlanOf`, so
choosing a new playlist shows that a sync is needed. `SyncRowSummary.kt` builds the one-line
summary on the Settings row.

## Playback

```
Fragments ──▶ PlaybackViewModel (activity-scoped, wraps one MediaController)
                     │  MainActivity builds the controller in onStart and releases it in onStop
                     ▼
              MediaSession ──▶ PlaybackService (MediaSessionService, foreground "mediaPlayback")
                                   ├─ TrackResolver   IDs → MediaItems, via the repositories
                                   ├─ ResumeStore      saves the queue, restores it paused
                                   ├─ PlaybackFocus    audio focus, ducking, becoming-noisy
                                   ├─ BookProgressWriter → book_progress
                                   ├─ EqualiserStore listener → engine.setEqualiser
                                   └─ BassPlayer (Media3 SimpleBasePlayer: queue, repeat, shuffle)
                                          └─ BassEngine
                                               BASS → BASSmix queued mixer (48 kHz, float)
                                                       ├─ music: one decoder per track, next one queued
                                                       ├─ book: BASS_FX tempo stream round the decoder
                                                       └─ BASS_FX PEAKEQ on the mixer (10 bands)
```

- **Resolving.** Queue items carry only a Jellyfin ID as `mediaId`. `TrackResolver` resolves a
  batch at a time, kept under SQLite's 999-parameter limit, through
  `LibraryRepository.playableTracks` and `BookRepository.playableBooks`. It adds the file URI,
  metadata and extras: codec, bit depth, sample rate, bitrate, size, and a book's chapters
  (`playback/TrackExtras.kt`). An ID with no catalogue row, no sync record or no file is skipped.
  If nothing resolves, the queue stays as it was and the UI shows "Files missing. Run a sync."
- **Gapless.** The next track is queued in the mixer (`BASS_MIXER_QUEUE`), so it starts with no
  gap. Seeking moves the position in place without reopening the file, so book skips are quick.
- **Order.** `QueueOrder` (pure) holds the repeat and shuffle rules. Shuffle starts with the
  current track. Repeat cycles off → all → one. Previous restarts the track after 3 s.
- **Resume.** The queue is saved on play/pause, track change, seek, repeat and shuffle, and every
  10 s while playing. A service starting with an empty queue restores it paused.
  `onPlaybackResumption` gives Bluetooth or lock-screen Play the same queue.
- **Lifecycle.** Swiped away while playing, the app keeps playing; while paused, the service
  stops. Logout stops playback and clears the queue.
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

**Activities.** `PermissionsActivity` is the launcher and goes on to `LoginActivity` or
`MainActivity`. `MainActivity` hosts every other screen through one `NavHostFragment`, above
`miniPlayerContainer`. There's no tab bar: Home's header has a round Settings button, which also
shows a sync progress ring.

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
    ├── appearanceFragment
    ├── homeScreenFragment
    └── sync_graph (start: syncSettingsFragment)
        ├── syncSettingsFragment
        ├── albumSelectionFragment, playlistSelectionFragment, bookSelectionFragment
        └── autoSyncFragment
```

The sync notification deep-links to `syncSettingsFragment`, with Home → Settings → Sync behind it.

**View models.**
- `MainViewModel` is activity-scoped. It holds server and sync state, sync counts and logout.
- `PlaybackViewModel` is activity-scoped and mirrors the media controller.
- `SettingsViewModel` is scoped to `settings_graph` with `navGraphViewModels`.
- Each browse screen has its own view model reading the repositories' Room `Flow`s, so screens
  update when a sync finishes.

**Home.**
- `home/HomeCategory.kt` is the enum of categories, in default order.
- `home/HomeLayout.kt` parses the saved order and hidden set. It drops unknown keys, appends
  missing categories and never hides the last one.
- `ui/Home/HomeCategoryViews.kt` is one `when` table that gives each category its title, icon,
  navigation action and count.
- `HomeLibraryState` covers Building, Failed and Ready (with counts). With an empty catalogue,
  Home refreshes it without a sync.

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
  **Before the alpha's first sync, set a different sync directory.** Both apps default to the
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

- Add the SQL to `db/CatalogueDao.kt`. Expose it from the right repository as a `Flow`, applying
  the visibility rule (`library/AlbumSelectionRule.kt`, `library/LibraryGrouping.kt`). Never call
  the DAO from a screen.
- Pure ordering and grouping go in `library/` with JUnit tests. Join queries get a Robolectric
  test, like `LibraryRepositoryTest`.
- Start playback through `PlaybackViewModel.playTracks(itemIds, startIndex, shuffle)` with
  Jellyfin IDs.

### Add a server request

- Add a GET to `api/JellyfinApi.kt` that takes the `Authorization` header as a parameter, and
  call it from `JellyfinRepository`. Prefer `GET Items` with `userId`.
- `ReadOnlyInterceptor` refuses anything else. A non-GET needs the user's decision and an
  explicit exception.
- If the data belongs in the catalogue, fetch it during sync and the catalogue refresh, and add it
  to the `replaceCatalogue`/`clearCatalogue` transaction. Bump the database version and decide
  about a migration (see [Data](#data)).

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
- **Device ID.** It's hard-coded as `hz-android-001`, so every install shares one device
  identity on the server.
- **HTTP logging** is always on at BASIC level (`JellyfinRepository`, `debug = true`).
- **Cancellation.** `JellyfinRepository.isServerHealthy()` and `safeCall` catch
  `CancellationException`, and `MainViewModel.checkServerConnection()` works around it with
  `ensureActive()`. The fix is to rethrow it there and remove the workaround.
- **Downloader.** The file downloader builds its own OkHttp client without
  `ReadOnlyInterceptor`. Its only request is a hard-coded GET, so it's safe, but routing it
  through the interceptor would add a backstop.
- **Artwork.** `SyncEngine` swallows album-art download failures.
- **Strings.** `fragment_album_selection.xml` hard-codes its English text.
- **Not seen yet.** Home may open twice on the first Android 12+ launch after an upgrade. If it
  happens, the fix is `CLEAR_TOP|SINGLE_TOP` in `PermissionsActivity.proceed()`.
- **README is stale** in places:
  - "No in-app playback" is no longer true;
  - the API table lists `/Audio/{id}/file`;
  - the architecture tree and the screenshots predate the overhaul.

## What's next

- **The player and adapter split** (spec `2026-10-07-player-adapter-split-design.md`). T1 is
  done and T2 is being built. Then T3, the player reading the Library folder; T4, Settings,
  launch and sign-out; and the optional T5, Gradle modules. Search builds after it.
- **Sub-project 4, Search.** Not designed yet. It will search albums, artists, songs, playlists
  and audiobooks. Points to settle:
  - Search reads the catalogue through the repositories and applies the visibility rule.
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
  files, lyrics, queue editing, colours from artwork, EQ settings per output, a manual preamp,
  and a smaller playback buffer if slider lag matters.
