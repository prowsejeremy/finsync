package com.jpd.hz.ui

import android.view.LayoutInflater
import android.view.View
import com.jpd.hz.R
import com.jpd.hz.databinding.FragmentHomeBinding
import com.jpd.hz.databinding.ItemHomeCategoryBinding
import com.jpd.hz.home.HomeCategory
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Home's category cards and empty state on a screen too short to show them all. The empty state's
 * last button is "Set up an adapter" (adapter harness spec, H9.6).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w${LANDSCAPE_WIDTH_DP}dp-h${LANDSCAPE_HEIGHT_DP}dp-land-mdpi")
class HomeLayoutTest {

    @Test
    fun `the last card scrolls into view in landscape`() {
        val inflater = LayoutInflater.from(themedContext())
        val binding = FragmentHomeBinding.inflate(inflater)
        val cards = binding.libraryCards
        cards.visibility = View.VISIBLE
        val lastCard = HomeCategory.entries
            .map { ItemHomeCategoryBinding.inflate(inflater, cards, true).root }
            .last()
        layOut(binding.root, LANDSCAPE_WIDTH_DP, LANDSCAPE_HEIGHT_DP)

        val scroller = cards.scrollingAncestor()
        assertNotNull("nothing scrolls Home's cards", scroller)
        scroller!!.scrollToEnd()

        val visible = scroller.frameIn(binding.root)
        val card = lastCard.frameIn(binding.root)
        assertTrue("last card $card is outside the scroller's $visible", visible.contains(card))
    }

    @Test
    fun `the empty state's buttons scroll into view in landscape`() {
        val inflater = LayoutInflater.from(themedContext())
        val binding = FragmentHomeBinding.inflate(inflater)
        val cards = binding.libraryCards
        cards.visibility = View.VISIBLE
        HomeCategory.entries.forEach { ItemHomeCategoryBinding.inflate(inflater, cards, true) }
        binding.tvLibraryNote.text =
            inflater.context.getString(R.string.home_library_empty, "Music")
        binding.tvLibraryNote.visibility = View.VISIBLE
        binding.emptyActions.visibility = View.VISIBLE
        layOut(binding.root, LANDSCAPE_WIDTH_DP, LANDSCAPE_HEIGHT_DP)

        val lastButton = binding.btnSetUpAdapter
        val scroller = lastButton.scrollingAncestor()
        assertNotNull("nothing scrolls Home's empty-state buttons", scroller)
        scroller!!.scrollToEnd()

        val visible = scroller.frameIn(binding.root)
        val button = lastButton.frameIn(binding.root)
        assertTrue("button $button is outside the scroller's $visible", visible.contains(button))
    }
}
