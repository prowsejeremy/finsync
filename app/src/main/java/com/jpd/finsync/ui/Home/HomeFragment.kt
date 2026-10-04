package com.jpd.finsync.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.jpd.finsync.R
import com.jpd.finsync.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnSettings.setOnClickListener {
            navigateSafely(R.id.homeFragment, R.id.action_home_to_settings)
        }
        viewModel.uiState.observe(viewLifecycleOwner) { renderSyncRing(SyncDisplay.from(it)) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun renderSyncRing(display: SyncDisplay) {
        if (display.status == SyncDisplay.Status.SYNCING) {
            binding.syncRing.showSyncProgress(display.progress)
        } else {
            binding.syncRing.hideSyncProgress()
        }
    }
}
