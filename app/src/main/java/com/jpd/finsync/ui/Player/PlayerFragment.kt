package com.jpd.finsync.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.media3.common.Player
import androidx.mediarouter.app.SystemOutputSwitcherDialogController
import androidx.navigation.fragment.findNavController
import com.jpd.finsync.R
import com.jpd.finsync.databinding.FragmentPlayerBinding

class PlayerFragment : Fragment() {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!
    private val playbackViewModel: PlaybackViewModel by activityViewModels()
    private var albumId: String? = null
    // The art currently shown, so it's only reloaded when the track's art changes.
    private var shownArtworkPath: String? = null
    private var userSeeking = false

    private val seekListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            if (fromUser) binding.tvElapsed.text = formatDuration(progress.toLong())
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {
            userSeeking = true
        }

        // "It seeks when you let go" (spec).
        override fun onStopTrackingTouch(seekBar: SeekBar) {
            userSeeking = false
            playbackViewModel.seekTo(seekBar.progress.toLong())
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
        binding.chipAlbum.setOnClickListener { openAlbum() }
        binding.btnPlayPause.setOnClickListener { playbackViewModel.togglePlayPause() }
        binding.btnPrevious.setOnClickListener { playbackViewModel.previous() }
        binding.btnNext.setOnClickListener { playbackViewModel.next() }
        binding.btnRepeat.setOnClickListener { playbackViewModel.cycleRepeatMode() }
        binding.btnShuffle.setOnClickListener { playbackViewModel.toggleShuffle() }
        binding.btnOutput.setOnClickListener { openOutputSwitcher() }
        binding.btnQueue.setOnClickListener { openQueue() }
        binding.seekBar.setOnSeekBarChangeListener(seekListener)

        playbackViewModel.state.observe(viewLifecycleOwner) { render(it) }
        playbackViewModel.position.observe(viewLifecycleOwner) { renderPosition(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        shownArtworkPath = null
    }

    private fun render(state: PlaybackUiState) {
        albumId = state.albumId
        binding.tvAlbumName.text = state.albumTitle
        binding.tvTitle.text = state.title
        binding.chipArtist.text = state.artist
        binding.chipArtist.visibility = if (state.artist.isBlank()) View.GONE else View.VISIBLE
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
        tint(binding.btnShuffle, if (state.shuffle) R.color.accent_green else R.color.muted)
    }

    private fun renderPosition(position: PlaybackPosition) {
        binding.seekBar.max = position.durationMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        binding.tvTotal.text = formatDuration(position.durationMs)
        if (userSeeking) return
        binding.seekBar.progress = position.positionMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        binding.tvElapsed.text = formatDuration(position.positionMs)
    }

    // Muted when off, green for all, green with a "1" for one.
    private fun renderRepeat(repeatMode: Int) {
        val icon = if (repeatMode == Player.REPEAT_MODE_ONE) {
            R.drawable.ic_repeat_one
        } else {
            R.drawable.ic_repeat
        }
        val colour = if (repeatMode == Player.REPEAT_MODE_OFF) R.color.muted else R.color.accent_green
        binding.btnRepeat.setImageResource(icon)
        tint(binding.btnRepeat, colour)
    }

    private fun tint(view: ImageView, @ColorRes colour: Int) {
        val colourList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), colour))
        ImageViewCompat.setImageTintList(view, colourList)
    }

    // Closes the Player if that album's detail is directly beneath it; otherwise opens it.
    private fun openAlbum() {
        val id = albumId ?: return
        val navController = findNavController()
        val beneath = navController.previousBackStackEntry
        val albumBeneath = beneath?.destination?.id == R.id.albumFragment &&
            beneath.arguments?.getString(ARG_ALBUM_ID) == id
        if (albumBeneath) {
            navController.popBackStack()
        } else {
            navigateSafely(
                R.id.playerFragment,
                R.id.action_player_to_album,
                bundleOf(ARG_ALBUM_ID to id)
            )
        }
    }

    private fun openOutputSwitcher() {
        if (!SystemOutputSwitcherDialogController.showDialog(requireContext())) {
            Toast.makeText(requireContext(), R.string.output_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openQueue() {
        if (childFragmentManager.findFragmentByTag(QueueSheet.TAG) == null) {
            QueueSheet().show(childFragmentManager, QueueSheet.TAG)
        }
    }
}
