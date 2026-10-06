package com.jpd.hz.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import com.jpd.hz.library.GroupDetail
import com.jpd.hz.library.LibraryRepository

/**
 * The artist or genre behind a group page or its All songs. The default factory fills
 * [savedState] from the fragment's arguments.
 */
class GroupViewModel(app: Application, savedState: SavedStateHandle) : AndroidViewModel(app) {

    val groupType: String =
        checkNotNull(savedState.get<String>(ARG_GROUP_TYPE)) { "A group page needs a groupType" }

    val groupId: String =
        checkNotNull(savedState.get<String>(ARG_GROUP_ID)) { "A group page needs a groupId" }

    val isArtist: Boolean = groupType == GROUP_TYPE_ARTIST

    /** Null once nothing in the group is visible, e.g. after a sync removes it. */
    val group: LiveData<GroupDetail?> = LibraryRepository(app).let { library ->
        if (isArtist) library.artist(groupId) else library.genre(groupId)
    }.asLiveData()
}
