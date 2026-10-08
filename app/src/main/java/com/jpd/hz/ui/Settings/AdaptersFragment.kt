package com.jpd.hz.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.asLiveData
import androidx.navigation.fragment.findNavController
import com.jpd.hz.R
import com.jpd.hz.databinding.FragmentAdaptersBinding
import com.jpd.hz.databinding.ViewSettingsRowBinding

/**
 * Settings → Adapters (spec "Settings → Adapters"): one row per adapter, "Jellyfin" over
 * "kurage · Synced · 1,400 of 1,400 songs" or "Not signed in". Each row opens its adapter's page.
 */
class AdaptersFragment : Fragment() {

    private var _binding: FragmentAdaptersBinding? = null
    private val binding get() = _binding!!
    private val mainViewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdaptersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.header.tvTitle.setText(R.string.settings_adapters_title)
        binding.header.btnBack.setOnClickListener { findNavController().navigateUp() }
        adapterPages().forEach { (adapter, actionId) -> addRow(adapter, actionId) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Each adapter and the page its row opens: a list, not a registry, until a second exists
    // (D12).
    private fun adapterPages(): List<Pair<Adapter, Int>> =
        listOf(mainViewModel.jellyfin to R.id.action_adapters_to_jellyfin)

    private fun addRow(adapter: Adapter, @IdRes actionId: Int) {
        val row = ViewSettingsRowBinding.inflate(layoutInflater, binding.adapterRows, false)
        row.ivRowIcon.setImageResource(R.drawable.ic_server)
        row.tvRowTitle.text = adapter.name
        // Read once per view: only Settings → Library moves it, and coming back makes a new view.
        val folder = adapter.folder()
        adapter.status.asLiveData().observe(viewLifecycleOwner) { status ->
            row.tvRowSummary.text = requireContext().adapterSummary(status, listOfNotNull(folder))
        }
        row.root.setOnClickListener { navigateSafely(R.id.adaptersFragment, actionId) }
        binding.adapterRows.addView(row.root)
    }
}
