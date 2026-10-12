package com.jpd.hz.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.jpd.hz.R
import com.jpd.hz.databinding.FragmentConnectionBinding
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The platform page's choices are only the rows ConnectionFragment adds, one per kind the platform
 * offers. Fixed Albums, Playlists and Books cards in the layout would show a second set that
 * nothing fills in, stuck on "Loading…" (found on the phone in the harness's device check).
 */
@RunWith(RobolectricTestRunner::class)
class ConnectionLayoutTest {

    @Test
    fun `the page's only choice rows are the ones it adds`() {
        val context = themedContext()
        val binding = FragmentConnectionBinding.inflate(LayoutInflater.from(context))
        val texts = binding.root.textViews().map { it.text.toString() }
        val sectionLabel = context.getString(R.string.settings_section_what_to_sync)
        val loading = context.getString(R.string.settings_loading)

        assertEquals(1, texts.count { it == sectionLabel })
        assertEquals(0, texts.count { it == loading })
        assertEquals(0, binding.choiceRows.childCount)
    }

    private fun View.textViews(): List<TextView> = when (this) {
        is TextView -> listOf(this)
        is ViewGroup -> (0 until childCount).flatMap { getChildAt(it).textViews() }
        else -> emptyList()
    }
}
