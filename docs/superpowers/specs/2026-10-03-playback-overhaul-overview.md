# Finsync playback overhaul: overview

Date: 2026-10-03
Status: Sub-project 1 designed. Sub-projects 2–5 not started.

## Goal

Turn Finsync from a sync tool into a music player for the files it syncs. The main interface
becomes a library browser and player. Everything the app does today (sync, downloads, sync
settings, server) moves into Settings.

## Decisions that apply to every sub-project

### Playback source: downloaded files only

Finsync plays only tracks that sync has downloaded to the device. Browse screens show only
downloaded items, and playback always works offline.

Streaming from the server is a likely later addition. To keep that cheap, every sub-project
follows four rules:

1. **Store the whole library's metadata, with a downloaded flag on each track.** Sync already
   fetches metadata for the entire library on every run (`repo.getAllAudioItems` in
   `SyncEngine.syncLibrary`), then discards everything outside the selected albums. Keeping it
   costs no extra requests. Every record is keyed by its Jellyfin ID, as tracks and albums
   already are, so a downloaded item and its server copy are the same record. Browse screens
   filter to downloaded items; streaming would lift that filter while online.
2. **Screens get library data from one repository**, never directly from Room DAOs.
3. **The play queue holds Jellyfin item IDs, not file paths.** A small resolver turns an ID into
   something playable just before it plays. Today that is always a local file; later it could be
   a stream.
4. **Playback uses Media3 (ExoPlayer and MediaSessionService).** It is needed anyway for
   background playback, the media notification, and lock screen and Bluetooth controls, and it
   plays HTTP streams as well as files.

### Navigation

- One activity (`MainActivity`) hosts every screen after login as fragments, managed by Jetpack
  Navigation. The permissions and login screens stay as separate activities.
- No tab bar. Home's header has round Search and Settings buttons. Search appears in
  sub-project 4.
- A mini-player sits at the bottom of every screen while something is playing. Tapping it opens
  the full Player screen.

### Settings layout

Settings is a list of cards: server row, Sync card, Downloads, Albums to Sync, Auto-sync and Sync
Directory. The Sync card shows status, progress and the Sync button, and opens today's sync screen
(the blob) as the Sync Status screen.

## Sub-projects

Each sub-project gets its own spec, plan and build, and leaves the app working.

| # | Sub-project | Scope |
|---|---|---|
| 1 | App shell and Settings | Single-activity shell, Home (empty for now), Settings taking over sync, downloads and server. No playback. Spec: [2026-10-03-app-shell-settings-design.md](2026-10-03-app-shell-settings-design.md) |
| 2 | Playback and Player | Media3 playback service, notification and lock screen controls, mini-player, Player screen. Albums becomes Home's first category, with the Albums list and album detail. Introduces the library repository and the catalogue tables for albums and tracks. |
| 3 | Library browsing | The rest of Home's categories: Artists and an artist's albums; Album Artists; Genres; Songs; Playlists; Audio Books. Extends the catalogue (artists, genres) and sync (artist images, playlists, audiobooks). |
| 4 | Search | One search across albums, artists, songs, playlists and audiobooks. |
| 5 | Equaliser | Presets and band sliders on the playback service's audio output. |

## Open questions for later sub-projects

- Whether browse sub-screens repeat Home's Search button. Decide in sub-project 2 or 3.
- How playlists and audiobooks are chosen for sync. Decide in sub-project 3.
- `ReadOnlyInterceptor` blocks every request except reads and the login. Reporting plays to the
  server (play counts, "now playing", audiobook positions) would need a deliberate exception.
  Not planned; decide if it comes up.

## If streaming is added later

Built on the four rules above, streaming would mostly be new code below the screens:

| Piece | What it involves | Size |
|---|---|---|
| Network playback | The player sends the `Authorization` header (Jellyfin 12 disables tokens in URLs by default); buffering state; recovery when the connection drops mid-track | Small |
| Online and offline browsing | Whole library when connected, downloads only when not; mark downloaded items | Small to medium |
| Streaming quality | A Wi-Fi vs mobile data setting with server-side transcoding. Today's requests fetch the original file (`static=true`) | Medium |
| Library refresh | Refresh metadata when the app opens, not only during sync | Small |
| Artwork | Covers for non-downloaded albums, loaded from the server | Small |

The transcoding and play-reporting details are from memory of the Jellyfin API and have not been
checked against Jellyfin 12.
