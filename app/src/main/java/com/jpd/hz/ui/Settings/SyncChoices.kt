package com.jpd.hz.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jpd.hz.databinding.FragmentAlbumSelectionBinding
import com.jpd.hz.databinding.ItemAlbumSelectionBinding

/** A row in a choice screen: an album, a playlist, a book or a folder. */
data class SyncChoice(val id: String, val title: String, val subtitle: String)

/**
 * The choice screens' checklist (3b; adapter harness spec, "Screens"). The choice applies at the
 * next sync: Select saves the ticked IDs and Cancel drops them. Select All ticks every listed ID;
 * what that saves depends on the kind (ChoiceRules.idsToSave).
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
     * Shows the latest choices. The saved selection, [tickedIds], is applied to the first list
     * that isn't empty, so a catalogue refresh that lands mid-edit keeps the user's ticks, and a
     * saved `all` ticks the groups once they've loaded.
     */
    fun submit(newChoices: List<SyncChoice>, tickedIds: (List<String>) -> Set<String>) {
        choices = newChoices
        if (!savedSelectionApplied && newChoices.isNotEmpty()) {
            checkedIds.addAll(tickedIds(newChoices.map { it.id }))
            savedSelectionApplied = true
        }
        render()
    }

    /** The listed choices' IDs, for what Select saves. */
    val listedIds: List<String> get() = choices.map { it.id }

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
