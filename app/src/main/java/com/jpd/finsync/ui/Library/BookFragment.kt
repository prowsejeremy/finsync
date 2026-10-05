package com.jpd.finsync.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.jpd.finsync.R
import com.jpd.finsync.databinding.FragmentBookBinding
import com.jpd.finsync.library.BookDetail
import com.jpd.finsync.library.BookStatus

/** Navigation argument naming the book to show (string, required). */
const val ARG_BOOK_ID = "bookId"

/** A book's page: progress, Resume or Play, Start over, and its chapters (spec "Book"). */
class BookFragment : Fragment() {

    private var _binding: FragmentBookBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BookViewModel by viewModels()
    private val playbackViewModel: PlaybackViewModel by activityViewModels()
    private lateinit var adapter: ChaptersAdapter
    private var scrolledToCurrent = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBookBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val backDispatcher = requireActivity().onBackPressedDispatcher
        binding.header.btnBack.setOnClickListener { backDispatcher.onBackPressed() }
        val bookId = viewModel.bookId

        adapter = ChaptersAdapter(showsStartTimes = false) { row ->
            playbackViewModel.playBookFrom(bookId, row.startMs)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.btnResume.setOnClickListener { playbackViewModel.playBook(bookId) }
        binding.btnStartOver.setOnClickListener { playbackViewModel.startBookOver(bookId) }

        viewModel.book.observe(viewLifecycleOwner) { render(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        scrolledToCurrent = false
    }

    private fun render(book: BookDetail?) {
        binding.content.visibility = if (book != null) View.VISIBLE else View.GONE
        binding.tvNotDownloaded.visibility = if (book == null) View.VISIBLE else View.GONE
        if (book == null) return

        val chapterCount = book.chapters.size
        binding.tvTitle.text = book.name
        binding.tvAuthor.text = book.author ?: getString(R.string.unknown_author)
        // "16 h 20 min · 32 chapters"
        binding.tvMeta.text = joinWithDots(
            listOf(
                formatListLength(resources, listOf(book.durationMs)),
                resources.getQuantityString(R.plurals.chapter_count, chapterCount, chapterCount)
            )
        )
        loadArtwork(binding.ivCover, book.coverPath)
        renderProgress(book.status)

        // The green chapter follows the saved place (decision 18).
        val current = (book.status as? BookStatus.InProgress)?.let { it.chapterNumber - 1 }
        adapter.setCurrentIndex(current)
        adapter.submitList(chapterRowsOf(book.chapters, book.durationMs)) {
            // "The page opens scrolled to the current chapter" (spec), once per visit.
            if (!scrolledToCurrent && current != null) {
                _binding?.recyclerView?.scrollToPosition(current)
                scrolledToCurrent = true
            }
        }
    }

    // Resume while in progress; Play when not started or finished (spec "Buttons").
    private fun renderProgress(status: BookStatus) {
        val inProgress = status as? BookStatus.InProgress
        binding.progressBar.visibility = if (inProgress != null) View.VISIBLE else View.GONE
        binding.progressBar.progress = ((inProgress?.fraction ?: 0f) * BOOK_PROGRESS_MAX).toInt()
        binding.tvProgress.text = bookStatusText(resources, status)
        val colour = if (status == BookStatus.Finished) R.color.status_good else R.color.muted
        binding.tvProgress.setTextColor(ContextCompat.getColor(requireContext(), colour))
        binding.btnResume.setText(if (inProgress != null) R.string.btn_resume else R.string.btn_play)
    }
}
