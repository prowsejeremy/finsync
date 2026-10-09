package com.jpd.hz.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jpd.hz.R
import com.jpd.hz.databinding.FragmentAlbumSelectionBinding
import com.jpd.hz.databinding.ItemAlbumSelectionBinding
import com.jpd.hz.library.albumIdsToSave
import com.jpd.hz.model.AlbumSelection

class AlbumSelectionFragment : Fragment() {

    private var _binding: FragmentAlbumSelectionBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by navGraphViewModels(R.id.settings_graph)

    private lateinit var adapter: AlbumAdapter
    // Working copy of which album IDs are selected in this session
    private val checkedIds = mutableSetOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlbumSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.header.tvTitle.setText(R.string.header_settings)
        binding.header.btnBack.setOnClickListener { findNavController().navigateUp() }

        adapter = AlbumAdapter { albumId, isChecked ->
            if (isChecked) checkedIds.add(albumId) else checkedIds.remove(albumId)
            // Re-submit so recycled ViewHolders get the correct checked state on rebind
            val albums = viewModel.albums.value ?: return@AlbumAdapter
            adapter.submitList(albums.map { it.copy(isSelected = checkedIds.contains(it.item.id)) })
            syncSelectAll()
            updateCount()
        }
        binding.rvAlbums.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAlbums.adapter = adapter
        // Select saves nothing until the albums have loaded (spec "Sign-in health", decision 5);
        // after a failed fetch, opening this screen tries again.
        binding.btnSelect.isEnabled = viewModel.albums.value != null
        if (viewModel.albumsFailed.value == true) viewModel.loadAlbums()

        viewModel.albums.observe(viewLifecycleOwner) { albums ->
            val selectedIds = viewModel.getSelectedAlbumIds()
            val isAll = selectedIds.contains("all") || selectedIds.isEmpty()
            checkedIds.clear()
            checkedIds.addAll(
                if (isAll) albums.map { it.item.id }
                else albums.filter { selectedIds.contains(it.item.id) }.map { it.item.id }
            )
            adapter.submitList(albums.map { it.copy(isSelected = checkedIds.contains(it.item.id)) })
            syncSelectAll()
            updateCount()
            binding.btnSelect.isEnabled = true
        }

        binding.rowSelectAll.setOnClickListener {
            val albums = viewModel.albums.value ?: return@setOnClickListener
            if (checkedIds.size == albums.size) {
                checkedIds.clear()
            } else {
                checkedIds.clear()
                checkedIds.addAll(albums.map { it.item.id })
            }
            binding.cbSelectAll.isChecked = checkedIds.size == albums.size
            adapter.submitList(
                (viewModel.albums.value ?: emptyList())
                    .map { it.copy(isSelected = checkedIds.contains(it.item.id)) }
            )
            updateCount()
        }

        binding.btnCancel.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnSelect.setOnClickListener {
            val total = viewModel.albums.value?.size ?: 0
            val ids = albumIdsToSave(checkedIds, total) ?: return@setOnClickListener
            viewModel.setSelectedAlbumIds(ids)
            viewModel.loadAlbums()
            findNavController().popBackStack()
        }
    }

    private fun syncSelectAll() {
        val total = viewModel.albums.value?.size ?: return
        binding.cbSelectAll.isChecked = checkedIds.size == total
    }

    private fun updateCount() {
        val total = viewModel.albums.value?.size ?: 0
        binding.tvSelectionCount.text = "${checkedIds.size}/$total"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    inner class AlbumAdapter(
        private val onToggle: (albumId: String, isChecked: Boolean) -> Unit
    ) : RecyclerView.Adapter<AlbumAdapter.VH>() {

        private var items = listOf<AlbumSelection>()

        fun submitList(list: List<AlbumSelection>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemAlbumSelectionBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(b)
        }

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
        override fun getItemCount() = items.size

        inner class VH(private val b: ItemAlbumSelectionBinding) :
            RecyclerView.ViewHolder(b.root) {

            fun bind(item: AlbumSelection) {
                b.tvAlbumName.text = item.item.name
                b.tvArtistName.text =
                    item.item.albumArtist ?: item.item.artists?.firstOrNull() ?: ""
                b.cbAlbum.isChecked = item.isSelected
                b.root.setOnClickListener {
                    val newChecked = !b.cbAlbum.isChecked
                    b.cbAlbum.isChecked = newChecked
                    onToggle(item.item.id, newChecked)
                }
            }
        }
    }
}
