# Sub-project 1: App Shell and Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move every screen after login into one Jetpack Navigation host, add a Home screen, and move sync, downloads and server controls into Settings. Sync keeps working as it does today, apart from the approved offline auto-stop change.

**Architecture:** `MainActivity` becomes the host for a `NavHostFragment`. Each screen becomes a fragment in `res/navigation/nav_graph.xml`. Home is the start destination, and Settings and its sub-screens form a nested `settings_graph`. A pure `SyncDisplay` mapping replaces the rules inside `MainActivity.render()`, so the Sync card and the Sync Status screen always agree. It is unit tested on the JVM. Tasks 5–8 build the new fragments alongside the old activities, Task 9 switches the app over, and Task 10 deletes what's left.

**Tech stack:** Kotlin 1.9.22, AGP 8.5.2, compileSdk 34, ViewBinding, AndroidX Navigation 2.7.7 (`navigation-fragment-ktx`), Material Components 1.11.0, Room 2.6.1, JUnit 4.13.2.

**Spec:** [2026-10-03-app-shell-settings-design.md](../specs/2026-10-03-app-shell-settings-design.md)

---

## Ground rules for whoever runs this plan

- **No git writes.** Don't run `git add`, `commit`, `branch`, `stash`, `checkout -b`, worktree commands or any other git write. The user manages git. Each task ends with a checkpoint: stop, report what changed, and offer the suggested commit message. Reading state (`git status`, `git diff`) is fine.
- **Two steps need the user's approval first:** adding the Navigation dependency (Task 4) and deleting files (Task 10). Ask, then wait for a yes. Anything else on the user's ask-first list also needs asking first: installing or upgrading dependencies, deleting files, CI config, hand-editing lockfiles, Docker and `sudo`.
- **Tests:** never change a test to make it pass. If a test and the spec disagree, stop and report.
- **Report failures honestly**, with the command output.
- **Style:** match the surrounding code.
  - Every file under `app/src/main/java/com/jpd/finsync/ui/` uses `package com.jpd.finsync.ui`, whatever its folder.
  - Fragments use the `_binding` / `binding` ViewBinding pattern.
  - Imports are a plain list without group comments, as in the existing files. The user's rules say to match the project's existing style first.
  - Use four-space indents, keep lines to about 100 characters, and write comments that explain why.
  - No formatter is configured.
- **If you dispatch subagents,** each brief must include the user's global permission and style rules from `~/.claude/CLAUDE.md` in full. The user requires this.
- **Commands** run from the repo root with JDK 17. On 2026-10-03, `./gradlew :app:assembleDebug` succeeded and there were no unit tests.
- **On-device checks are done by the user.** Build the APK, then ask the user to install it (`./gradlew :app:installDebug` with a device connected) and report back.

## Notes beyond the spec

These fill gaps the spec left to planning:

- **Navigation 2.7.7.** This is the newest version I'm confident builds with Kotlin 1.9.22 and compileSdk 34. Versions 2.8 and later add type-safe routes built on Kotlin serialization, which this project doesn't need. I haven't confirmed that they build with Kotlin 1.9.22.
- **`MainViewModel.toggleSync()`** holds the start-or-stop logic that both the Sync card and the Sync Status screen need.
- **`visibleDownloadedAlbums()`** is a small pure function shared by the Downloads list and the Downloads card count, so the two can't disagree. It has its own unit tests.
- **`navigateSafely()`** prevents a crash when a card is double-tapped. Without it, the second tap tries to run the action from the new screen, where that action doesn't exist.
- **Settings refreshes the track total when it resumes.** Today the main screen's total refreshes when you return to it from Settings, because `MainActivity.onResume()` reloads it. With one activity that no longer happens. Instead, `SettingsFragment.onResume()` calls `MainViewModel.loadAlbums()`, so the Sync card's total stays correct after you change Albums to Sync.

## File map

**Create**

| File | Responsibility |
|---|---|
| `app/src/main/java/com/jpd/finsync/ui/Settings/SyncDisplay.kt` | Pure mapping from `MainViewModel.UiState` to what the sync UI shows |
| `app/src/main/java/com/jpd/finsync/ui/Settings/SyncDisplayViews.kt` | Android helpers for `SyncDisplay`: label and colour resources, progress indicators |
| `app/src/main/java/com/jpd/finsync/ui/Settings/SyncStatusFragment.kt` | Sync Status screen, moved from `MainActivity` |
| `app/src/main/java/com/jpd/finsync/ui/Settings/SettingsFragment.kt` | Settings screen |
| `app/src/main/java/com/jpd/finsync/ui/Library/DownloadedAlbums.kt` | Which downloaded albums the Downloads list shows |
| `app/src/main/java/com/jpd/finsync/ui/Library/DownloadsFragment.kt` | Downloads screen, moved from `LibraryActivity` |
| `app/src/main/java/com/jpd/finsync/ui/Home/HomeFragment.kt` | Home screen |
| `app/src/main/java/com/jpd/finsync/ui/NavigationExtensions.kt` | `navigateSafely()` |
| `app/src/main/res/navigation/nav_graph.xml` | Navigation graph |
| `app/src/main/res/layout/view_screen_header.xml` | Shared back-arrow header |
| `app/src/main/res/layout/fragment_sync_status.xml` | Sync Status layout |
| `app/src/main/res/layout/fragment_downloads.xml` | Downloads layout |
| `app/src/main/res/layout/fragment_home.xml` | Home layout |
| `app/src/main/res/layout/fragment_settings.xml` | Settings layout |
| `app/src/test/java/com/jpd/finsync/ui/SyncDisplayTest.kt` | Unit tests for `SyncDisplay` |
| `app/src/test/java/com/jpd/finsync/ui/DownloadedAlbumsTest.kt` | Unit tests for `visibleDownloadedAlbums()` |

**Modify:** `app/build.gradle`, `MainViewModel.kt`, `MainActivity.kt`, `activity_main.xml`, `SettingsViewModel.kt`, `AlbumSelectionFragment.kt`, `fragment_album_selection.xml`, `AutoSyncFragment.kt`, `fragment_auto_sync.xml`, `AlbumDetailFragment.kt`, `SyncService.kt`, `strings.xml`, `AndroidManifest.xml`, `README.md`.

**Delete (Task 10, with approval):** `LibraryActivity.kt`, `SettingsActivity.kt`, `SettingsListFragment.kt`, `NavBarFragment.kt`, `activity_library.xml`, `activity_settings.xml`, `fragment_settings_list.xml`, `fragment_nav_bar.xml`, `bg_nav_active.xml`, `ic_home.xml`, `ic_download.xml`.

---

### Task 0: Confirm the baseline

- [ ] **Step 1: Build**

Run: `./gradlew :app:assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Confirm there are no unit tests yet**

Run: `./gradlew :app:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`, with `> Task :app:testDebugUnitTest NO-SOURCE` in the output.

If either step fails, stop and report. Don't start Task 1 on a broken baseline.

---

### Task 1: `SyncDisplay` mapping

**Files:**
- Create: `app/src/test/java/com/jpd/finsync/ui/SyncDisplayTest.kt`
- Create: `app/src/main/java/com/jpd/finsync/ui/Settings/SyncDisplay.kt`

These rules come from today's `MainActivity.render()` (`app/src/main/java/com/jpd/finsync/ui/Home/MainActivity.kt:96-164`) and the spec's rules table. One change is deliberate: a missing sync state counts as idle.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.jpd.finsync.ui

import com.jpd.finsync.model.SyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SyncDisplayTest {

    private fun displayFor(
        syncState: SyncState?,
        syncedTracks: Int = 0,
        totalTracks: Int = 0,
        serverConnected: Boolean = true
    ): SyncDisplay = SyncDisplay.from(
        MainViewModel.UiState(
            syncState = syncState,
            trackStats = Pair(syncedTracks, totalTracks),
            serverConnected = serverConnected
        )
    )

    @Test
    fun `offline wins over a running sync and shows no error`() {
        val display = displayFor(
            SyncState(isRunning = true, totalItems = 10, downloadedItems = 4, errorMessage = "boom"),
            serverConnected = false
        )

        assertEquals(SyncDisplay.Status.OFFLINE, display.status)
        assertNull(display.errorMessage)
    }

    @Test
    fun `no sync state yet counts as idle`() {
        val display = displayFor(syncState = null)

        assertEquals(SyncDisplay.Status.NOT_SYNCED, display.status)
        assertNull(display.trackCount)
        assertProgress(0f, display.progress)
    }

    @Test
    fun `running before the total is known has no progress yet`() {
        val display = displayFor(SyncState(isRunning = true, totalItems = 0))

        assertEquals(SyncDisplay.Status.SYNCING, display.status)
        assertEquals(0, display.trackCount)
        assertNull(display.progress)
    }

    @Test
    fun `running sync shows items processed this run and their share of the run`() {
        val display = displayFor(
            SyncState(isRunning = true, totalItems = 200, downloadedItems = 50),
            syncedTracks = 30,
            totalTracks = 180
        )

        assertEquals(SyncDisplay.Status.SYNCING, display.status)
        assertEquals(50, display.trackCount)
        assertEquals(180, display.totalTracks)
        assertProgress(0.25f, display.progress)
    }

    @Test
    fun `stopped sync shows items processed before it stopped`() {
        val display = displayFor(
            SyncState(wasStopped = true, totalItems = 200, downloadedItems = 40),
            syncedTracks = 30,
            totalTracks = 180
        )

        assertEquals(SyncDisplay.Status.STOPPED, display.status)
        assertEquals(40, display.trackCount)
        assertProgress(0f, display.progress)
    }

    @Test
    fun `failed sync passes the error through and shows synced tracks`() {
        val display = displayFor(SyncState(errorMessage = "boom"), syncedTracks = 10, totalTracks = 100)

        assertEquals(SyncDisplay.Status.FAILED, display.status)
        assertEquals(10, display.trackCount)
        assertEquals("boom", display.errorMessage)
    }

    @Test
    fun `failed sync with no total shows no count`() {
        val display = displayFor(SyncState(errorMessage = "boom"))

        assertEquals(SyncDisplay.Status.FAILED, display.status)
        assertNull(display.trackCount)
    }

    @Test
    fun `completed sync is synced with full progress`() {
        val display = displayFor(SyncState(syncComplete = true), syncedTracks = 100, totalTracks = 100)

        assertEquals(SyncDisplay.Status.SYNCED, display.status)
        assertEquals(100, display.trackCount)
        assertProgress(1f, display.progress)
    }

    @Test
    fun `completed sync with no total is synced with no count`() {
        val display = displayFor(SyncState(syncComplete = true))

        assertEquals(SyncDisplay.Status.SYNCED, display.status)
        assertNull(display.trackCount)
    }

    @Test
    fun `idle with every selected track on the device is synced`() {
        val display = displayFor(SyncState(), syncedTracks = 100, totalTracks = 100)

        assertEquals(SyncDisplay.Status.SYNCED, display.status)
        assertProgress(1f, display.progress)
    }

    @Test
    fun `idle with some tracks missing is not synced`() {
        val display = displayFor(SyncState(), syncedTracks = 40, totalTracks = 100)

        assertEquals(SyncDisplay.Status.NOT_SYNCED, display.status)
        assertEquals(40, display.trackCount)
        assertProgress(0f, display.progress)
    }

    @Test
    fun `never synced with nothing selected shows no count`() {
        val display = displayFor(SyncState())

        assertEquals(SyncDisplay.Status.NOT_SYNCED, display.status)
        assertNull(display.trackCount)
        assertEquals(0, display.totalTracks)
    }

    private fun assertProgress(expected: Float, actual: Float?) {
        assertNotNull(actual)
        assertEquals(expected, actual!!, DELTA)
    }

    private companion object {
        const val DELTA = 0.0001f
    }
}
```

- [ ] **Step 2: Run the tests to check they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.jpd.finsync.ui.SyncDisplayTest" --console=plain`
Expected: FAIL. `:app:compileDebugUnitTestKotlin` reports `Unresolved reference: SyncDisplay`.

- [ ] **Step 3: Write `SyncDisplay`**

```kotlin
package com.jpd.finsync.ui

import com.jpd.finsync.model.SyncState

/**
 * What the Sync card and the Sync Status screen show for the current sync and server state.
 * It has no Android dependencies, so the rules can be unit tested on the JVM.
 */
data class SyncDisplay(
    val status: Status,
    /** Rendered as "—" when null. */
    val trackCount: Int?,
    /** The selected albums' track total. Zero means there's no total to show. */
    val totalTracks: Int,
    /** From 0 to 1, or null while a sync is running but its total isn't known yet. */
    val progress: Float?,
    val errorMessage: String?
) {
    enum class Status { OFFLINE, SYNCING, STOPPED, FAILED, SYNCED, NOT_SYNCED }

    companion object {

        fun from(state: MainViewModel.UiState): SyncDisplay {
            val (syncedTracks, totalTracks) = state.trackStats
            if (!state.serverConnected) {
                // Offline overrides every sync state, and the offline screen shows no error.
                return SyncDisplay(Status.OFFLINE, null, totalTracks, 0f, null)
            }

            // Null until SyncEngine's first emission; treat that as idle.
            val sync = state.syncState ?: SyncState()
            val idleCount = if (totalTracks > 0) syncedTracks else null

            fun display(status: Status, trackCount: Int?, progress: Float?) =
                SyncDisplay(status, trackCount, totalTracks, progress, sync.errorMessage)

            return when {
                sync.isRunning -> display(Status.SYNCING, sync.downloadedItems, runningProgress(sync))
                sync.wasStopped -> display(Status.STOPPED, sync.downloadedItems, 0f)
                sync.errorMessage != null -> display(Status.FAILED, idleCount, 0f)
                sync.syncComplete || (totalTracks > 0 && syncedTracks >= totalTracks) ->
                    display(Status.SYNCED, idleCount, 1f)
                else -> display(Status.NOT_SYNCED, idleCount, 0f)
            }
        }

        private fun runningProgress(sync: SyncState): Float? =
            if (sync.totalItems > 0) sync.downloadedItems.toFloat() / sync.totalItems else null
    }
}
```

- [ ] **Step 4: Run the tests to check they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.jpd.finsync.ui.SyncDisplayTest" --console=plain`
Expected: `BUILD SUCCESSFUL`. All 12 tests pass (see `app/build/reports/tests/testDebugUnitTest/index.html`).

- [ ] **Step 5: Checkpoint.** Stop. The user commits. Suggested message: `Add SyncDisplay mapping for sync status, with unit tests`

---

### Task 2: Shared downloaded-albums filter

**Files:**
- Create: `app/src/test/java/com/jpd/finsync/ui/DownloadedAlbumsTest.kt`
- Create: `app/src/main/java/com/jpd/finsync/ui/Library/DownloadedAlbums.kt`

The filter comes from `LibraryActivity.loadAlbums()` (`app/src/main/java/com/jpd/finsync/ui/Library/LibraryActivity.kt:70-75`).

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.jpd.finsync.ui

import com.jpd.finsync.db.SyncedAlbum
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadedAlbumsTest {

    private val first = SyncedAlbum(albumId = "a1", name = "First", albumArtist = null, childCount = 3)
    private val second = SyncedAlbum(albumId = "a2", name = "Second", albumArtist = null, childCount = 5)
    private val albums = listOf(first, second)

    @Test
    fun `empty selection shows every album`() {
        assertEquals(albums, visibleDownloadedAlbums(albums, emptySet()))
    }

    @Test
    fun `selection containing all shows every album`() {
        assertEquals(albums, visibleDownloadedAlbums(albums, setOf("all", "a1")))
    }

    @Test
    fun `specific selection shows only those albums`() {
        assertEquals(listOf(second), visibleDownloadedAlbums(albums, setOf("a2", "missing")))
    }
}
```

- [ ] **Step 2: Run the tests to check they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.jpd.finsync.ui.DownloadedAlbumsTest" --console=plain`
Expected: FAIL with `Unresolved reference: visibleDownloadedAlbums`.

- [ ] **Step 3: Write the function**

```kotlin
package com.jpd.finsync.ui

import com.jpd.finsync.db.SyncedAlbum

/**
 * The albums the Downloads list shows. An empty selection, or one containing "all", means every
 * album, matching how sync reads the "selected_albums" preference.
 */
fun visibleDownloadedAlbums(
    albums: List<SyncedAlbum>,
    selectedIds: Set<String>
): List<SyncedAlbum> {
    val showAll = selectedIds.isEmpty() || selectedIds.contains("all")
    return if (showAll) albums else albums.filter { selectedIds.contains(it.albumId) }
}
```

- [ ] **Step 4: Run all unit tests**

Run: `./gradlew :app:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`, 15 tests passing.

- [ ] **Step 5: Checkpoint.** Stop. The user commits. Suggested message: `Add shared filter for the albums the Downloads list shows`

---

### Task 3: Move the offline auto-stop into `MainViewModel`

**Files:**
- Modify: `app/src/main/java/com/jpd/finsync/ui/Home/MainViewModel.kt`
- Modify: `app/src/main/java/com/jpd/finsync/ui/Home/MainActivity.kt`

This is the spec's approved behaviour change. `MainViewModel` can't be unit tested without new test libraries, so device checks cover it.

- [ ] **Step 1: Stop a running sync when the server check fails**

In `MainViewModel.checkServerConnection()`, replace:

```kotlin
            val healthy = repo.isServerHealthy(cfg.serverUrl)
            _serverConnected.postValue(healthy)
```

with:

```kotlin
            val healthy = repo.isServerHealthy(cfg.serverUrl)
            _serverConnected.postValue(healthy)
            // Only a running sync needs stopping; stopping an idle one would mark it "stopped".
            if (!healthy && SyncEngine.syncState.value.isRunning) stopSync()
```

- [ ] **Step 2: Add `toggleSync()`**

In `MainViewModel`, directly after the closing brace of `fun stopSync()`, add:

```kotlin

    fun toggleSync() {
        if (SyncEngine.syncState.value.isRunning) stopSync() else startSync()
    }
```

- [ ] **Step 3: Use `toggleSync()` in `MainActivity`**

In `MainActivity.setupViews()`, replace:

```kotlin
        binding.btnSyncNow.setOnClickListener {
            if (viewModel.uiState.value?.syncState?.isRunning == true) {
                viewModel.stopSync()
            } else {
                viewModel.startSync()
            }
        }
```

with:

```kotlin
        binding.btnSyncNow.setOnClickListener { viewModel.toggleSync() }
```

- [ ] **Step 4: Remove the old stop from `render()`**

In `MainActivity.render()`, replace:

```kotlin
        if (!state.serverConnected) {
            viewModel.stopSync()
            binding.tvSyncState.text = "Lost connection to server"
```

with:

```kotlin
        if (!state.serverConnected) {
            binding.tvSyncState.text = "Lost connection to server"
```

- [ ] **Step 5: Build and test**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`, 15 tests passing.

- [ ] **Step 6: Ask the user to check on a device**

The app still uses the old screens here. Ask the user to install the APK and check two things:
1. Start a sync, then turn on airplane mode while the main screen is visible. The sync stops.
2. With no sync running, turn on airplane mode, then turn it off. The status doesn't change to "Sync stopped".

- [ ] **Step 7: Checkpoint.** Stop. The user commits. Suggested message: `Move the offline sync stop into MainViewModel and skip idle syncs`

---

### Task 4: Add Jetpack Navigation (needs approval)

**Files:**
- Modify: `app/build.gradle`

- [ ] **Step 1: Ask the user**

Ask: "Task 4 adds `androidx.navigation:navigation-fragment-ktx:2.7.7` to `app/build.gradle`. It also raises the `androidx.fragment` version the app gets indirectly. OK to add it?" Wait for a yes. If the answer is no, stop the plan here, because every later task depends on Navigation.

- [ ] **Step 2: Add the dependency**

In `app/build.gradle`, replace:

```groovy
    implementation 'androidx.swiperefreshlayout:swiperefreshlayout:1.1.0'
```

with:

```groovy
    implementation 'androidx.swiperefreshlayout:swiperefreshlayout:1.1.0'
    implementation 'androidx.navigation:navigation-fragment-ktx:2.7.7'
```

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL`. The first run downloads the library.

- [ ] **Step 4: Report the resolved fragment version**

Run: `./gradlew :app:dependencies --configuration debugRuntimeClasspath --console=plain | grep -E "androidx\.fragment:fragment(-ktx)?:" | sort -u`
Expected: lines such as `androidx.fragment:fragment:1.3.6 -> 1.6.2`. Tell the user the version shown after `->`.

- [ ] **Step 5: Checkpoint.** Stop. The user commits. Suggested message: `Add Jetpack Navigation (navigation-fragment-ktx 2.7.7)`

---

### Task 5: Sync Status screen

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/layout/view_screen_header.xml`
- Create: `app/src/main/res/layout/fragment_sync_status.xml`
- Create: `app/src/main/java/com/jpd/finsync/ui/Settings/SyncDisplayViews.kt`
- Create: `app/src/main/java/com/jpd/finsync/ui/Settings/SyncStatusFragment.kt`

Nothing shows this screen until Task 9.

- [ ] **Step 1: Add strings**

In `app/src/main/res/values/strings.xml`, replace `</resources>` with:

```xml

    <!-- Screen headers -->
    <string name="cd_back">Back</string>
    <string name="header_sync_status">Sync Status</string>

    <!-- Sync status -->
    <string name="status_offline">Lost connection to server</string>
    <string name="status_sync_stopped">Sync stopped</string>
    <string name="status_synced">Synced</string>
    <string name="sync_count_none">—</string>
    <string name="sync_count_offline">OFFLINE</string>
    <string name="sync_detail_offline">Please reconnect to sync.</string>
    <string name="sync_track_total">of %1$d tracks synced</string>
    <string name="sync_error">Sync error: %1$s</string>
</resources>
```

The existing `status_syncing`, `status_not_synced`, `status_sync_failed`, `btn_sync_now` and `btn_stop_sync` strings are reused.

- [ ] **Step 2: Create the shared header**

`app/src/main/res/layout/view_screen_header.xml` copies the header from today's `activity_library.xml` and `activity_settings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:paddingStart="4dp"
    android:paddingEnd="16dp"
    android:paddingTop="8dp"
    android:paddingBottom="8dp">

    <ImageButton
        android:id="@+id/btnBack"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:src="@drawable/ic_arrow_back"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:contentDescription="@string/cd_back"
        android:scaleType="center" />

    <TextView
        android:id="@+id/tvTitle"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:textColor="@color/text_primary"
        android:textSize="20sp"
        android:fontFamily="@font/hk_regular"
        android:paddingStart="4dp" />

</LinearLayout>
```

- [ ] **Step 3: Create the Sync Status layout**

`app/src/main/res/layout/fragment_sync_status.xml` holds today's `activity_main.xml` content without the header, the in-page "Sync Status" heading or the tab bar:

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/bg_primary">

    <include
        android:id="@+id/header"
        layout="@layout/view_screen_header"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

    <androidx.core.widget.NestedScrollView
        android:id="@+id/scrollContent"
        android:layout_width="0dp"
        android:layout_height="0dp"
        app:layout_constraintTop_toBottomOf="@id/header"
        app:layout_constraintBottom_toTopOf="@id/btnSyncNow"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:gravity="center_horizontal"
            android:paddingTop="50dp">

            <!-- Blob + text overlay -->
            <FrameLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content">

                <com.jpd.finsync.ui.SyncBlobView
                    android:id="@+id/syncBlobView"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent" />

                <!-- Text centered inside the blob -->
                <LinearLayout
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_gravity="center"
                    android:orientation="vertical"
                    android:gravity="center">

                    <TextView
                        android:id="@+id/tvSyncState"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/status_not_synced"
                        android:textColor="@color/muted"
                        android:textSize="14sp"
                        android:fontFamily="@font/hk_regular"
                        android:layout_marginBottom="2dp" />

                    <TextView
                        android:id="@+id/tvTrackCount"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/sync_count_none"
                        android:textColor="@color/text_primary"
                        android:textSize="60sp"
                        android:fontFamily="@font/hk_extrabold"
                        android:includeFontPadding="false" />

                    <TextView
                        android:id="@+id/tvTrackTotal"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text=""
                        android:textColor="@color/muted"
                        android:textSize="13sp"
                        android:fontFamily="@font/hk_regular" />

                </LinearLayout>

            </FrameLayout>

        </LinearLayout>
    </androidx.core.widget.NestedScrollView>

    <com.google.android.material.button.MaterialButton
        android:id="@+id/btnSyncNow"
        android:layout_width="0dp"
        android:layout_height="56dp"
        style="@style/Widget.Material3.Button.OutlinedButton"
        android:text="@string/btn_sync_now"
        android:textColor="@color/text_primary"
        android:textSize="16sp"
        android:fontFamily="@font/hk_regular"
        app:strokeColor="@color/surface_2"
        app:cornerRadius="28dp"
        android:layout_marginHorizontal="20dp"
        android:layout_marginBottom="30dp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

- [ ] **Step 4: Create the Android helpers for `SyncDisplay`**

`app/src/main/java/com/jpd/finsync/ui/Settings/SyncDisplayViews.kt`:

```kotlin
package com.jpd.finsync.ui

import android.view.View
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.google.android.material.progressindicator.BaseProgressIndicator
import com.jpd.finsync.R
import kotlin.math.roundToInt

// Android-side helpers, kept out of SyncDisplay so that class stays JVM-testable.

@get:StringRes
val SyncDisplay.Status.labelRes: Int
    get() = when (this) {
        SyncDisplay.Status.OFFLINE    -> R.string.status_offline
        SyncDisplay.Status.SYNCING    -> R.string.status_syncing
        SyncDisplay.Status.STOPPED    -> R.string.status_sync_stopped
        SyncDisplay.Status.FAILED     -> R.string.status_sync_failed
        SyncDisplay.Status.SYNCED     -> R.string.status_synced
        SyncDisplay.Status.NOT_SYNCED -> R.string.status_not_synced
    }

@get:ColorRes
val SyncDisplay.Status.labelColorRes: Int
    get() = if (this == SyncDisplay.Status.SYNCED) R.color.accent_green else R.color.muted

@get:StringRes
val SyncDisplay.buttonLabelRes: Int
    get() = if (status == SyncDisplay.Status.SYNCING) R.string.btn_stop_sync else R.string.btn_sync_now

/** Shows [progress] from 0 to 1, or an indeterminate animation when it's null. */
fun BaseProgressIndicator<*>.showSyncProgress(progress: Float?) {
    if (progress == null) {
        if (!isIndeterminate) {
            // Some Material versions refuse to switch to indeterminate while visible.
            visibility = View.INVISIBLE
            isIndeterminate = true
        }
    } else {
        // On an indeterminate indicator, this switches to determinate after the current cycle.
        setProgressCompat((progress * max).roundToInt(), true)
    }
    visibility = View.VISIBLE
}
```

- [ ] **Step 5: Create `SyncStatusFragment`**

`app/src/main/java/com/jpd/finsync/ui/Settings/SyncStatusFragment.kt` moves `MainActivity.render()` here, now driven by `SyncDisplay`:

```kotlin
package com.jpd.finsync.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.jpd.finsync.R
import com.jpd.finsync.databinding.FragmentSyncStatusBinding

private const val COUNT_TEXT_SIZE_SP = 60f
private const val OFFLINE_COUNT_TEXT_SIZE_SP = 50f

class SyncStatusFragment : Fragment() {

    private var _binding: FragmentSyncStatusBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSyncStatusBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.header.tvTitle.setText(R.string.header_sync_status)
        binding.header.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnSyncNow.setOnClickListener { viewModel.toggleSync() }
        viewModel.uiState.observe(viewLifecycleOwner) { render(SyncDisplay.from(it)) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun render(display: SyncDisplay) {
        binding.tvSyncState.setText(display.status.labelRes)
        binding.tvSyncState.setTextColor(color(display.status.labelColorRes))

        if (display.status == SyncDisplay.Status.OFFLINE) {
            binding.tvTrackCount.setText(R.string.sync_count_offline)
            binding.tvTrackCount.textSize = OFFLINE_COUNT_TEXT_SIZE_SP
            binding.tvTrackTotal.setText(R.string.sync_detail_offline)
            binding.btnSyncNow.isEnabled = false
            binding.btnSyncNow.setBackgroundColor(color(R.color.surface_2))
            binding.btnSyncNow.setTextColor(color(R.color.muted))
            return
        }

        binding.tvTrackCount.textSize = COUNT_TEXT_SIZE_SP
        binding.btnSyncNow.isEnabled = true
        binding.btnSyncNow.setBackgroundColor(color(android.R.color.transparent))
        binding.btnSyncNow.setTextColor(color(R.color.text_primary))

        binding.syncBlobView.isSyncing = display.status == SyncDisplay.Status.SYNCING
        binding.syncBlobView.progress = display.progress ?: 0f
        binding.tvTrackCount.text =
            display.trackCount?.toString() ?: getString(R.string.sync_count_none)
        binding.tvTrackTotal.text = if (display.totalTracks > 0) {
            getString(R.string.sync_track_total, display.totalTracks)
        } else {
            ""
        }
        binding.btnSyncNow.setText(display.buttonLabelRes)

        display.errorMessage?.let { error ->
            val message = getString(R.string.sync_error, error)
            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun color(@ColorRes colorRes: Int) = ContextCompat.getColor(requireContext(), colorRes)
}
```

- [ ] **Step 6: Build and test**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`, 15 tests passing.

- [ ] **Step 7: Checkpoint.** Stop. The user commits. Suggested message: `Add Sync Status screen fragment and shared header (not wired up yet)`

---

### Task 6: Downloads screen

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/layout/fragment_downloads.xml`
- Create: `app/src/main/java/com/jpd/finsync/ui/Library/DownloadsFragment.kt`

This moves `LibraryActivity` into a fragment. `AlbumDetailFragment` still talks to `LibraryActivity` until Task 9, and nothing shows this screen before then.

- [ ] **Step 1: Add the header string**

In `strings.xml`, add this line after `<string name="header_sync_status">Sync Status</string>`:

```xml
    <string name="header_downloads">Downloads</string>
```

- [ ] **Step 2: Create the layout**

`app/src/main/res/layout/fragment_downloads.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/bg_primary">

    <include
        android:id="@+id/header"
        layout="@layout/view_screen_header"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

    <!-- Album list -->
    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/recyclerView"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:clipToPadding="false"
        android:paddingBottom="8dp"
        app:layout_constraintTop_toBottomOf="@id/header"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

    <!-- Empty state -->
    <TextView
        android:id="@+id/tvEmpty"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="No albums synced yet"
        android:textColor="@color/muted"
        android:textSize="15sp"
        android:fontFamily="@font/hk_regular"
        android:visibility="gone"
        app:layout_constraintTop_toBottomOf="@id/header"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

    <!-- Album detail overlay (hidden until an album is tapped) -->
    <FrameLayout
        android:id="@+id/albumDetailContainer"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:visibility="gone"
        app:layout_constraintTop_toBottomOf="@id/header"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

- [ ] **Step 3: Create `DownloadsFragment`**

`app/src/main/java/com/jpd/finsync/ui/Library/DownloadsFragment.kt`. The adapter is copied unchanged from `LibraryActivity`. The artwork lookup is split into two small functions with the same behaviour.

```kotlin
package com.jpd.finsync.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.jpd.finsync.R
import com.jpd.finsync.databinding.FragmentDownloadsBinding
import com.jpd.finsync.databinding.ItemAlbumBinding
import com.jpd.finsync.db.SyncDao
import com.jpd.finsync.db.SyncDatabase
import com.jpd.finsync.db.SyncedAlbum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val FOLDER_ART_NAMES = listOf("folder.jpg", "folder.png")

class DownloadsFragment : Fragment() {

    private var _binding: FragmentDownloadsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: AlbumAdapter

    // Enabled only while the album sheet is open, so back closes the sheet before leaving.
    private val closeAlbumDetailCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = hideAlbumDetail()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDownloadsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val backDispatcher = requireActivity().onBackPressedDispatcher
        backDispatcher.addCallback(viewLifecycleOwner, closeAlbumDetailCallback)
        binding.header.tvTitle.setText(R.string.header_downloads)
        binding.header.btnBack.setOnClickListener { backDispatcher.onBackPressed() }

        adapter = AlbumAdapter { item -> showAlbumDetail(item) }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        loadAlbums()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun reloadAlbums() = loadAlbums()

    private fun loadAlbums() {
        val context = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                val dao = SyncDatabase.getInstance(context).syncDao()
                val selectedIds = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                    .getStringSet("selected_albums", emptySet()) ?: emptySet()
                visibleDownloadedAlbums(dao.getAllAlbums(), selectedIds)
                    .map { album -> toAlbumItem(dao, album) }
            }
            adapter.submit(items)
            binding.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private suspend fun toAlbumItem(dao: SyncDao, album: SyncedAlbum): AlbumItem {
        val synced = dao.getSyncedTrackCountForAlbum(album.albumId)
        val artworkPath = album.artworkPath ?: findFolderArtwork(dao, album.albumId)
        return AlbumItem(album, synced, artworkPath)
    }

    // The database doesn't always record an artwork path, so check the album folder as well.
    private suspend fun findFolderArtwork(dao: SyncDao, albumId: String): String? {
        val trackPath = dao.getTracksForAlbum(albumId).firstOrNull()?.localPath ?: return null
        val folder = File(trackPath).parentFile ?: return null
        return FOLDER_ART_NAMES.map { File(folder, it) }.firstOrNull { it.exists() }?.absolutePath
    }

    // ── Album detail overlay ──────────────────────────────────────────────────

    fun showAlbumDetail(item: AlbumItem) {
        binding.albumDetailContainer.visibility = View.VISIBLE
        closeAlbumDetailCallback.isEnabled = true
        childFragmentManager.beginTransaction()
            .replace(binding.albumDetailContainer.id, AlbumDetailFragment.newInstance(item.album))
            .commit()
    }

    fun hideAlbumDetail() {
        binding.albumDetailContainer.visibility = View.GONE
        closeAlbumDetailCallback.isEnabled = false
        childFragmentManager.findFragmentById(binding.albumDetailContainer.id)?.let { frag ->
            childFragmentManager.beginTransaction().remove(frag).commitAllowingStateLoss()
        }
    }

    // ── Data model ────────────────────────────────────────────────────────────

    data class AlbumItem(val album: SyncedAlbum, val syncedTracks: Int, val artworkPath: String?)

    // ── Adapter ───────────────────────────────────────────────────────────────

    inner class AlbumAdapter(
        private val onItemClick: (AlbumItem) -> Unit
    ) : RecyclerView.Adapter<AlbumAdapter.VH>() {

        private val items = mutableListOf<AlbumItem>()

        fun submit(newItems: List<AlbumItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemAlbumBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
        override fun getItemCount() = items.size

        inner class VH(private val b: ItemAlbumBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(item: AlbumItem) {
                val album = item.album
                b.tvAlbum.text   = album.name
                b.tvArtist.text  = album.albumArtist ?: "Unknown Artist"
                b.tvSyncStatus.text = "Synced: ${item.syncedTracks} / ${album.childCount} tracks"

                val artFile = item.artworkPath?.let { File(it) }
                if (artFile != null && artFile.exists()) {
                    Glide.with(b.ivAlbumArt).load(artFile).centerCrop().into(b.ivAlbumArt)
                } else {
                    Glide.with(b.ivAlbumArt).clear(b.ivAlbumArt)
                }

                b.root.setOnClickListener { onItemClick(item) }
            }
        }
    }
}
```

- [ ] **Step 4: Build and test**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`, 15 tests passing.

- [ ] **Step 5: Checkpoint.** Stop. The user commits. Suggested message: `Add Downloads screen fragment (not wired up yet)`

---

### Task 7: Navigation graph and Home

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/navigation/nav_graph.xml`
- Create: `app/src/main/java/com/jpd/finsync/ui/NavigationExtensions.kt`
- Create: `app/src/main/res/layout/fragment_home.xml`
- Create: `app/src/main/java/com/jpd/finsync/ui/Home/HomeFragment.kt`

The graph names `com.jpd.finsync.ui.SettingsFragment`, which Task 8 creates. Debug builds don't check graph class names, and nothing loads the graph until Task 9.

- [ ] **Step 1: Add strings**

In `strings.xml`, add these lines after `<string name="header_downloads">Downloads</string>`:

```xml
    <string name="header_home">Home</string>
    <string name="cd_settings">Settings</string>

    <!-- Home -->
    <string name="home_empty_title">Nothing here yet</string>
    <string name="home_empty_body">Sync and downloads are in Settings.</string>
```

- [ ] **Step 2: Create the navigation graph**

`app/src/main/res/navigation/nav_graph.xml`. There are no transition animations, matching today's `overridePendingTransition(0, 0)`.

```xml
<?xml version="1.0" encoding="utf-8"?>
<navigation
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/nav_graph"
    app:startDestination="@id/homeFragment">

    <fragment
        android:id="@+id/homeFragment"
        android:name="com.jpd.finsync.ui.HomeFragment">
        <action
            android:id="@+id/action_home_to_settings"
            app:destination="@id/settings_graph" />
    </fragment>

    <!-- Nested so SettingsViewModel lives exactly as long as you're in Settings, and so the
         notification deep link builds the Home → Settings → Sync Status back stack. -->
    <navigation
        android:id="@+id/settings_graph"
        app:startDestination="@id/settingsFragment">

        <fragment
            android:id="@+id/settingsFragment"
            android:name="com.jpd.finsync.ui.SettingsFragment">
            <action
                android:id="@+id/action_settings_to_sync_status"
                app:destination="@id/syncStatusFragment" />
            <action
                android:id="@+id/action_settings_to_downloads"
                app:destination="@id/downloadsFragment" />
            <action
                android:id="@+id/action_settings_to_album_selection"
                app:destination="@id/albumSelectionFragment" />
            <action
                android:id="@+id/action_settings_to_auto_sync"
                app:destination="@id/autoSyncFragment" />
        </fragment>

        <fragment
            android:id="@+id/syncStatusFragment"
            android:name="com.jpd.finsync.ui.SyncStatusFragment" />

        <fragment
            android:id="@+id/downloadsFragment"
            android:name="com.jpd.finsync.ui.DownloadsFragment" />

        <fragment
            android:id="@+id/albumSelectionFragment"
            android:name="com.jpd.finsync.ui.AlbumSelectionFragment" />

        <fragment
            android:id="@+id/autoSyncFragment"
            android:name="com.jpd.finsync.ui.AutoSyncFragment" />

    </navigation>

</navigation>
```

- [ ] **Step 3: Create `navigateSafely()`**

`app/src/main/java/com/jpd/finsync/ui/NavigationExtensions.kt`:

```kotlin
package com.jpd.finsync.ui

import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

/**
 * Runs [actionId] only while [fromId] is the current destination. Otherwise a quick double tap
 * would run the action again from the next screen, where it doesn't exist, and crash.
 */
fun Fragment.navigateSafely(@IdRes fromId: Int, @IdRes actionId: Int) {
    val navController = findNavController()
    if (navController.currentDestination?.id == fromId) navController.navigate(actionId)
}
```

- [ ] **Step 4: Create the Home layout**

`app/src/main/res/layout/fragment_home.xml`. The Settings button copies today's `btnSettings` from `activity_main.xml`.

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/bg_primary">

    <LinearLayout
        android:id="@+id/homeHeader"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:paddingHorizontal="20dp"
        android:paddingTop="16dp"
        android:paddingBottom="16dp"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent">

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="@string/header_home"
            android:textColor="@color/text_primary"
            android:textSize="20sp"
            android:fontFamily="@font/hk_regular" />

        <!-- Settings button, ringed by sync progress while a sync runs -->
        <FrameLayout
            android:layout_width="58dp"
            android:layout_height="58dp">

            <com.google.android.material.progressindicator.CircularProgressIndicator
                android:id="@+id/syncRing"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_gravity="center"
                android:visibility="gone"
                app:indicatorSize="58dp"
                app:indicatorInset="0dp"
                app:trackThickness="3dp"
                app:indicatorColor="@color/accent_green"
                app:trackColor="@android:color/transparent" />

            <com.google.android.material.card.MaterialCardView
                android:id="@+id/btnSettings"
                android:layout_width="48dp"
                android:layout_height="48dp"
                android:layout_gravity="center"
                android:clickable="true"
                android:focusable="true"
                android:contentDescription="@string/cd_settings"
                app:cardBackgroundColor="@color/surface_2"
                app:cardCornerRadius="24dp"
                app:cardElevation="0dp"
                app:strokeWidth="0dp">

                <ImageView
                    android:layout_width="22dp"
                    android:layout_height="22dp"
                    android:layout_gravity="center"
                    android:src="@drawable/ic_settings"
                    android:importantForAccessibility="no"
                    app:tint="@color/text_primary" />

            </com.google.android.material.card.MaterialCardView>
        </FrameLayout>
    </LinearLayout>

    <!-- Shown while Home has no library categories -->
    <LinearLayout
        android:id="@+id/emptyState"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:gravity="center_horizontal"
        app:layout_constraintTop_toBottomOf="@id/homeHeader"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/home_empty_title"
            android:textColor="@color/text_primary"
            android:textSize="18sp"
            android:fontFamily="@font/hk_extrabold" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:text="@string/home_empty_body"
            android:textColor="@color/muted"
            android:textSize="14sp"
            android:fontFamily="@font/hk_regular" />

    </LinearLayout>

</androidx.constraintlayout.widget.ConstraintLayout>
```

- [ ] **Step 5: Create `HomeFragment`**

`app/src/main/java/com/jpd/finsync/ui/Home/HomeFragment.kt`:

```kotlin
package com.jpd.finsync.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.jpd.finsync.R
import com.jpd.finsync.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnSettings.setOnClickListener {
            navigateSafely(R.id.homeFragment, R.id.action_home_to_settings)
        }
        viewModel.uiState.observe(viewLifecycleOwner) { renderSyncRing(SyncDisplay.from(it)) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun renderSyncRing(display: SyncDisplay) {
        if (display.status == SyncDisplay.Status.SYNCING) {
            binding.syncRing.showSyncProgress(display.progress)
        } else {
            binding.syncRing.visibility = View.GONE
        }
    }
}
```

- [ ] **Step 6: Build and test**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`, 15 tests passing.

- [ ] **Step 7: Checkpoint.** Stop. The user commits. Suggested message: `Add navigation graph and Home screen (not wired up yet)`

---

### Task 8: Settings screen

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/com/jpd/finsync/ui/Settings/SettingsViewModel.kt`
- Create: `app/src/main/res/layout/fragment_settings.xml`
- Create: `app/src/main/java/com/jpd/finsync/ui/Settings/SettingsFragment.kt`

This combines `SettingsListFragment`, the header and folder picker from `SettingsActivity`, and three new cards. Nothing shows it until Task 9.

- [ ] **Step 1: Add strings**

In `strings.xml`, add these lines after `<string name="home_empty_body">Sync and downloads are in Settings.</string>`:

```xml

    <!-- Settings -->
    <string name="header_settings">Settings</string>
    <string name="server_connected">Connected</string>
    <string name="server_offline">Offline</string>
    <string name="settings_sync_title">Sync</string>
    <string name="sync_card_detail">%1$d of %2$d tracks synced</string>
    <string name="settings_downloads_title">Downloads</string>
    <string name="settings_downloads_none">No albums on this device yet</string>
    <string name="settings_downloads_hint">Tap to manage downloads</string>
    <plurals name="settings_downloads_count">
        <item quantity="one">%d album on this device</item>
        <item quantity="other">%d albums on this device</item>
    </plurals>
```

- [ ] **Step 2: Add the downloads count to `SettingsViewModel`**

In `SettingsViewModel.kt`, replace:

```kotlin
    private val _syncDir = MutableLiveData<String>()
    val syncDir: LiveData<String> = _syncDir
```

with:

```kotlin
    private val _syncDir = MutableLiveData<String>()
    val syncDir: LiveData<String> = _syncDir

    private val _downloadedAlbumCount = MutableLiveData<Int>()
    val downloadedAlbumCount: LiveData<Int> = _downloadedAlbumCount
```

Then, directly before `    fun getSelectedAlbumIds(): Set<String> =`, add:

```kotlin
    fun refreshDownloadedAlbumCount() {
        viewModelScope.launch {
            val albums = visibleDownloadedAlbums(dao.getAllAlbums(), getSelectedAlbumIds())
            _downloadedAlbumCount.postValue(albums.size)
        }
    }

```

- [ ] **Step 3: Create the Settings layout**

`app/src/main/res/layout/fragment_settings.xml`. The last three cards are copied from `fragment_settings_list.xml` with the same IDs and text, reordered to match the spec: Albums to Sync, Auto-sync, Sync Directory.

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@color/bg_primary">

    <include
        android:id="@+id/header"
        layout="@layout/view_screen_header" />

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:paddingHorizontal="16dp"
            android:paddingTop="8dp"
            android:paddingBottom="16dp">

            <!-- Server -->
            <com.google.android.material.card.MaterialCardView
                android:id="@+id/cardServer"
                android:layout_width="match_parent"
                android:layout_height="48dp"
                android:layout_marginBottom="12dp"
                app:cardBackgroundColor="@color/surface_1"
                app:cardCornerRadius="24dp"
                app:cardElevation="0dp"
                app:strokeWidth="0dp">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:orientation="horizontal"
                    android:gravity="center_vertical"
                    android:paddingStart="8dp"
                    android:paddingEnd="16dp">

                    <ImageView
                        android:layout_width="35dp"
                        android:layout_height="35dp"
                        android:padding="3dp"
                        android:layout_marginEnd="2dp"
                        android:src="@drawable/ic_finsync"
                        android:importantForAccessibility="no" />

                    <TextView
                        android:id="@+id/tvServerName"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:textColor="@color/text_primary"
                        android:textSize="16sp"
                        android:fontFamily="@font/hk_regular"
                        android:ellipsize="end"
                        android:singleLine="true" />

                    <View
                        android:id="@+id/serverStatusDot"
                        android:layout_width="8dp"
                        android:layout_height="8dp"
                        android:layout_marginStart="12dp"
                        android:layout_marginEnd="8dp"
                        android:background="@drawable/circle_accent_muted" />

                    <TextView
                        android:id="@+id/tvServerStatus"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:textColor="@color/muted"
                        android:textSize="13sp"
                        android:fontFamily="@font/hk_regular" />

                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>

            <!-- Sync -->
            <com.google.android.material.card.MaterialCardView
                android:id="@+id/cardSync"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="12dp"
                app:cardBackgroundColor="@color/surface_1"
                app:cardCornerRadius="12dp"
                app:cardElevation="0dp"
                app:strokeWidth="0dp">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="16dp">

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:gravity="center_vertical">

                        <TextView
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:text="@string/settings_sync_title"
                            android:textColor="@color/text_primary"
                            android:textSize="16sp"
                            android:fontFamily="@font/hk_extrabold" />

                        <TextView
                            android:id="@+id/tvSyncCardStatus"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:textColor="@color/muted"
                            android:textSize="13sp"
                            android:fontFamily="@font/hk_regular" />

                    </LinearLayout>

                    <TextView
                        android:id="@+id/tvSyncCardDetail"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="4dp"
                        android:visibility="gone"
                        android:textColor="@color/text_primary"
                        android:textSize="14sp"
                        android:fontFamily="@font/hk_regular" />

                    <com.google.android.material.progressindicator.LinearProgressIndicator
                        android:id="@+id/syncCardProgress"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="12dp"
                        android:visibility="gone"
                        app:indicatorColor="@color/text_primary"
                        app:trackColor="@color/surface_2"
                        app:trackCornerRadius="2dp"
                        app:trackThickness="4dp" />

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/btnSyncCard"
                        style="@style/Widget.Material3.Button.OutlinedButton"
                        android:layout_width="match_parent"
                        android:layout_height="48dp"
                        android:layout_marginTop="12dp"
                        android:text="@string/btn_sync_now"
                        android:textColor="@color/text_primary"
                        android:textSize="15sp"
                        android:fontFamily="@font/hk_regular"
                        app:strokeColor="@color/surface_2"
                        app:cornerRadius="24dp" />

                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>

            <!-- Downloads -->
            <com.google.android.material.card.MaterialCardView
                android:id="@+id/cardDownloads"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="12dp"
                app:cardBackgroundColor="@color/surface_1"
                app:cardCornerRadius="12dp"
                app:cardElevation="0dp"
                app:strokeWidth="0dp">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="16dp">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/settings_downloads_title"
                        android:textColor="@color/text_primary"
                        android:textSize="16sp"
                        android:fontFamily="@font/hk_extrabold" />

                    <TextView
                        android:id="@+id/tvDownloadsSummary"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="4dp"
                        android:textColor="@color/text_primary"
                        android:textSize="14sp"
                        android:fontFamily="@font/hk_regular" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="4dp"
                        android:text="@string/settings_downloads_hint"
                        android:textColor="@color/muted"
                        android:textSize="13sp"
                        android:fontFamily="@font/hk_regular" />

                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>

            <!-- Albums to Sync -->
            <com.google.android.material.card.MaterialCardView
                android:id="@+id/cardAlbums"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="12dp"
                app:cardBackgroundColor="@color/surface_1"
                app:cardCornerRadius="12dp"
                app:cardElevation="0dp"
                app:strokeWidth="0dp">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="16dp">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Albums to Sync"
                        android:textColor="@color/text_primary"
                        android:textSize="16sp"
                        android:fontFamily="@font/hk_extrabold" />

                    <TextView
                        android:id="@+id/tvAlbumsSummary"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Loading..."
                        android:textColor="@color/text_primary"
                        android:textSize="14sp"
                        android:fontFamily="@font/hk_regular"
                        android:layout_marginTop="4dp" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Tap to change selection"
                        android:textColor="@color/muted"
                        android:textSize="13sp"
                        android:fontFamily="@font/hk_regular"
                        android:layout_marginTop="4dp" />

                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>

            <!-- Auto-sync -->
            <com.google.android.material.card.MaterialCardView
                android:id="@+id/cardAutoSync"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="12dp"
                app:cardBackgroundColor="@color/surface_1"
                app:cardCornerRadius="12dp"
                app:cardElevation="0dp"
                app:strokeWidth="0dp">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="16dp">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Auto-sync"
                        android:textColor="@color/text_primary"
                        android:textSize="16sp"
                        android:fontFamily="@font/hk_extrabold" />

                    <TextView
                        android:id="@+id/tvAutoSync"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Disabled"
                        android:textColor="@color/text_primary"
                        android:textSize="14sp"
                        android:fontFamily="@font/hk_regular"
                        android:layout_marginTop="4dp" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Tap to change selection"
                        android:textColor="@color/muted"
                        android:textSize="13sp"
                        android:fontFamily="@font/hk_regular"
                        android:layout_marginTop="4dp" />

                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>

            <!-- Sync Directory -->
            <com.google.android.material.card.MaterialCardView
                android:id="@+id/cardSyncDir"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                app:cardBackgroundColor="@color/surface_1"
                app:cardCornerRadius="12dp"
                app:cardElevation="0dp"
                app:strokeWidth="0dp">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="16dp">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Sync Directory"
                        android:textColor="@color/text_primary"
                        android:textSize="16sp"
                        android:fontFamily="@font/hk_extrabold" />

                    <TextView
                        android:id="@+id/tvSyncDir"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="—"
                        android:textColor="@color/text_primary"
                        android:textSize="14sp"
                        android:fontFamily="@font/hk_regular"
                        android:layout_marginTop="4dp" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Tap to change selection"
                        android:textColor="@color/muted"
                        android:textSize="13sp"
                        android:fontFamily="@font/hk_regular"
                        android:layout_marginTop="4dp" />

                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>

        </LinearLayout>
    </ScrollView>

</LinearLayout>
```

- [ ] **Step 4: Create `SettingsFragment`**

`app/src/main/java/com/jpd/finsync/ui/Settings/SettingsFragment.kt`. `bindSyncPreferences()`, `autoSyncLabel()` and `uriToPath()` keep the behaviour of `SettingsListFragment` and `SettingsActivity`. The only change is that `uriToPath()` now logs the exception it used to swallow silently.

```kotlin
package com.jpd.finsync.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.jpd.finsync.R
import com.jpd.finsync.databinding.FragmentSettingsBinding
import java.io.File

private const val TAG = "SettingsFragment"

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by navGraphViewModels(R.id.settings_graph)
    private val mainViewModel: MainViewModel by activityViewModels()

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        requireContext().contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        val path = uriToPath(uri) ?: uri.toString()
        viewModel.setSyncDirectory(path)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.header.tvTitle.setText(R.string.header_settings)
        binding.header.btnBack.setOnClickListener { findNavController().navigateUp() }

        bindServerAndSync()
        bindDownloads()
        bindSyncPreferences()
    }

    override fun onResume() {
        super.onResume()
        // Sub-screens can change the album selection, the downloads and the schedule. The Sync
        // card's track total depends on the selection, so reload it too.
        mainViewModel.loadAlbums()
        viewModel.refreshDownloadedAlbumCount()
        binding.tvAutoSync.text = autoSyncLabel(viewModel.getAutoSyncInterval())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Server and sync ───────────────────────────────────────────────────────

    private fun bindServerAndSync() {
        mainViewModel.config.observe(viewLifecycleOwner) { config ->
            binding.tvServerName.text = config?.serverName.orEmpty()
        }
        mainViewModel.uiState.observe(viewLifecycleOwner) { state ->
            renderServerStatus(state.serverConnected)
            renderSyncCard(SyncDisplay.from(state))
        }
        binding.cardServer.setOnClickListener {
            ServerBottomSheet().show(childFragmentManager, ServerBottomSheet.TAG)
        }
        binding.cardSync.setOnClickListener {
            navigateSafely(R.id.settingsFragment, R.id.action_settings_to_sync_status)
        }
        binding.btnSyncCard.setOnClickListener { mainViewModel.toggleSync() }
    }

    private fun renderServerStatus(connected: Boolean) {
        binding.serverStatusDot.setBackgroundResource(
            if (connected) R.drawable.circle_accent_green else R.drawable.circle_accent_muted
        )
        binding.tvServerStatus.setText(
            if (connected) R.string.server_connected else R.string.server_offline
        )
    }

    private fun renderSyncCard(display: SyncDisplay) {
        val isOffline = display.status == SyncDisplay.Status.OFFLINE
        binding.tvSyncCardStatus.setText(display.status.labelRes)
        binding.tvSyncCardStatus.setTextColor(color(display.status.labelColorRes))

        val detail = when {
            isOffline -> getString(R.string.sync_detail_offline)
            display.totalTracks > 0 ->
                getString(R.string.sync_card_detail, display.trackCount ?: 0, display.totalTracks)
            else -> null
        }
        binding.tvSyncCardDetail.text = detail
        binding.tvSyncCardDetail.isVisible = detail != null

        if (display.status == SyncDisplay.Status.SYNCING) {
            binding.syncCardProgress.showSyncProgress(display.progress)
        } else {
            binding.syncCardProgress.visibility = View.GONE
        }

        binding.btnSyncCard.setText(display.buttonLabelRes)
        binding.btnSyncCard.isEnabled = !isOffline
        binding.btnSyncCard.setTextColor(color(if (isOffline) R.color.muted else R.color.text_primary))
    }

    // ── Downloads ─────────────────────────────────────────────────────────────

    private fun bindDownloads() {
        viewModel.downloadedAlbumCount.observe(viewLifecycleOwner) { count ->
            binding.tvDownloadsSummary.text = if (count == 0) {
                getString(R.string.settings_downloads_none)
            } else {
                resources.getQuantityString(R.plurals.settings_downloads_count, count, count)
            }
        }
        binding.cardDownloads.setOnClickListener {
            navigateSafely(R.id.settingsFragment, R.id.action_settings_to_downloads)
        }
    }

    // ── Sync preferences ──────────────────────────────────────────────────────

    private fun bindSyncPreferences() {
        viewModel.albums.observe(viewLifecycleOwner) { albums ->
            val selectedIds = viewModel.getSelectedAlbumIds()
            val total = albums.size
            val isAll = selectedIds.contains("all") || selectedIds.isEmpty()
            val selectedCount = if (isAll) total else minOf(selectedIds.size, total)
            binding.tvAlbumsSummary.text = "$selectedCount of $total albums selected"
        }
        viewModel.syncDir.observe(viewLifecycleOwner) { path ->
            binding.tvSyncDir.text = path ?: "—"
        }
        binding.cardAlbums.setOnClickListener {
            navigateSafely(R.id.settingsFragment, R.id.action_settings_to_album_selection)
        }
        binding.cardAutoSync.setOnClickListener {
            navigateSafely(R.id.settingsFragment, R.id.action_settings_to_auto_sync)
        }
        binding.cardSyncDir.setOnClickListener { openFolderPicker() }
    }

    private fun openFolderPicker() {
        val startUri = viewModel.getSyncDirectoryPath()?.let { Uri.fromFile(File(it)) }
        folderPickerLauncher.launch(startUri)
    }

    // Turns a document-tree URI into a filesystem path when its storage volume is recognisable.
    private fun uriToPath(uri: Uri): String? = try {
        val docId = DocumentFile.fromTreeUri(requireContext(), uri)?.uri?.lastPathSegment
        docId?.let {
            val parts = it.split(":")
            if (parts.size == 2) {
                val (volume, rel) = parts
                if (volume == "primary") "/storage/emulated/0/$rel" else "/storage/$volume/$rel"
            } else null
        }
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't turn $uri into a path; saving the URI instead", e)
        null
    }

    private fun autoSyncLabel(interval: String) = when (interval) {
        "1"  -> "Every 1 hour"
        "6"  -> "Every 6 hours"
        "12" -> "Every 12 hours"
        "24" -> "Every 24 hours"
        else -> "Disabled"
    }

    private fun color(@ColorRes colorRes: Int) = ContextCompat.getColor(requireContext(), colorRes)
}
```

- [ ] **Step 5: Build and test**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`, 15 tests passing. If Kotlin warns that `path ?: "—"` is redundant because `syncDir` is non-null, leave it. It matches the existing code.

- [ ] **Step 6: Checkpoint.** Stop. The user commits. Suggested message: `Add Settings screen with server, sync and downloads cards (not wired up yet)`

---

### Task 9: Switch the app to the new shell

**Files:**
- Modify (replace contents): `app/src/main/res/layout/activity_main.xml`
- Modify (replace contents): `app/src/main/java/com/jpd/finsync/ui/Home/MainActivity.kt`
- Modify: `app/src/main/java/com/jpd/finsync/ui/Library/AlbumSelectionFragment.kt`
- Modify: `app/src/main/res/layout/fragment_album_selection.xml`
- Modify: `app/src/main/java/com/jpd/finsync/ui/Settings/AutoSyncFragment.kt`
- Modify: `app/src/main/res/layout/fragment_auto_sync.xml`
- Modify: `app/src/main/java/com/jpd/finsync/ui/Library/AlbumDetailFragment.kt`
- Modify: `app/src/main/java/com/jpd/finsync/service/SyncService.kt`

After this task, nothing can open `LibraryActivity`, `SettingsActivity` or `NavBarFragment` any more. They still compile, and Task 10 deletes them.

- [ ] **Step 1: Replace `activity_main.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/bg_primary"
    android:fitsSystemWindows="true">

    <androidx.fragment.app.FragmentContainerView
        android:id="@+id/navHost"
        android:name="androidx.navigation.fragment.NavHostFragment"
        android:layout_width="0dp"
        android:layout_height="0dp"
        app:defaultNavHost="true"
        app:navGraph="@navigation/nav_graph"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toTopOf="@id/miniPlayerContainer"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

    <!-- Hosts the mini-player; hidden until playback exists -->
    <FrameLayout
        android:id="@+id/miniPlayerContainer"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:visibility="gone"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

- [ ] **Step 2: Replace `MainActivity.kt`**

```kotlin
package com.jpd.finsync.ui

import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.jpd.finsync.databinding.ActivityMainBinding

/** Hosts every screen after login. Navigation swaps the screens; see res/navigation/nav_graph.xml. */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!viewModel.isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        observeViewModel()
        viewModel.checkServerConnection() // Initial check; will also be triggered by network callback and ServerBottomSheet.
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            runOnUiThread { viewModel.checkServerConnection() }
        }
        override fun onLost(network: Network) {
            runOnUiThread { viewModel.checkServerConnection() }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshConfig()
        val cm = getSystemService(ConnectivityManager::class.java)
        cm.registerDefaultNetworkCallback(networkCallback)
    }

    override fun onPause() {
        super.onPause()
        val cm = getSystemService(ConnectivityManager::class.java)
        cm.unregisterNetworkCallback(networkCallback)
    }

    private fun observeViewModel() {
        viewModel.config.observe(this) { config ->
            if (config == null) {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }
    }
}
```

- [ ] **Step 3: Give Albums to Sync its header and Navigation**

In `fragment_album_selection.xml`, make these two edits in this order, so the first one's text is still unique:

1. Point the card's top at the header. The card's constraints use an eight-space indent, and the top constraint is followed by its bottom constraint. Replace:

   ```xml
           app:layout_constraintTop_toTopOf="parent"
           app:layout_constraintBottom_toBottomOf="parent"
   ```

   with:

   ```xml
           app:layout_constraintTop_toBottomOf="@id/header"
           app:layout_constraintBottom_toBottomOf="parent"
   ```

2. Add the header above the card. Replace:

   ```xml
       <com.google.android.material.card.MaterialCardView
           android:id="@+id/selectionCard"
   ```

   with:

   ```xml
       <include
           android:id="@+id/header"
           layout="@layout/view_screen_header"
           android:layout_width="0dp"
           android:layout_height="wrap_content"
           app:layout_constraintTop_toTopOf="parent"
           app:layout_constraintStart_toStartOf="parent"
           app:layout_constraintEnd_toEndOf="parent" />

       <com.google.android.material.card.MaterialCardView
           android:id="@+id/selectionCard"
   ```

In `AlbumSelectionFragment.kt`:
1. Replace `import androidx.fragment.app.activityViewModels` with:

   ```kotlin
   import androidx.navigation.fragment.findNavController
   import androidx.navigation.navGraphViewModels
   ```

   Then replace `import com.jpd.finsync.databinding.FragmentAlbumSelectionBinding` with:

   ```kotlin
   import com.jpd.finsync.R
   import com.jpd.finsync.databinding.FragmentAlbumSelectionBinding
   ```

2. Replace `private val viewModel: SettingsViewModel by activityViewModels()` with:

   ```kotlin
       private val viewModel: SettingsViewModel by navGraphViewModels(R.id.settings_graph)
   ```

3. Directly before `        adapter = AlbumAdapter { albumId, isChecked ->`, add:

   ```kotlin
           binding.header.tvTitle.setText(R.string.header_settings)
           binding.header.btnBack.setOnClickListener { findNavController().navigateUp() }

   ```

4. Replace both occurrences of `parentFragmentManager.popBackStack()` with `findNavController().popBackStack()`. Popping the fragment manager directly would leave Navigation's back stack out of step.

- [ ] **Step 4: Give Auto-sync its header, Navigation and the copy fix**

In `fragment_auto_sync.xml`, make these edits in this order:

1. Point the card's top at the header. The card's `app:layout_constraintTop_toTopOf="parent"` is the only one in the file before step 2 adds another. Replace it with `app:layout_constraintTop_toBottomOf="@id/header"`.

2. Add the header above the card. Replace:

   ```xml
       <com.google.android.material.card.MaterialCardView
           android:id="@+id/autoSyncCard"
   ```

   with:

   ```xml
       <include
           android:id="@+id/header"
           layout="@layout/view_screen_header"
           android:layout_width="0dp"
           android:layout_height="wrap_content"
           app:layout_constraintTop_toTopOf="parent"
           app:layout_constraintStart_toStartOf="parent"
           app:layout_constraintEnd_toEndOf="parent" />

       <com.google.android.material.card.MaterialCardView
           android:id="@+id/autoSyncCard"
   ```

3. Replace the copy `You can also trigger a manual sync from the home screen at any time.` with `You can also sync manually at any time from the Sync card in Settings.`

In `AutoSyncFragment.kt`:
1. Replace `import androidx.fragment.app.activityViewModels` with:

   ```kotlin
   import androidx.navigation.fragment.findNavController
   import androidx.navigation.navGraphViewModels
   ```

2. Replace `private val viewModel: SettingsViewModel by activityViewModels()` with:

   ```kotlin
       private val viewModel: SettingsViewModel by navGraphViewModels(R.id.settings_graph)
   ```

3. Directly before `        // Pre-select the current interval`, add:

   ```kotlin
           binding.header.tvTitle.setText(R.string.header_settings)
           binding.header.btnBack.setOnClickListener { findNavController().navigateUp() }

   ```

4. Replace both occurrences of `parentFragmentManager.popBackStack()` with `findNavController().popBackStack()`.

- [ ] **Step 5: Point the album sheet at `DownloadsFragment`**

In `AlbumDetailFragment.kt`, replace:

```kotlin
            (activity as? LibraryActivity)?.reloadAlbums()
```

with:

```kotlin
            (parentFragment as? DownloadsFragment)?.reloadAlbums()
```

and replace:

```kotlin
        (activity as? LibraryActivity)?.hideAlbumDetail()
```

with:

```kotlin
        (parentFragment as? DownloadsFragment)?.hideAlbumDetail()
```

- [ ] **Step 6: Deep-link the sync notification to Sync Status**

In `SyncService.kt`:
1. Add `import androidx.navigation.NavDeepLinkBuilder` after `import androidx.core.app.NotificationCompat`.
2. Add this property after `private lateinit var notificationManager: NotificationManager`:

   ```kotlin

       // Opens Sync Status with Settings and Home behind it. The component is set explicitly
       // because the launcher activity is PermissionsActivity, not MainActivity.
       private val openSyncStatusIntent: PendingIntent by lazy {
           NavDeepLinkBuilder(this)
               .setComponentName(MainActivity::class.java)
               .setGraph(R.navigation.nav_graph)
               .setDestination(R.id.syncStatusFragment)
               .createPendingIntent()
       }
   ```

3. In `buildNotification()`, delete:

   ```kotlin
           val openIntent = PendingIntent.getActivity(
               this, 0,
               Intent(this, MainActivity::class.java),
               PendingIntent.FLAG_IMMUTABLE
           )
   ```

   and replace `.setContentIntent(openIntent)` with `.setContentIntent(openSyncStatusIntent)`.

- [ ] **Step 7: Build and test**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`, 15 tests passing.

- [ ] **Step 8: Ask the user for a smoke test on a device**

Ask the user to install the debug APK and check the following:
1. After login, the app opens on Home with the "Nothing here yet" message.
2. Settings opens from the round button and shows all six cards in the spec's order.
3. The Sync card opens Sync Status, and back returns to Settings.
4. Downloads lists albums. An album's sheet opens, and back closes the sheet first.
5. Albums to Sync and Auto-sync open under a "← Settings" header and return to Settings.

If anything fails, use superpowers:systematic-debugging before changing code.

- [ ] **Step 9: Checkpoint.** Stop. The user commits. Suggested message: `Switch to a single-activity shell with Home and Settings`

---

### Task 10: Delete the old screens (needs approval)

**Files:**
- Delete: the 11 files listed below
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Confirm nothing else uses them**

Run:

```bash
grep -rn "LibraryActivity\|SettingsActivity\|SettingsListFragment\|NavBarFragment" app/src/main
grep -rn "activity_library\|activity_settings\|fragment_settings_list\|fragment_nav_bar\|bg_nav_active\|ic_home\|ic_download\|ActivityLibraryBinding\|ActivitySettingsBinding\|FragmentSettingsListBinding\|FragmentNavBarBinding" app/src/main
```

Expected: matches only inside the files listed in Step 3, plus the two `<activity>` entries in `AndroidManifest.xml`. If anything else matches, stop and report it.

- [ ] **Step 2: Ask the user**

Ask: "Task 10 deletes these 11 files, which nothing uses any more, and removes their two manifest entries and two strings. OK to delete?" List the files from Step 3 in the question. Wait for a yes. If the answer is no, skip to Task 11. The app works with the files left in place.

- [ ] **Step 3: Delete the files**

```bash
rm app/src/main/java/com/jpd/finsync/ui/Library/LibraryActivity.kt
rm app/src/main/java/com/jpd/finsync/ui/Settings/SettingsActivity.kt
rm app/src/main/java/com/jpd/finsync/ui/Settings/SettingsListFragment.kt
rm app/src/main/java/com/jpd/finsync/ui/NavBarFragment.kt
rm app/src/main/res/layout/activity_library.xml
rm app/src/main/res/layout/activity_settings.xml
rm app/src/main/res/layout/fragment_settings_list.xml
rm app/src/main/res/layout/fragment_nav_bar.xml
rm app/src/main/res/drawable/bg_nav_active.xml
rm app/src/main/res/drawable/ic_home.xml
rm app/src/main/res/drawable/ic_download.xml
```

- [ ] **Step 4: Remove the manifest entries**

In `AndroidManifest.xml`, delete:

```xml
        <activity
            android:name=".ui.LibraryActivity"
            android:exported="false"
            android:label="@string/title_library"
            android:theme="@style/Theme.Finsync.Main" />

        <activity
            android:name=".ui.SettingsActivity"
            android:exported="false"
            android:label="@string/title_settings"
            android:theme="@style/Theme.Finsync.Main" />

```

- [ ] **Step 5: Remove the unused strings**

In `strings.xml`, delete:

```xml
    <string name="title_library">Finsync – Library</string>
    <string name="title_settings">Finsync – Settings</string>
```

- [ ] **Step 6: Build, test and re-check**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`, 15 tests passing.

Run the two `grep` commands from Step 1 again, plus `grep -rn "title_library\|title_settings" app/src/main`.
Expected: no output from any of them.

- [ ] **Step 7: Checkpoint.** Stop. The user commits. Suggested message: `Remove the old Library and Settings activities and the tab bar`

---

### Task 11: Update the README

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Update the architecture tree**

Replace:

```
└── ui/
    ├── PermissionsActivity     Launcher — permission onboarding
    ├── LoginActivity           Server URL + credential entry
    ├── MainActivity            Sync control and status
    ├── LibraryActivity         Browse downloaded albums
    └── SettingsActivity        Sync interval and directory preferences
```

with:

```
└── ui/
    ├── PermissionsActivity     Launcher — permission onboarding
    ├── LoginActivity           Server URL + credential entry
    ├── MainActivity            Hosts every screen after login (Jetpack Navigation)
    ├── HomeFragment            Start screen
    ├── SettingsFragment        Server, sync, downloads and sync preferences
    ├── SyncStatusFragment      Sync progress and controls
    └── DownloadsFragment       Browse and manage downloaded albums
```

- [ ] **Step 2: Update the setup steps**

Replace:

```
4. On the main screen, tap **Sync Now**. Progress appears in the notification drawer.
5. To adjust the sync schedule or storage location, open the three-dot menu → **Settings**.
```

with:

```
4. On Home, tap the **Settings** button, then **Sync Now** on the Sync card. Progress appears on the card, around the Settings button and in the notification drawer.
5. To adjust the sync schedule or storage location, use the cards in **Settings**.
```

- [ ] **Step 3: Checkpoint.** Stop. The user commits. Suggested message: `Update README for the new navigation`

---

### Task 12: Final verification

- [ ] **Step 1: Unit tests**

Run: `./gradlew :app:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`, 15 tests passing.

- [ ] **Step 2: Debug and release builds**

Run: `./gradlew :app:assembleDebug :app:assembleRelease --console=plain`
Expected: `BUILD SUCCESSFUL`. The release build runs R8 and `lintVitalRelease`, which is what the user ships. If lint reports a problem that existed before this plan, report it rather than fixing it.

- [ ] **Step 3: Ask the user to run the spec's on-device checklist**

Ask the user to run the checklist with the release or debug APK and report each result:
1. After login, the app lands on Home with the empty state.
2. Home → Settings → each card, then back from every screen to Home. Back on Home leaves the app.
3. Start a sync from the Sync card. The card, Sync Status and Home's ring all update.
4. Stop a sync from Sync Status.
5. Tap the sync notification mid-sync. It lands on Sync Status, and back goes to Settings, then Home.
6. Turn on airplane mode mid-sync. The sync stops, the server row shows Offline and the Sync button is disabled.
7. Turn on airplane mode while idle, then reconnect. The status does not change to "Sync stopped".
8. In Downloads, open an album sheet and press back to close it. Remove an album; the list and the Downloads card count both update.
9. Albums to Sync and Auto-sync save as they do today.
10. Pick a sync directory. Cancelling the picker leaves the path unchanged.
11. Log out from the server sheet. The app returns to Login.
12. Rotate on a Settings sub-screen. With "Don't keep activities" turned on, leave the app and return.

- [ ] **Step 4: Report**

Summarise the results for the user: the test count, both builds, and each checklist item. Give the exact output or the user's report for anything that failed. Don't claim an item passed unless it was run.
