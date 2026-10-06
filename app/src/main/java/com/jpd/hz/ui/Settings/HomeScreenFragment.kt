package com.jpd.hz.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jpd.hz.R
import com.jpd.hz.databinding.FragmentHomeScreenBinding
import com.jpd.hz.home.HomeCategory
import com.jpd.hz.home.HomeLayoutStore

/**
 * Settings → Home screen (spec "Home screen"): drag a row by its handle to reorder Home, and
 * switch categories on or off. Every change is saved straight away, so there's no view model.
 */
class HomeScreenFragment : Fragment(), HomeScreenAdapter.Listener {

    private var _binding: FragmentHomeScreenBinding? = null
    private val binding get() = _binding!!
    private lateinit var store: HomeLayoutStore
    private lateinit var adapter: HomeScreenAdapter
    private var touchHelper: ItemTouchHelper? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.header.tvTitle.setText(R.string.header_home_screen)
        binding.header.btnBack.setOnClickListener { findNavController().navigateUp() }

        store = HomeLayoutStore(requireContext())
        adapter = HomeScreenAdapter(store.load(), this)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        val helper = ItemTouchHelper(DragCallback())
        helper.attachToRecyclerView(binding.recyclerView)
        touchHelper = helper
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Back mid-drag: detaching calls clearView only for rows already dropped, so the order
        // as it stands is saved here too (spec: every change saves straight away).
        store.save(adapter.layout)
        touchHelper?.attachToRecyclerView(null)
        touchHelper = null
        _binding = null
    }

    override fun onHandleTouched(holder: RecyclerView.ViewHolder) {
        touchHelper?.startDrag(holder)
    }

    override fun onShownChanged(category: HomeCategory, shown: Boolean) {
        adapter.update(adapter.layout.withHidden(category, hidden = !shown))
        saveAndRefresh()
    }

    override fun onMoveRequested(fromIndex: Int, toIndex: Int) {
        if (toIndex !in 0 until adapter.itemCount) return
        adapter.move(fromIndex, toIndex)
        saveAndRefresh()
    }

    private fun saveAndRefresh() {
        store.save(adapter.layout)
        // Posted, so the rebind never lands while the list is laying out or settling a drop.
        binding.recyclerView.post { adapter.refreshRows() }
    }

    // Rows move only from their handle (onHandleTouched); the order saves when one is dropped.
    private inner class DragCallback :
        ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {

        private var orderChanged = false

        override fun isLongPressDragEnabled(): Boolean = false

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val from = viewHolder.adapterPosition
            val to = target.adapterPosition
            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
            adapter.move(from, to)
            orderChanged = true
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

        // Called once the dropped row has settled.
        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            if (!orderChanged) return
            orderChanged = false
            saveAndRefresh()
        }
    }
}
