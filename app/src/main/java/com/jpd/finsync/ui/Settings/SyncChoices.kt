package com.jpd.finsync.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jpd.finsync.databinding.FragmentAlbumSelectionBinding
import com.jpd.finsync.databinding.ItemAlbumSelectionBinding

/** A row in Playlists to Sync or Books to Sync. */
data class SyncChoice(val id: String, val title: String, val subtitle: String)

/**
 * Runs Albums to Sync's layout as the Playlists to Sync or Books to Sync checklist (3b). The
 * choice applies at the next sync: Select saves the ticked IDs and Cancel drops them. Select All
 * ticks every listed ID by name, so something added on the server later isn't chosen by itself.
 */
class SyncChoicesController(
    private val binding: FragmentAlbumSelectionBinding,
    private val onSave: (Set<String>) -> Unit,
    private val onClose: () -> Unit
) {
    private val adapter = SyncChoiceAdapter(::toggle)
    private val checkedIds = mutableSetOf<String>()
    private var choices: List<SyncChoice> = emptyList()
    private var savedSelectionApplied = false

    init {
        binding.rvAlbums.layoutManager = LinearLayoutManager(binding.root.context)
        binding.rvAlbums.adapter = adapter
        binding.rowSelectAll.setOnClickListener { toggleAll() }
        binding.btnCancel.setOnClickListener { onClose() }
        binding.btnSelect.setOnClickListener {
            onSave(checkedIds.toSet())
            onClose()
        }
    }

    /**
     * Shows the latest choices. The saved selection is applied only the first time, so a
     * catalogue refresh that lands mid-edit keeps the user's ticks.
     */
    fun submit(newChoices: List<SyncChoice>, savedIds: Set<String>) {
        choices = newChoices
        if (!savedSelectionApplied) {
            checkedIds.addAll(savedIds)
            savedSelectionApplied = true
        }
        render()
    }

    private fun toggle(id: String) {
        if (!checkedIds.remove(id)) checkedIds.add(id)
        render()
    }

    private fun toggleAll() {
        val allChecked = choices.isNotEmpty() && choices.all { it.id in checkedIds }
        if (allChecked) checkedIds.clear() else checkedIds.addAll(choices.map { it.id })
        render()
    }

    private fun render() {
        adapter.submitList(choices.map { SyncChoiceRow(it, it.id in checkedIds) })
        val checkedCount = choices.count { it.id in checkedIds }
        binding.cbSelectAll.isChecked = choices.isNotEmpty() && checkedCount == choices.size
        binding.tvSelectionCount.text = "$checkedCount/${choices.size}"
    }
}

private data class SyncChoiceRow(val choice: SyncChoice, val checked: Boolean)

private class SyncChoiceAdapter(
    private val onToggle: (String) -> Unit
) : ListAdapter<SyncChoiceRow, SyncChoiceAdapter.Holder>(SyncChoiceDiff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        ItemAlbumSelectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemAlbumSelectionBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(row: SyncChoiceRow) {
            binding.tvAlbumName.text = row.choice.title
            binding.tvArtistName.text = row.choice.subtitle
            binding.cbAlbum.isChecked = row.checked
            binding.root.setOnClickListener { onToggle(row.choice.id) }
        }
    }
}

private object SyncChoiceDiff : DiffUtil.ItemCallback<SyncChoiceRow>() {
    override fun areItemsTheSame(oldItem: SyncChoiceRow, newItem: SyncChoiceRow) =
        oldItem.choice.id == newItem.choice.id

    override fun areContentsTheSame(oldItem: SyncChoiceRow, newItem: SyncChoiceRow) =
        oldItem == newItem
}
