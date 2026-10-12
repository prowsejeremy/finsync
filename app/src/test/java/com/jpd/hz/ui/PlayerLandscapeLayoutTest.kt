package com.jpd.hz.ui

import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import com.jpd.hz.databinding.FragmentPlayerBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Player on its side: the art on the left, the details and controls beside it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w${LANDSCAPE_WIDTH_DP}dp-h${LANDSCAPE_HEIGHT_DP}dp-land-mdpi")
class PlayerLandscapeLayoutTest {

    private lateinit var binding: FragmentPlayerBinding

    @Before
    fun setUp() {
        binding = FragmentPlayerBinding.inflate(LayoutInflater.from(themedContext()))
        binding.tvAlbumName.text = "Kind of Blue"
        binding.tvTitle.setTitle("So What")
        binding.chipArtist.text = "Miles Davis"
        binding.chipAlbum.text = "Kind of Blue"
        binding.tvInfo.text = "FLAC · 24-bit · 96 kHz"
        binding.tvElapsed.text = "1:02"
        binding.tvTotal.text = "9:22"
    }

    @Test
    fun musicArtSitsLeftOfItsDetailsAndControls() {
        layOut(binding.root, LANDSCAPE_WIDTH_DP, LANDSCAPE_HEIGHT_DP)
        assertArtBesideDetailsAndControls()
    }

    @Test
    fun bookArtSitsLeftOfItsDetailsAndControls() {
        showBook()
        layOut(binding.root, LANDSCAPE_WIDTH_DP, LANDSCAPE_HEIGHT_DP)
        assertArtBesideDetailsAndControls()
    }

    @Test
    fun theColumnScrollsToThePillsWhenItCantFit() {
        showBook()
        layOut(binding.root, LANDSCAPE_WIDTH_DP, CRAMPED_HEIGHT_DP)

        val scroller = binding.pills.scrollingAncestor()
        assertNotNull("nothing scrolls the details and controls", scroller)
        scroller!!.scrollToEnd()

        val visible = scroller.frameIn(binding.root)
        val pills = binding.pills.frameIn(binding.root)
        assertTrue("pills $pills are outside the scroller's $visible", visible.contains(pills))
    }

    // The tallest column: a book adds the time left and the Chapters and speed pills (3b).
    private fun showBook() {
        binding.tvBookLeft.text = "8 h 28 min left in book"
        binding.tvSpeed.text = "1.25×"
        listOf(binding.tvBookLeft, binding.btnChapters, binding.btnSpeed)
            .forEach { it.visibility = View.VISIBLE }
        binding.btnQueue.visibility = View.GONE
    }

    private fun assertArtBesideDetailsAndControls() {
        val root = binding.root
        val screen = Rect(0, 0, root.width, root.height)
        val art = binding.artCard.frameIn(root)
        assertTrue("art $art is under half the screen's height", art.height() >= root.height / 2)
        assertEquals("art $art isn't square", art.width(), art.height())

        val column = with(binding) {
            listOf(tvTitle, chips, tvInfo, seekBar, times, tvBookLeft, controls, pills)
        }.filter { it.visibility == View.VISIBLE }.map { it.frameIn(root) }
        column.forEach { frame ->
            assertTrue("$frame is off the screen", screen.contains(frame))
            assertTrue("$frame isn't right of the art $art", frame.left >= art.right)
        }
        column.zipWithNext { above, below ->
            assertTrue("$above overlaps $below", above.bottom <= below.top)
        }

        val header = with(binding) { listOf(btnClose, playerContext, btnEqualiser) }
            .map { it.frameIn(root) }
        header.forEach { frame ->
            assertTrue("header $frame is off the screen", screen.contains(frame))
            (column + art).forEach { other ->
                assertFalse("header $frame overlaps $other", Rect.intersects(frame, other))
            }
        }
    }
}
