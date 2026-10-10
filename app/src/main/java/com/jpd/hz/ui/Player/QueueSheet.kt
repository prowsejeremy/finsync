package com.jpd.hz.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.jpd.hz.R
import com.jpd.hz.databinding.FragmentQueueSheetBinding
import com.jpd.hz.databinding.ItemQueueTrackBinding
import com.jpd.hz.playback.QueueOrder

/**
 * The queue in play order, with the current track in the accent (queue editing spec). Tapping a
 * row jumps to it, dragging its handle moves it, and swiping it removes it, except the playing
 * row. Each edit goes to the player, which owns the queue.
 */
class QueueSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentQueueSheetBinding? = null
    private val binding get() = _binding!!
    private val playbackViewModel: PlaybackViewModel by activityViewModels()
    private lateinit var adapter: QueueAdapter
    private var scrolledToCurrent = false
    // While a row is dragged the player's updates wait, so the list doesn't change under it.
    private var dragging = false

    override fun getTheme(): Int = R.style.Theme_Hz_BottomSheet

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

        val helper = ItemTouchHelper(QueueTouchCallback())
        adapter = QueueAdapter(
            onClick = { index -> playbackViewModel.jumpTo(index) },
            onHandleTouched = { holder -> helper.startDrag(holder) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        // Never detached: ItemTouchHelper 1.1.0 leaves a settling row animating on detach, then
        // calls clearView with a null RecyclerView, which this Kotlin callback rejects.
        helper.attachToRecyclerView(binding.recyclerView)

        playbackViewModel.state.observe(viewLifecycleOwner) { state ->
            if (!dragging) showQueue(state)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun showQueue(state: PlaybackUiState) {
        adapter.submit(state.queue, state.currentIndex)
        if (!scrolledToCurrent && state.queue.isNotEmpty()) {
            binding.recyclerView.scrollToPosition(state.currentIndex)
            scrolledToCurrent = true
        }
    }

    // Swiping the sheet down to close it would fight a row drag, so it's off during one.
    private fun setSheetDraggable(draggable: Boolean) {
        (dialog as? BottomSheetDialog)?.behavior?.isDraggable = draggable
    }

    // Rows move only from their handle, and the player hears one move on the drop. Any row but
    // the playing one swipes away.
    private inner class QueueTouchCallback : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN,
        ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
    ) {

        private var dragFrom = RecyclerView.NO_POSITION
        private var dragTo = RecyclerView.NO_POSITION

        override fun isLongPressDragEnabled(): Boolean = false

        override fun getSwipeDirs(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int = if (viewHolder.adapterPosition == adapter.currentIndex) {
            0
        } else {
            super.getSwipeDirs(recyclerView, viewHolder)
        }

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                dragging = true
                dragFrom = viewHolder.adapterPosition
                dragTo = dragFrom
                setSheetDraggable(false)
            } else if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
                // The drop: the player hears now, not once the row settles, so a row grabbed or
                // tapped while it settles already means the player's row.
                if (dragFrom != RecyclerView.NO_POSITION && dragTo != dragFrom) {
                    playbackViewModel.moveQueueItem(dragFrom, dragTo)
                }
                dragFrom = RecyclerView.NO_POSITION
                dragTo = RecyclerView.NO_POSITION
            }
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val from = viewHolder.adapterPosition
            val to = target.adapterPosition
            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
            adapter.move(from, to)
            dragTo = to
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val index = viewHolder.adapterPosition
            if (index == RecyclerView.NO_POSITION) return
            adapter.removeAt(index)
            playbackViewModel.removeQueueItem(index)
        }

        // Called once a dropped row has settled, and for a swiped row, which isn't a drag. A row
        // grabbed while another settled keeps updates held until it's dropped too.
        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            if (!dragging || dragFrom != RecyclerView.NO_POSITION) return
            dragging = false
            setSheetDraggable(true)
            // Posted, so the rebind never lands while the list settles the drop. It shows anything
            // that changed while the row was held, such as the next track starting.
            recyclerView.post {
                if (_binding != null && !dragging) playbackViewModel.state.value?.let(::showQueue)
            }
        }
    }

    companion object {
        const val TAG = "QueueSheet"
    }
}

private class QueueAdapter(
    private val onClick: (Int) -> Unit,
    private val onHandleTouched: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<QueueAdapter.Holder>() {

    private val rows = mutableListOf<QueueRow>()
    /** The playing row; drags and removals made here keep it on the same track. */
    var currentIndex = 0
        private set

    fun submit(newRows: List<QueueRow>, newCurrentIndex: Int) {
        // An edit the list already shows comes back from the player as no change; skipping it
        // lets the move or removal animation finish.
        if (newRows == rows && newCurrentIndex == currentIndex) return
        rows.clear()
        rows.addAll(newRows)
        currentIndex = newCurrentIndex
        // The highlight moves on every track change: rebind it all.
        notifyDataSetChanged()
    }

    /** One step of a drag; the player hears of the whole drag on the drop. */
    fun move(from: Int, to: Int) {
        rows.add(to, rows.removeAt(from))
        currentIndex = QueueOrder.positionAfterMove(currentIndex, from, to)
        notifyItemMoved(from, to)
    }

    /** A swiped row, never the playing one. */
    fun removeAt(index: Int) {
        rows.removeAt(index)
        if (index < currentIndex) currentIndex--
        notifyItemRemoved(index)
    }

    override fun getItemCount(): Int = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemQueueTrackBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) =
        holder.bind(rows[position], position)

    inner class Holder(private val binding: ItemQueueTrackBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            // Rows move, so a tap reads the row's position when it lands.
            binding.root.setOnClickListener {
                val index = adapterPosition
                if (index != RecyclerView.NO_POSITION) onClick(index)
            }
            // Touch-down on the handle starts the drag; long-press drag is off. The handle keeps
            // the touch, so a tap on it that never moves isn't taken as the row's tap.
            binding.ivDragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) onHandleTouched(this)
                true
            }
        }

        fun bind(row: QueueRow, index: Int) {
            val context = binding.root.context
            val colour = if (index == currentIndex) {
                context.accentColor()
            } else {
                ContextCompat.getColor(context, R.color.text_primary)
            }
            binding.tvTitle.text = row.title
            binding.tvTitle.setTextColor(colour)
            binding.tvArtist.text = row.artist
            binding.tvDuration.text = row.durationMs?.let(::formatDuration) ?: ""
        }
    }
}
