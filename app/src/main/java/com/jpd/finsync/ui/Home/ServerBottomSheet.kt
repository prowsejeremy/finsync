package com.jpd.finsync.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.jpd.finsync.R
import com.jpd.finsync.databinding.FragmentServerBottomSheetBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ServerBottomSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentServerBottomSheetBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()

    override fun getTheme(): Int = R.style.Theme_Finsync_BottomSheet

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentServerBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Remove the default Material bottom sheet background so our custom
        // bg_bottom_sheet drawable (rounded top corners, surface_1) shows through.
        val bottomSheetView = dialog?.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        )
        bottomSheetView?.setBackgroundResource(android.R.color.transparent)


        viewModel.config.observe(viewLifecycleOwner) { config ->
            config ?: return@observe
            binding.tvSheetServerName.text = config.serverName
            binding.tvSheetServerUrl.text  = config.serverUrl
            viewModel.checkServerConnection()
        }

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            val statusDot = binding.root.findViewById<View>(R.id.serverStatusDot)
            statusDot?.setBackgroundResource(
                if (state.serverConnected) R.drawable.circle_accent_green else R.drawable.circle_accent_muted
            )
        }

        binding.btnLogout.setOnClickListener {
            viewModel.logout()
            startActivity(
                Intent(requireContext(), LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
            requireActivity().finish()
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ServerBottomSheet"
    }
}
