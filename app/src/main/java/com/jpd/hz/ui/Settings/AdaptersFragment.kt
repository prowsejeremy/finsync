package com.jpd.hz.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.jpd.hz.R
import com.jpd.hz.adapter.Connection
import com.jpd.hz.adapter.Platform
import com.jpd.hz.adapter.Platforms
import com.jpd.hz.adapter.folders.ConnectionFolders
import com.jpd.hz.databinding.FragmentAdaptersBinding
import com.jpd.hz.databinding.ViewSettingsRowBinding
import com.jpd.hz.library.LibraryFolderStore

/**
 * Settings → Adapters (spec "Screens"): one row per installed platform, "Jellyfin" over
 * "kurage · Synced · 1,400 of 1,400 songs" or "Not signed in". Each row opens its platform's page.
 */
class AdaptersFragment : Fragment() {

    private var _binding: FragmentAdaptersBinding? = null
    private val binding get() = _binding!!

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
        Platforms.all.forEach(::addRow)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun addRow(platform: Platform) {
        val row = ViewSettingsRowBinding.inflate(layoutInflater, binding.adapterRows, false)
        row.ivRowIcon.setImageResource(R.drawable.ic_server)
        row.tvRowTitle.text = platform.name
        showAdapterSummary(platform, before = { listOfNotNull(it?.let(::folderName)) }) {
            row.tvRowSummary.text = it
        }
        row.root.setOnClickListener {
            navigateSafely(
                R.id.adaptersFragment,
                R.id.action_adapters_to_connection,
                ConnectionFragment.argsOf(platform.key)
            )
        }
        binding.adapterRows.addView(row.root)
    }

    // "kurage": where the connection syncs, named from the Library folder (T3). Only Settings →
    // Library moves it, and coming back makes a new view.
    private fun folderName(connection: Connection): String? {
        val platform = Platforms.find(connection.platform) ?: return null
        val folder = ConnectionFolders.folderFor(requireContext(), connection, platform.name)
        return LibraryFolderStore(requireContext()).nameInLibrary(folder.path)
    }
}
