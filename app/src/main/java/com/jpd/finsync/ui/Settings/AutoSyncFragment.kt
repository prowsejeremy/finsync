package com.jpd.finsync.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.jpd.finsync.R
import com.jpd.finsync.databinding.FragmentAutoSyncBinding

class AutoSyncFragment : Fragment() {

    private var _binding: FragmentAutoSyncBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by activityViewModels()

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

        // Pre-select the current interval
        val current = viewModel.getAutoSyncInterval()
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
            parentFragmentManager.popBackStack()
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
            parentFragmentManager.popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
