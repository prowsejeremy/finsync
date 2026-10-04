# Sub-project 1: app shell and Settings

Date: 2026-10-03
Status: Design approved. Ready for an implementation plan.
Overview: [Finsync playback overhaul](2026-10-03-playback-overhaul-overview.md)

## Goal

Move every screen after login into a single-activity shell, replace the bottom tab bar with
header buttons on Home, and move sync, downloads and server controls into Settings. Sync behaves
as it does today, apart from the approved offline auto-stop change under Behaviour.

## Out of scope

Playback, the mini-player itself, library browsing, Search, the Equaliser, catalogue changes to
the database and the library repository. These belong to sub-projects 2–5.

## Screens

### Home (new)

- The start screen after login. Back on Home leaves the app.
- Header: the title "Home" on the left, and a round Settings button on the right styled like
  today's settings cog.
- While a sync runs, a progress ring surrounds the Settings button. It fills as tracks download,
  and spins (indeterminate) while the library is still being fetched and there is no total yet.
- No Search button until sub-project 4.
- Body: library categories appear as sub-projects 2 and 3 add them. Until then it shows an empty
  state: "Nothing here yet" with "Sync and downloads are in Settings." underneath.

### Settings

Header "← Settings", then these cards from top to bottom:

1. **Server row (new).** Pill-shaped like today's server pill: Finsync logo, server name, a status
   dot (green when connected, muted when not) and "Connected" or "Offline". Opens today's
   `ServerBottomSheet` (URL, status, Log out).
2. **Sync card (new).** See below. Tapping the card opens Sync Status. Tapping its button starts
   or stops a sync without leaving Settings.
3. **Downloads (new card).** "N albums on this device", where N is the number of albums the
   Downloads list shows, or "No albums on this device yet" when it shows none. The hint reads
   "Tap to manage downloads". Opens Downloads.
4. **Albums to Sync**, 5. **Auto-sync** and 6. **Sync Directory**, as today.

### Sync card

- Title row: "Sync" on the left, and on the right the status label from the rules table
  (green when synced, muted otherwise, as today).
- Detail line: "{count} of {total} tracks synced" when there is a total. While offline: "Please
  reconnect to sync." Hidden otherwise.
- Progress bar: shown only while a sync is running. Indeterminate while the total isn't known
  yet.
- Button: "Sync Now" or "Stop Sync", outlined like today's Sync button. Disabled while offline.
- All values come from the shared sync display function (see Code structure).

### Sync Status screen (moved from MainActivity)

- Header "← Sync Status". Today's in-page "Sync Status" heading becomes the header title.
- Content as today: blob, status label, track count, total line, Sync button and the error
  snackbar. The offline state is also as today: the label "Lost connection to server", the count
  "OFFLINE" and the line "Please reconnect to sync."
- No server pill or settings cog.

### Downloads (moved from LibraryActivity)

- Header "← Downloads". The list, empty state and album sheet (Re-sync, Remove, Cancel) work as
  today.
- Back closes the album sheet first, then leaves Downloads.

### Albums to Sync and Auto-sync

- Content unchanged, still under a "← Settings" header.
- Auto-sync copy fix: "You can also trigger a manual sync from the home screen at any time."
  becomes "You can also sync manually at any time from the Sync card in Settings."

### Sync Directory

Opens the system folder picker, as today.

### Server sheet

Unchanged. Log out returns to Login.

### Mini-player slot

A hidden container at the bottom of MainActivity's layout. Sub-project 2 fills it.

## Navigation

- `res/navigation/nav_graph.xml`, with `homeFragment` as the start destination.
- A nested graph, `settings_graph`, with `settingsFragment` as its start destination. It also
  contains `syncStatusFragment`, `downloadsFragment`, `albumSelectionFragment` and
  `autoSyncFragment`.
- The server sheet and the album sheet are not destinations. They open the way they do today.
- **Notification deep link.** `SyncService`'s notification content intent becomes a
  `NavDeepLinkBuilder` pending intent to `syncStatusFragment`. Set the component to
  `MainActivity` explicitly, because the launcher activity is `PermissionsActivity`. The
  synthetic back stack is Home → Settings → Sync Status. The notification's Stop action is
  unchanged.

## Code structure

### MainActivity

- Layout: a `FragmentContainerView` hosting the `NavHostFragment` (the default nav host), above
  the hidden mini-player container.
- Keeps: the logged-in check, the config observer that opens Login when the config is cleared,
  the network callback (registered in `onResume`, unregistered in `onPause`), `refreshConfig()`
  on resume, and the initial server check.
- Loses: the header, the sync rendering and the Sync button, which move into fragments.

### Screens

| Screen | Class | Built from |
|---|---|---|
| Home | `HomeFragment`, new, in `ui/Home` | Nothing; it's new |
| Settings | `SettingsFragment`, in `ui/Settings` | `SettingsListFragment`, plus `SettingsActivity`'s header, folder picker and `uriToPath()`, plus the three new cards |
| Sync Status | `SyncStatusFragment`, in `ui/Settings` | `MainActivity`'s sync content and `render()` |
| Downloads | `DownloadsFragment`, in `ui/Library` | `LibraryActivity`. `AlbumDetailFragment` stays a child overlay and calls its parent fragment instead of `LibraryActivity` |
| Albums to Sync | `AlbumSelectionFragment` | Unchanged apart from navigation and view model scope |
| Auto-sync | `AutoSyncFragment` | Unchanged apart from navigation, view model scope and the copy fix |
| Server sheet | `ServerBottomSheet` | Unchanged; shown from the server row |

New files use the `com.jpd.finsync.ui` package, as existing files in these folders do. Every
screen except Home includes one shared back-arrow header layout.

### View models

- `MainViewModel` is activity-scoped (`activityViewModels()`) and shared by Home, Settings, Sync
  Status and the server sheet.
- `SettingsViewModel` is scoped to `settings_graph` (`navGraphViewModels()`). It is created when
  you enter Settings and cleared when you leave, the same lifetime as today's `SettingsActivity`.

### Shared sync display function

`SyncDisplay.from(state: MainViewModel.UiState)` in `ui/Settings/SyncDisplay.kt`. It is pure
Kotlin with no Android calls, so it runs in JVM unit tests. Both the Sync card and the Sync
Status screen use it. It returns:

- `status`: `OFFLINE`, `SYNCING`, `STOPPED`, `FAILED`, `SYNCED` or `NOT_SYNCED`
- `trackCount`: `Int?`. Null renders as "—".
- `totalTracks`: `Int`, always `UiState.trackStats.second`. Zero means no total line.
- `progress`: `Float?` from 0 to 1. Null means a sync is running but its total isn't known yet.
- `errorMessage`: `String?`, passed through from the sync state.

The rules are taken from today's `MainActivity.render()` and checked in order. "Synced" and
"total" are `UiState.trackStats.first` and `.second`. `isRunning`, `wasStopped`,
`errorMessage`, `syncComplete`, `downloadedItems` and `totalItems` are `SyncState` fields.

| Order | Condition | status | Label | trackCount | progress |
|---|---|---|---|---|---|
| 1 | Server not connected | OFFLINE | Lost connection to server | not used | not used |
| 2 | `isRunning` | SYNCING | Syncing… | `downloadedItems` | `downloadedItems / totalItems`; null while `totalItems` is 0 |
| 3 | `wasStopped` | STOPPED | Sync stopped | `downloadedItems` | 0 |
| 4 | `errorMessage` set | FAILED | Sync failed | synced if total > 0, else null | 0 |
| 5 | `syncComplete`, or synced ≥ total > 0 | SYNCED | Synced | synced if total > 0, else null | 1 |
| 6 | Otherwise | NOT_SYNCED | Not synced yet | synced if total > 0, else null | 0 |

- The button reads "Stop Sync" when the status is SYNCING and "Sync Now" otherwise. It is
  disabled when the status is OFFLINE.
- A missing sync state counts as the default idle `SyncState()`. That also stops the layout's
  default "Syncing..." label flashing at startup before the first state arrives.
- Status labels move from hard-coded strings into `strings.xml`, reusing the existing
  `status_*` and `btn_*` strings where they match.

## Behaviour

- **State flow.** `SyncEngine.syncState` feeds `MainViewModel.uiState`, which every screen that
  shows sync or server state reads. No screen keeps its own copy.
- **Offline auto-stop (approved change).** Today `MainActivity.render()` calls `stopSync()`
  whenever the server is unreachable and the main screen is visible, even when no sync is
  running. This moves into `MainViewModel.checkServerConnection()`: when the check fails and
  `SyncEngine.syncState.value.isRunning` is true, it calls `stopSync()`. It now works on every
  screen and no longer marks an idle state as stopped.
- **Downloads card count** refreshes whenever Settings resumes, so it reflects albums removed in
  Downloads.
- **Back.** Navigation handles back between screens. `DownloadsFragment` keeps a back callback that
  closes the album sheet first. The custom back callbacks in `SettingsActivity` and
  `LibraryActivity` go away with those activities.

## Errors and edge cases

| Case | Result |
|---|---|
| Server unreachable | The server row's dot turns muted and the row says "Offline". The Sync card and Sync Status show the OFFLINE state with the button disabled. A running sync stops. |
| Sync fails | The Sync card shows "Sync failed". Sync Status shows today's error snackbar. |
| Not logged in, or config cleared | `MainActivity` opens Login, as today. This includes arriving from the notification. |
| Rotation or process death | Navigation restores the back stack, including Settings sub-screens. |
| Folder picker cancelled | Nothing changes, as today. |

## Testing

- The repo has no tests today: `app/src` contains only `main`. JUnit 4.13.2 is already a
  `testImplementation` dependency, so no new test dependency is needed.
- **Unit tests first.** Write `app/src/test/java/com/jpd/finsync/ui/SyncDisplayTest.kt` before
  moving `render()`. Include one test per row of the rules table, plus these cases:
  - no sync state yet
  - running before the total is known
  - offline while a sync is running (OFFLINE wins)
  - `syncComplete` with no total

  Run them with `./gradlew :app:testDebugUnitTest`.
- **Build:** `./gradlew :app:assembleDebug`.
- **On-device checklist**, run by the user:
  1. After login, the app lands on Home with the empty state.
  2. Home → Settings → each card, then back from every screen to Home. Back on Home leaves the
     app.
  3. Start a sync from the Sync card. The card, Sync Status and Home's ring all update.
  4. Stop a sync from Sync Status.
  5. Tap the sync notification mid-sync. It lands on Sync Status, and back goes to Settings, then
     Home.
  6. Turn on airplane mode mid-sync. The sync stops, the server row shows Offline and the Sync
     button is disabled.
  7. Turn on airplane mode while idle, then reconnect. The status does not change to "Sync
     stopped".
  8. In Downloads, open an album sheet and press back to close it. Remove an album; the list and
     the Downloads card count both update.
  9. Albums to Sync and Auto-sync save as they do today.
  10. Pick a sync directory; cancelling the picker leaves the path unchanged.
  11. Log out from the server sheet; the app returns to Login.
  12. Rotate on a Settings sub-screen. With "Don't keep activities" turned on, leave the app and
      return.

## Needs the user's approval during implementation

- **Adding `androidx.navigation:navigation-fragment-ktx`.** The plan confirms the newest version
  that works with Kotlin 1.9.22, AGP 8.5.2 and compileSdk 34. Adding it also raises the
  `androidx.fragment` version the app gets indirectly.
- **Deleting files that become unused.** The plan confirms each is unused before deleting:
  - `ui/Library/LibraryActivity.kt`, `ui/Settings/SettingsActivity.kt`, `ui/NavBarFragment.kt`,
    and `ui/Settings/SettingsListFragment.kt` (replaced by `SettingsFragment`)
  - `res/layout/activity_library.xml`, `activity_settings.xml`, `fragment_nav_bar.xml` and
    `fragment_settings_list.xml`
  - `res/drawable/bg_nav_active.xml`, `ic_home.xml` and `ic_download.xml`, which only the tab bar
    uses
  - the strings `title_library` and `title_settings`, which only the removed manifest entries use

## Noticed, not in scope

- `res/menu/main_menu.xml` isn't referenced anywhere.
- `MainViewModel` and `SettingsViewModel` both load the album list and both read and write the
  album selection and sync directory.
- The Sync Status error snackbar reappears on every state change while an error persists.
- From reading `SyncEngine`, not verified on device: if every download fails, for example
  because the server drops mid-sync, the run still ends with `syncComplete = true` and shows
  "Synced". Stopping the sync when the connection drops, now on every screen, makes this less
  likely.
