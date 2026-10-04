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

        // A recreated fragment gets its open album sheet back from the child fragment manager,
        // but the container starts hidden and the back callback disabled, so match them to it.
        val openSheet = childFragmentManager.findFragmentById(binding.albumDetailContainer.id)
        binding.albumDetailContainer.visibility = if (openSheet != null) View.VISIBLE else View.GONE
        closeAlbumDetailCallback.isEnabled = openSheet != null

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
