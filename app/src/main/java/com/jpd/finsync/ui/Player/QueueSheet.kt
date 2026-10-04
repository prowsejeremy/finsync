package com.jpd.finsync.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.jpd.finsync.R
import com.jpd.finsync.databinding.FragmentQueueSheetBinding
import com.jpd.finsync.databinding.ItemQueueTrackBinding

/** The queue in play order, with the current track in green. Tapping a row jumps to it. */
class QueueSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentQueueSheetBinding? = null
    private val binding get() = _binding!!
    private val playbackViewModel: PlaybackViewModel by activityViewModels()
    private var scrolledToCurrent = false

    override fun getTheme(): Int = R.style.Theme_Finsync_BottomSheet

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQueueSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Let bg_bottom_sheet's rounded corners show instead of Material's default background.
        dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundResource(android.R.color.transparent)

        val adapter = QueueAdapter { index -> playbackViewModel.jumpTo(index) }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        playbackViewModel.state.observe(viewLifecycleOwner) { state ->
            adapter.submit(state.queue, state.currentIndex)
            if (!scrolledToCurrent && state.queue.isNotEmpty()) {
                binding.recyclerView.scrollToPosition(state.currentIndex)
                scrolledToCurrent = true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "QueueSheet"
    }
}

private class QueueAdapter(
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<QueueAdapter.Holder>() {

    private var rows: List<QueueRow> = emptyList()
    private var currentIndex = 0

    fun submit(newRows: List<QueueRow>, newCurrentIndex: Int) {
        rows = newRows
        currentIndex = newCurrentIndex
        // A queue is one album, and the highlight moves on every track change: rebind it all.
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemQueueTrackBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) =
        holder.bind(rows[position], position)

    inner class Holder(private val binding: ItemQueueTrackBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(row: QueueRow, index: Int) {
            val colour = if (index == currentIndex) R.color.accent_green else R.color.text_primary
            binding.tvTitle.text = row.title
            binding.tvTitle.setTextColor(ContextCompat.getColor(binding.root.context, colour))
            binding.tvArtist.text = row.artist
            binding.tvDuration.text = row.durationMs?.let(::formatDuration) ?: ""
            binding.root.setOnClickListener { onClick(index) }
        }
    }
}
