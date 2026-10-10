package com.jpd.hz.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.annotation.IdRes
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.media3.common.Player
import androidx.mediarouter.app.SystemOutputSwitcherDialogController
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.jpd.hz.R
import com.jpd.hz.databinding.FragmentPlayerBinding
import com.jpd.hz.equaliser.EqualiserStore
import com.jpd.hz.library.chapterWindowAt

class PlayerFragment : Fragment() {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!
    private val playbackViewModel: PlaybackViewModel by activityViewModels()
    private var albumId: String? = null
    private var albumArtistId: String? = null
    // The playing book, or null for music (3b).
    private var book: BookPlayback? = null
    // The art currently shown, so it's only reloaded when the track's art changes.
    private var shownArtworkPath: String? = null
    private var userSeeking = false
    // Where the seek bar starts in the track: 0, or a book's current chapter (3b).
    private var seekWindowStartMs = 0L

    private val seekListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            if (fromUser) binding.tvElapsed.text = formatDuration(progress.toLong())
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {
            userSeeking = true
        }

        // "It seeks when you let go" (spec). A book's bar covers its chapter (mockup option A).
        override fun onStopTrackingTouch(seekBar: SeekBar) {
            userSeeking = false
            playbackViewModel.seekTo(seekWindowStartMs + seekBar.progress)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val backDispatcher = requireActivity().onBackPressedDispatcher
        binding.btnClose.setOnClickListener { backDispatcher.onBackPressed() }
        // Swiping the Player down closes it too.
        binding.root.listener =
            PlayerDismiss(binding.root, binding.playerContent) { backDispatcher.onBackPressed() }
        binding.btnEqualiser.setOnClickListener {
            navigateSafely(R.id.playerFragment, R.id.action_player_to_equaliser)
        }
        binding.playerContext.setOnClickListener { openBook() }
        binding.chipArtist.setOnClickListener { openArtist() }
        binding.chipAlbum.setOnClickListener { if (book != null) openBook() else openAlbum() }
        binding.btnPlayPause.setOnClickListener { playbackViewModel.togglePlayPause() }
        // A book's previous and next move between chapters (spec "Controls").
        binding.btnPrevious.setOnClickListener {
            if (book != null) playbackViewModel.previousChapter() else playbackViewModel.previous()
        }
        binding.btnNext.setOnClickListener {
            if (book != null) playbackViewModel.nextChapter() else playbackViewModel.next()
        }
        // As the mini-player: a book's swipe moves between chapters, like the buttons above.
        binding.trackSwipe.listener =
            TrackSwipe(binding.trackSwipe, binding.trackInfo, playbackViewModel)
        binding.btnSkipBack.setOnClickListener { playbackViewModel.skipBack() }
        binding.btnSkipForward.setOnClickListener { playbackViewModel.skipForward() }
        binding.btnRepeat.setOnClickListener { playbackViewModel.cycleRepeatMode() }
        binding.btnShuffle.setOnClickListener { playbackViewModel.toggleShuffle() }
        binding.btnOutput.setOnClickListener { openOutputSwitcher() }
        binding.btnQueue.setOnClickListener { openSheet(QueueSheet.TAG) { QueueSheet() } }
        binding.btnChapters.setOnClickListener { openSheet(ChaptersSheet.TAG) { ChaptersSheet() } }
        binding.btnSpeed.setOnClickListener { openSheet(SpeedSheet.TAG) { SpeedSheet() } }
        binding.seekBar.setOnSeekBarChangeListener(seekListener)

        playbackViewModel.state.observe(viewLifecycleOwner) { render(it) }
        playbackViewModel.position.observe(viewLifecycleOwner) { renderPosition(it) }
    }

    // The Equaliser is its own destination, so the Player always resumes after it (spec).
    override fun onResume() {
        super.onResume()
        renderEqualiserButton(EqualiserStore(requireContext()).load().enabled)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        shownArtworkPath = null
    }

    private fun render(state: PlaybackUiState) {
        albumId = state.albumId
        albumArtistId = state.albumArtistId
        book = state.book
        binding.tvAlbumName.text = state.albumTitle
        // Called on every state update; the view ignores the same title, so its cycle carries on.
        binding.tvTitle.setTitle(state.title)
        // A book's chips are its author and the book (spec "Labels"); music's are the album
        // artist and the album, each opening its page.
        val artistChip = state.book?.let { it.author ?: getString(R.string.unknown_author) }
            ?: state.albumArtist
        binding.chipArtist.text = artistChip
        binding.chipArtist.visibility = if (artistChip.isBlank()) View.GONE else View.VISIBLE
        binding.chipArtist.isClickable = state.albumArtistId != null
        binding.chipAlbum.text = state.albumTitle
        binding.chipAlbum.visibility = if (state.albumTitle.isBlank()) View.GONE else View.VISIBLE

        val info = formatInfoRow(state.info)
        binding.tvInfo.text = info
        binding.tvInfo.visibility = if (info.isEmpty()) View.GONE else View.VISIBLE

        if (state.artworkPath != shownArtworkPath) {
            shownArtworkPath = state.artworkPath
            loadArtwork(binding.ivArt, state.artworkPath)
        }

        val playIcon = if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        val playLabel = if (state.isPlaying) R.string.cd_pause else R.string.cd_play
        binding.ivPlayPause.setImageResource(playIcon)
        binding.btnPlayPause.contentDescription = getString(playLabel)

        renderRepeat(state.repeatMode)
        tint(binding.btnShuffle, if (state.shuffle) accent() else muted())
        renderMode(state.book)
    }

    // Music shows repeat, shuffle and Queue; a book shows −15, +30, Chapters, its speed and the
    // time left (spec "Player with a book"). Hidden controls take their spacers with them.
    private fun renderMode(book: BookPlayback?) {
        val isBook = book != null
        val musicOnly = if (isBook) View.GONE else View.VISIBLE
        val bookOnly = if (isBook) View.VISIBLE else View.GONE
        val label = if (isBook) R.string.player_playing_book else R.string.player_playing_from_album
        binding.tvContextLabel.setText(label)
        binding.playerContext.isClickable = isBook
        listOf(
            binding.btnRepeat, binding.spaceAfterRepeat,
            binding.spaceBeforeShuffle, binding.btnShuffle, binding.btnQueue
        ).forEach { it.visibility = musicOnly }
        listOf(
            binding.btnSkipBack, binding.spaceAfterSkipBack, binding.spaceBeforeSkipForward,
            binding.btnSkipForward, binding.btnSpeed, binding.tvBookLeft
        ).forEach { it.visibility = bookOnly }
        // With no chapters there's nothing to pick, and the bar covers the whole book (spec).
        val hasChapters = book != null && book.chapters.size > 1
        binding.btnChapters.visibility = if (hasChapters) View.VISIBLE else View.GONE
        binding.tvSpeed.text = book?.let { formatSpeed(it.speed) }
        val previousLabel = if (isBook) R.string.cd_previous_chapter else R.string.cd_previous
        val nextLabel = if (isBook) R.string.cd_next_chapter else R.string.cd_next
        binding.btnPrevious.contentDescription = getString(previousLabel)
        binding.btnNext.contentDescription = getString(nextLabel)
    }

    private fun renderPosition(position: PlaybackPosition) {
        // While dragging, the bar keeps the span it started with, so letting go lands where shown.
        if (userSeeking) return
        val currentBook = book
        val window = currentBook?.let {
            chapterWindowAt(it.chapterStartsMs, position.positionMs, position.durationMs)
        }
        val startMs = window?.startMs ?: 0L
        val lengthMs = window?.lengthMs ?: position.durationMs
        val elapsedMs = position.positionMs - startMs
        seekWindowStartMs = startMs
        binding.seekBar.max = lengthMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        binding.seekBar.progress = elapsedMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        binding.tvElapsed.text = formatDuration(elapsedMs)
        binding.tvTotal.text = formatDuration(lengthMs)
        if (currentBook != null) {
            // Book time, not adjusted for speed (spec "Progress").
            val leftMs = (position.durationMs - position.positionMs).coerceAtLeast(0L)
            binding.tvBookLeft.text =
                getString(R.string.player_book_left, formatListLength(resources, listOf(leftMs)))
        }
    }

    // Muted when off, the accent for all, the accent with a "1" for one.
    private fun renderRepeat(repeatMode: Int) {
        val icon = if (repeatMode == Player.REPEAT_MODE_ONE) {
            R.drawable.ic_repeat_one
        } else {
            R.drawable.ic_repeat
        }
        binding.btnRepeat.setImageResource(icon)
        tint(binding.btnRepeat, if (repeatMode == Player.REPEAT_MODE_OFF) muted() else accent())
    }

    // The accent while the EQ is on, text_primary while it's off (spec "Player").
    private fun renderEqualiserButton(enabled: Boolean) {
        val textPrimary = ContextCompat.getColor(requireContext(), R.color.text_primary)
        tint(binding.btnEqualiser, if (enabled) accent() else textPrimary)
        val label = if (enabled) R.string.cd_equaliser_on else R.string.cd_equaliser_off
        binding.btnEqualiser.contentDescription = getString(label)
    }

    private fun tint(view: ImageView, @ColorInt colour: Int) {
        ImageViewCompat.setImageTintList(view, ColorStateList.valueOf(colour))
    }

    @ColorInt
    private fun accent(): Int = requireContext().accentColor()

    @ColorInt
    private fun muted(): Int = ContextCompat.getColor(requireContext(), R.color.muted)

    private fun openAlbum() {
        val id = albumId ?: return
        openPage(R.id.albumFragment, R.id.action_player_to_album, bundleOf(ARG_ALBUM_ID to id))
    }

    private fun openArtist() {
        val id = albumArtistId ?: return
        openPage(
            R.id.groupFragment,
            R.id.action_player_to_group,
            bundleOf(ARG_GROUP_TYPE to GROUP_TYPE_ARTIST, ARG_GROUP_ID to id)
        )
    }

    // A book's page (spec "Header"; 3b).
    private fun openBook() {
        val id = book?.bookId ?: return
        openPage(R.id.bookFragment, R.id.action_player_to_book, bundleOf(ARG_BOOK_ID to id))
    }

    // Closes the Player if that same page is directly beneath it; otherwise opens it.
    private fun openPage(@IdRes destinationId: Int, @IdRes actionId: Int, args: Bundle) {
        val navController = findNavController()
        val beneath = navController.previousBackStackEntry
        val pageBeneath = beneath?.destination?.id == destinationId &&
            args.keySet().all { key -> beneath.arguments?.getString(key) == args.getString(key) }
        if (pageBeneath) {
            navController.popBackStack()
        } else {
            navigateSafely(R.id.playerFragment, actionId, args)
        }
    }

    private fun openOutputSwitcher() {
        if (!SystemOutputSwitcherDialogController.showDialog(requireContext())) {
            Toast.makeText(requireContext(), R.string.output_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    // One copy of each sheet at a time, as the Queue sheet always had.
    private fun openSheet(tag: String, create: () -> BottomSheetDialogFragment) {
        if (childFragmentManager.findFragmentByTag(tag) == null) {
            create().show(childFragmentManager, tag)
        }
    }
}
