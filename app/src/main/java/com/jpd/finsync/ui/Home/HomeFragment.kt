package com.jpd.finsync.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.jpd.finsync.R
import com.jpd.finsync.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private val libraryViewModel: LibraryViewModel by viewModels()

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
        binding.cardAlbums.setOnClickListener {
            navigateSafely(R.id.homeFragment, R.id.action_home_to_albums)
        }
        binding.cardAlbumArtists.setOnClickListener {
            navigateSafely(R.id.homeFragment, R.id.action_home_to_album_artists)
        }
        binding.cardGenres.setOnClickListener {
            navigateSafely(R.id.homeFragment, R.id.action_home_to_genres)
        }
        binding.cardSongs.setOnClickListener {
            navigateSafely(R.id.homeFragment, R.id.action_home_to_songs)
        }
        binding.cardPlaylists.setOnClickListener {
            navigateSafely(R.id.homeFragment, R.id.action_home_to_playlists)
        }
        binding.btnRetry.setOnClickListener { libraryViewModel.retry() }
        viewModel.uiState.observe(viewLifecycleOwner) { renderSyncRing(SyncDisplay.from(it)) }
        libraryViewModel.homeState.observe(viewLifecycleOwner) { renderLibrary(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun renderSyncRing(display: SyncDisplay) {
        if (display.status == SyncDisplay.Status.SYNCING) {
            binding.syncRing.showSyncProgress(display.progress)
        } else {
            binding.syncRing.hideSyncProgress()
        }
    }

    private fun renderLibrary(state: HomeLibraryState) {
        val ready = state as? HomeLibraryState.Ready
        binding.libraryCards.visibility = if (ready != null) View.VISIBLE else View.GONE
        binding.tvAlbumsHint.visibility =
            if (ready?.nothingVisible == true) View.VISIBLE else View.GONE
        binding.libraryStatus.visibility = if (ready == null) View.VISIBLE else View.GONE
        val failed = state == HomeLibraryState.Failed
        binding.btnRetry.visibility = if (failed) View.VISIBLE else View.GONE
        binding.tvLibraryStatus.setText(
            if (failed) R.string.home_library_failed else R.string.home_library_building
        )
        if (ready == null) return
        binding.tvAlbumsCount.text = ready.albumCount.toString()
        binding.tvAlbumArtistsCount.text = ready.albumArtistCount.toString()
        binding.tvGenresCount.text = ready.genreCount.toString()
        binding.tvSongsCount.text = ready.songCount.toString()
        binding.tvPlaylistsCount.text = ready.playlistCount.toString()
    }
}
