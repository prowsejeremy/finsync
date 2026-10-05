package com.jpd.finsync.ui

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.jpd.finsync.R
import com.jpd.finsync.databinding.ItemHomeScreenCategoryBinding
import com.jpd.finsync.home.HomeCategory
import com.jpd.finsync.home.HomeLayout

// Rebinding with a payload updates a row in place, so the list doesn't cross-fade it.
private const val REFRESH_PAYLOAD = "refresh"

/**
 * The Home screen settings' rows, one per category in Home's order (spec "Home screen"): a drag
 * handle, the icon box, the name and a shown switch. The fragment saves every change.
 */
class HomeScreenAdapter(
    initialLayout: HomeLayout,
    private val listener: Listener
) : RecyclerView.Adapter<HomeScreenAdapter.Holder>() {

    interface Listener {
        /** A row's drag handle was touched: drag that row. */
        fun onHandleTouched(holder: RecyclerView.ViewHolder)

        /** The user flipped a row's switch. */
        fun onShownChanged(category: HomeCategory, shown: Boolean)

        /** TalkBack's Move up or Move down on the row at [fromIndex]. */
        fun onMoveRequested(fromIndex: Int, toIndex: Int)
    }

    /** What the rows show. */
    var layout: HomeLayout = initialLayout
        private set

    /** Takes [newLayout] without redrawing; [refreshRows] redraws. */
    fun update(newLayout: HomeLayout) {
        layout = newLayout
    }

    /** Moves one row, while it's dragged or for TalkBack. */
    fun move(fromIndex: Int, toIndex: Int) {
        layout = layout.moved(fromIndex, toIndex)
        notifyItemMoved(fromIndex, toIndex)
    }

    /** Rebinds every row: switches lock or unlock, and Move up and Move down follow the order. */
    fun refreshRows() {
        notifyItemRangeChanged(0, itemCount, REFRESH_PAYLOAD)
    }

    override fun getItemCount(): Int = layout.order.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        ItemHomeScreenCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) =
        holder.bind(layout.order[position], position)

    inner class Holder(private val binding: ItemHomeScreenCategoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val moveActionIds = mutableListOf<Int>()

        init {
            // Touch-down on the handle starts the drag; long-press drag is off (spec "Reorder").
            binding.ivDragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) listener.onHandleTouched(this)
                false
            }
        }

        fun bind(category: HomeCategory, position: Int) {
            val name = binding.root.resources.getString(category.info.titleRes)
            binding.tvName.text = name
            binding.ivIcon.showCategoryIcon(category)
            // Cleared first, so setting the state here isn't taken for the user flipping it.
            binding.switchShown.setOnCheckedChangeListener(null)
            binding.switchShown.isChecked = category !in layout.hidden
            // The last shown category's switch is disabled (spec "Show and hide").
            binding.switchShown.isEnabled = layout.canHide(category)
            binding.switchShown.contentDescription = name
            binding.switchShown.setOnCheckedChangeListener { _, isChecked ->
                listener.onShownChanged(category, isChecked)
            }
            bindMoveActions(position)
        }

        // TalkBack can't drag, so each row offers Move up and Move down (spec "TalkBack").
        private fun bindMoveActions(position: Int) {
            val row = binding.root
            moveActionIds.forEach { ViewCompat.removeAccessibilityAction(row, it) }
            moveActionIds.clear()
            if (position > 0) {
                moveActionIds += ViewCompat.addAccessibilityAction(
                    row, row.resources.getString(R.string.action_move_up)
                ) { _, _ -> moveBy(-1) }
            }
            if (position < itemCount - 1) {
                moveActionIds += ViewCompat.addAccessibilityAction(
                    row, row.resources.getString(R.string.action_move_down)
                ) { _, _ -> moveBy(1) }
            }
        }

        // The row's place when the action runs, which may differ from when it was bound.
        private fun moveBy(offset: Int): Boolean {
            val from = adapterPosition
            if (from == RecyclerView.NO_POSITION) return false
            listener.onMoveRequested(from, from + offset)
            return true
        }
    }
}
