package com.jpd.finsync.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.jpd.finsync.databinding.FragmentAlbumDetailBinding
import com.jpd.finsync.db.SyncDatabase
import com.jpd.finsync.db.SyncedAlbum
import com.jpd.finsync.service.SyncService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlbumDetailFragment : Fragment() {

    private var _binding: FragmentAlbumDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var albumId: String
    private lateinit var albumName: String
    private var albumArtist: String? = null
    private var artworkPath: String? = null

    companion object {
        private const val ARG_ALBUM_ID      = "album_id"
        private const val ARG_ALBUM_NAME    = "album_name"
        private const val ARG_ALBUM_ARTIST  = "album_artist"
        private const val ARG_ARTWORK_PATH  = "artwork_path"

        fun newInstance(album: SyncedAlbum) = AlbumDetailFragment().apply {
            arguments = bundleOf(
                ARG_ALBUM_ID     to album.albumId,
                ARG_ALBUM_NAME   to album.name,
                ARG_ALBUM_ARTIST to album.albumArtist,
                ARG_ARTWORK_PATH to album.artworkPath
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        albumId     = requireArguments().getString(ARG_ALBUM_ID)!!
        albumName   = requireArguments().getString(ARG_ALBUM_NAME)!!
        albumArtist = requireArguments().getString(ARG_ALBUM_ARTIST)
        artworkPath = requireArguments().getString(ARG_ARTWORK_PATH)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlbumDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvAlbumName.text  = albumName
        binding.tvArtistName.text = albumArtist ?: "Unknown Artist"

        val dao = SyncDatabase.getInstance(requireContext()).syncDao()
        lifecycleScope.launch {
            val artFile: File? = withContext(Dispatchers.IO) {
                artworkPath?.let { File(it).takeIf { f -> f.exists() } }
                    ?: run {
                        // Fallback: scan the album folder for folder.jpg / folder.png
                        val trackPath = dao.getTracksForAlbum(albumId).firstOrNull()?.localPath
                            ?: return@run null
                        val folder = File(trackPath).parentFile ?: return@run null
                        listOf("folder.jpg", "folder.png").map { File(folder, it) }.firstOrNull { it.exists() }
                    }
            }
            if (artFile != null) {
                Glide.with(this@AlbumDetailFragment)
                    .load(artFile)
                    .centerCrop()
                    .into(binding.ivAlbumArt)
            }

            val lastSynced = withContext(Dispatchers.IO) { dao.getLastSyncedTimeForAlbum(albumId) }
            binding.tvLastSynced.text = if (lastSynced != null) {
                val fmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                "Last synced: ${fmt.format(Date(lastSynced))}"
            } else {
                "Not yet synced"
            }

            val track = withContext(Dispatchers.IO) { dao.getTracksForAlbum(albumId).firstOrNull() }
            val folderPath = track?.localPath?.let { File(it).parent }
            if (!folderPath.isNullOrEmpty()) {
                binding.tvLocalFolderPath.text = "Local folder: $folderPath"
                binding.tvLocalFolderPath.visibility = View.VISIBLE
                binding.tvLocalFolderPath.setOnClickListener {
                    val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Folder Path", folderPath)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(requireContext(), "Copied folder path to clipboard", Toast.LENGTH_SHORT).show()
                }
            } else {
                binding.tvLocalFolderPath.visibility = View.GONE
            }
        }

        binding.root.setOnClickListener { dismiss() }
        binding.detailCard.setOnClickListener { /* consume — don't dismiss */ }

        binding.btnClose.setOnClickListener { dismiss() }
        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnResync.setOnClickListener { resyncAlbum() }
        binding.btnRemove.setOnClickListener { removeAlbum() }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private fun resyncAlbum() {
        val dao = SyncDatabase.getInstance(requireContext()).syncDao()
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val tracks = dao.getTracksForAlbum(albumId)
                tracks.forEach { File(it.localPath).delete() }
                dao.deleteTracksForAlbum(albumId)
            }
            ContextCompat.startForegroundService(
                requireContext(),
                Intent(requireContext(), SyncService::class.java).apply {
                    action = SyncService.ACTION_START
                }
            )
            dismiss()
            Toast.makeText(requireContext(), "Re-syncing $albumName…", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeAlbum() {
        val dao = SyncDatabase.getInstance(requireContext()).syncDao()
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val tracks = dao.getTracksForAlbum(albumId)
                tracks.forEach { File(it.localPath).delete() }

                tracks.firstOrNull()?.localPath?.let { path ->
                    val folder = File(path).parentFile
                    if (folder?.listFiles()?.isEmpty() == true) folder.delete()
                }

                dao.deleteTracksForAlbum(albumId)
                dao.deleteAlbum(albumId)

                val prefs = requireContext()
                    .getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
                val selectedIds = (prefs.getStringSet("selected_albums", emptySet())
                    ?: emptySet()).toMutableSet()
                if (!selectedIds.contains("all") && selectedIds.remove(albumId)) {
                    prefs.edit().putStringSet("selected_albums", selectedIds).apply()
                }
            }
            dismiss()
            (activity as? LibraryActivity)?.reloadAlbums()
        }
    }

    private fun dismiss() {
        (activity as? LibraryActivity)?.hideAlbumDetail()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
