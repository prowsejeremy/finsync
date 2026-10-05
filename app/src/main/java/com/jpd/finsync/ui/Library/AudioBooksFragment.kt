package com.jpd.finsync.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jpd.finsync.R
import com.jpd.finsync.databinding.FragmentAudioBooksBinding
import com.jpd.finsync.databinding.ItemBookBinding
import com.jpd.finsync.library.BookStatus
import com.jpd.finsync.library.BookSummary

// The progress bars' scale (android:max in item_book.xml and fragment_book.xml).
const val BOOK_PROGRESS_MAX = 1_000

/** Synced books: in progress first, most recent first, then A–Z (spec "Audio Books"). */
class AudioBooksFragment : Fragment() {

    private var _binding: FragmentAudioBooksBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AudioBooksViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAudioBooksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val backDispatcher = requireActivity().onBackPressedDispatcher
        binding.header.tvTitle.setText(R.string.header_audio_books)
        binding.header.btnBack.setOnClickListener { backDispatcher.onBackPressed() }

        val adapter = BooksAdapter { book -> openBook(book.bookId) }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        viewModel.books.observe(viewLifecycleOwner) { books ->
            adapter.submitList(books)
            binding.emptyState.visibility = if (books.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun openBook(bookId: String) = navigateSafely(
        R.id.audioBooksFragment,
        R.id.action_audio_books_to_book,
        bundleOf(ARG_BOOK_ID to bookId)
    )
}

private class BooksAdapter(
    private val onClick: (BookSummary) -> Unit
) : ListAdapter<BookSummary, BooksAdapter.Holder>(BookDiff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemBookBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemBookBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(book: BookSummary) {
            val context = binding.root.context
            val status = book.status
            binding.tvTitle.text = book.name
            binding.tvAuthor.text = book.author ?: context.getString(R.string.unknown_author)
            binding.tvStatus.text = bookStatusText(context.resources, status)
            // "Finished" is a status, so it stays status_good whatever the accent.
            val statusColour = if (status == BookStatus.Finished) R.color.status_good else R.color.muted
            binding.tvStatus.setTextColor(ContextCompat.getColor(context, statusColour))
            val inProgress = status as? BookStatus.InProgress
            binding.progressBar.visibility = if (inProgress != null) View.VISIBLE else View.GONE
            binding.progressBar.progress = ((inProgress?.fraction ?: 0f) * BOOK_PROGRESS_MAX).toInt()
            loadArtwork(binding.ivCover, book.coverPath)
            binding.root.setOnClickListener { onClick(book) }
        }
    }
}

private object BookDiff : DiffUtil.ItemCallback<BookSummary>() {
    override fun areItemsTheSame(oldItem: BookSummary, newItem: BookSummary) =
        oldItem.bookId == newItem.bookId

    override fun areContentsTheSame(oldItem: BookSummary, newItem: BookSummary) =
        oldItem == newItem
}
