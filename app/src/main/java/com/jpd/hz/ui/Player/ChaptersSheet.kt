package com.jpd.hz.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.jpd.hz.R
import com.jpd.hz.databinding.FragmentChaptersSheetBinding

/**
 * Every chapter with its start time and length, the current one green. Tapping one jumps to it
 * (spec "Sheets"); the sheet stays open, as the Queue sheet does.
 */
class ChaptersSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentChaptersSheetBinding? = null
    private val binding get() = _binding!!
    private val playbackViewModel: PlaybackViewModel by activityViewModels()
    private var scrolledToCurrent = false

    override fun getTheme(): Int = R.style.Theme_Hz_BottomSheet

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChaptersSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Let bg_bottom_sheet's rounded corners show instead of Material's default background.
        dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundResource(android.R.color.transparent)

        val adapter = ChaptersAdapter(showsStartTimes = true) { row ->
            playbackViewModel.jumpToChapter(row.index)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        playbackViewModel.state.observe(viewLifecycleOwner) { state ->
            // Music took over, so there are no chapters to show.
            val book = state.book ?: run {
                dismiss()
                return@observe
            }
            adapter.setCurrentIndex(book.chapterIndex)
            adapter.submitList(chapterRowsOf(book.chapters, book.durationMs)) {
                if (!scrolledToCurrent) {
                    _binding?.recyclerView?.scrollToPosition(book.chapterIndex)
                    scrolledToCurrent = true
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ChaptersSheet"
    }
}
