package com.jpd.hz.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.jpd.hz.R
import com.jpd.hz.databinding.FragmentAutoSyncBinding

/** A connection's Auto-sync: its own schedule (adapter harness spec, "Running connections"). */
class AutoSyncFragment : Fragment() {

    private var _binding: FragmentAutoSyncBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ConnectionViewModel by navGraphViewModels(R.id.connection_graph)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAutoSyncBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.header.tvTitle.setText(R.string.settings_auto_sync_title)
        binding.header.btnBack.setOnClickListener { findNavController().navigateUp() }

        // Pre-select the current interval
        val current = viewModel.autoSyncInterval()
        binding.radioGroupAutoSync.check(
            when (current) {
                "1"  -> R.id.rbEvery1h
                "6"  -> R.id.rbEvery6h
                "12" -> R.id.rbEvery12h
                "24" -> R.id.rbEvery24h
                else -> R.id.rbDisabled
            }
        )

        binding.btnCancel.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnApply.setOnClickListener {
            val interval = when (binding.radioGroupAutoSync.checkedRadioButtonId) {
                R.id.rbEvery1h  -> "1"
                R.id.rbEvery6h  -> "6"
                R.id.rbEvery12h -> "12"
                R.id.rbEvery24h -> "24"
                else            -> "disabled"
            }
            viewModel.setAutoSyncInterval(interval)
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
