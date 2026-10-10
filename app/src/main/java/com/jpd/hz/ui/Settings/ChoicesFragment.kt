package com.jpd.hz.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.jpd.hz.R
import com.jpd.hz.adapter.ChoiceKind
import com.jpd.hz.adapter.choices.ChoiceRules
import com.jpd.hz.databinding.FragmentAlbumSelectionBinding

private const val ARG_KIND = "kind"

/**
 * One kind's choice screen (spec "Screens"): Albums, Playlists, Books or Folders to Sync, from the
 * stored catalogue, with Select all and Cancel / Select. It replaces the three screens before the
 * harness.
 */
class ChoicesFragment : Fragment() {

    private var _binding: FragmentAlbumSelectionBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ConnectionViewModel by navGraphViewModels(R.id.connection_graph)

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
        val key = requireArguments().getString(ARG_KIND).orEmpty()
        val kind = checkNotNull(ChoiceKind.fromKey(key)) { "No choice kind $key" }
        binding.header.tvTitle.setText(R.string.header_settings)
        binding.header.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.tvSelectionTitle.setText(kind.selectionTitleRes)

        lateinit var choices: SyncChoicesController
        choices = SyncChoicesController(
            binding,
            onSave = { checked -> viewModel.saveChosen(kind, checked, choices.listedIds) },
            onClose = { findNavController().popBackStack() }
        )
        viewModel.refreshCatalogueOnce()
        viewModel.choices(kind).observe(viewLifecycleOwner) { groups ->
            val rows = groups.map { group ->
                SyncChoice(
                    id = group.groupId,
                    title = group.name,
                    subtitle = resources.choiceDetail(kind, group)
                )
            }
            val saved = viewModel.chosen(kind)
            choices.submit(rows) { listed -> ChoiceRules.tickedOf(saved, listed) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun argsOf(kind: ChoiceKind): Bundle = bundleOf(ARG_KIND to kind.key)
    }
}
