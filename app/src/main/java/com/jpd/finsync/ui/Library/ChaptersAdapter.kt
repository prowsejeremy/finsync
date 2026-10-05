package com.jpd.finsync.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jpd.finsync.R
import com.jpd.finsync.databinding.ItemChapterBinding
import com.jpd.finsync.library.Chapter
import com.jpd.finsync.library.chapterLengthsMs

/** One chapter row: its number (index + 1), name, start and length. */
data class ChapterRow(val index: Int, val name: String, val startMs: Long, val lengthMs: Long)

/** The rows for a book's chapters; the last runs to the end of the book. */
fun chapterRowsOf(chapters: List<Chapter>, durationMs: Long): List<ChapterRow> {
    val lengths = chapterLengthsMs(chapters.map { it.startMs }, durationMs)
    return chapters.mapIndexed { index, chapter ->
        ChapterRow(index, chapter.name, chapter.startMs, lengths[index])
    }
}

/**
 * The book page's and the Chapters sheet's rows: number, name and length, plus the start time in
 * the sheet. The current chapter is green (spec).
 */
class ChaptersAdapter(
    private val showsStartTimes: Boolean,
    private val onChapterClick: (ChapterRow) -> Unit
) : ListAdapter<ChapterRow, ChaptersAdapter.Holder>(ChapterDiff) {

    private var currentIndex: Int? = null

    fun setCurrentIndex(index: Int?) {
        if (index == currentIndex) return
        val previous = currentIndex
        currentIndex = index
        // Only the two rows that change are rebound; rows not in the list yet bind green later.
        previous?.takeIf { it < itemCount }?.let { notifyItemChanged(it) }
        index?.takeIf { it < itemCount }?.let { notifyItemChanged(it) }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemChapterBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemChapterBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(row: ChapterRow) {
            val context = binding.root.context
            val current = row.index == currentIndex
            val accent = context.accentColor()
            val nameColour =
                if (current) accent else ContextCompat.getColor(context, R.color.text_primary)
            val numberColour =
                if (current) accent else ContextCompat.getColor(context, R.color.muted)
            binding.tvNumber.text = (row.index + 1).toString()
            binding.tvNumber.setTextColor(numberColour)
            binding.tvName.text = row.name
            binding.tvName.setTextColor(nameColour)
            binding.tvStart.text = formatDuration(row.startMs)
            binding.tvStart.visibility = if (showsStartTimes) View.VISIBLE else View.GONE
            binding.tvLength.text = formatDuration(row.lengthMs)
            binding.root.setOnClickListener { onChapterClick(row) }
        }
    }
}

private object ChapterDiff : DiffUtil.ItemCallback<ChapterRow>() {
    override fun areItemsTheSame(oldItem: ChapterRow, newItem: ChapterRow) =
        oldItem.index == newItem.index

    override fun areContentsTheSame(oldItem: ChapterRow, newItem: ChapterRow) = oldItem == newItem
}
