package com.jpd.finsync.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.jpd.finsync.R
import com.jpd.finsync.databinding.FragmentAlbumSelectionBinding

/** Books to Sync: Albums to Sync's checklist over the server's audiobooks, with sizes (3b). */
class BookSelectionFragment : Fragment() {

    private var _binding: FragmentAlbumSelectionBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by navGraphViewModels(R.id.settings_graph)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlbumSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.header.tvTitle.setText(R.string.header_settings)
        binding.header.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.tvSelectionTitle.setText(R.string.selection_title_books)

        val choices = SyncChoicesController(
            binding,
            onSave = viewModel::setSelectedBookIds,
            onClose = { findNavController().popBackStack() }
        )
        viewModel.refreshCatalogueOnce()
        viewModel.bookChoices.observe(viewLifecycleOwner) { books ->
            val rows = books.map { book ->
                SyncChoice(
                    id = book.bookId,
                    title = book.name,
                    // "Andy Weir · 412 MB": book rows show the file size (spec).
                    subtitle = joinWithDots(
                        listOf(
                            book.author ?: getString(R.string.unknown_author),
                            book.sizeBytes?.let(::formatFileSize)
                        )
                    )
                )
            }
            choices.submit(rows, viewModel.getSelectedBookIds())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
