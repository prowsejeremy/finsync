package com.jpd.finsync.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.jpd.finsync.R
import com.jpd.finsync.databinding.ActivityLibraryBinding
import com.jpd.finsync.databinding.ItemAlbumBinding
import com.jpd.finsync.db.SyncDatabase
import com.jpd.finsync.db.SyncedAlbum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class LibraryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLibraryBinding
    private lateinit var adapter: AlbumAdapter
    private val db by lazy { SyncDatabase.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.albumDetailContainer.visibility == View.VISIBLE) {
                    hideAlbumDetail()
                } else {
                    finish()
                }
            }
        })

        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.navBar, NavBarFragment.newInstance(NavBarFragment.Tab.DOWNLOADS))
                .commit()
        }

        adapter = AlbumAdapter { item -> showAlbumDetail(item) }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        loadAlbums()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    fun reloadAlbums() = loadAlbums()

    private fun loadAlbums() {
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                val dao = db.syncDao()
                val selectedIds = getSharedPreferences("settings", MODE_PRIVATE)
                    .getStringSet("selected_albums", emptySet()) ?: emptySet()
                val showAll = selectedIds.isEmpty() || selectedIds.contains("all")

                dao.getAllAlbums()
                    .filter { album -> showAll || selectedIds.contains(album.albumId) }
                    .map { album ->
                        val synced = dao.getSyncedTrackCountForAlbum(album.albumId)
                        val artworkPath = album.artworkPath
                            ?: run {
                                val trackPath = dao.getTracksForAlbum(album.albumId).firstOrNull()?.localPath
                                    ?: return@run null
                                val folder = File(trackPath).parentFile ?: return@run null
                                listOf("folder.jpg", "folder.png")
                                    .map { File(folder, it) }
                                    .firstOrNull { it.exists() }
                                    ?.absolutePath
                            }
                        AlbumItem(album, synced, artworkPath)
                    }
            }
            adapter.submit(items)
            binding.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    // ── Album detail overlay ──────────────────────────────────────────────────

    fun showAlbumDetail(item: AlbumItem) {
        binding.albumDetailContainer.visibility = View.VISIBLE
        supportFragmentManager.beginTransaction()
            .replace(binding.albumDetailContainer.id, AlbumDetailFragment.newInstance(item.album))
            .commit()
    }

    fun hideAlbumDetail() {
        binding.albumDetailContainer.visibility = View.GONE
        supportFragmentManager.findFragmentById(binding.albumDetailContainer.id)?.let { frag ->
            supportFragmentManager.beginTransaction().remove(frag).commitAllowingStateLoss()
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
