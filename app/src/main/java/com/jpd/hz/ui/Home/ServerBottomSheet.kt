package com.jpd.hz.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.navGraphViewModels
import com.jpd.hz.R
import com.jpd.hz.databinding.FragmentServerBottomSheetBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

private const val DETAIL_SEPARATOR = " · "

/**
 * A connection's details, opened from its page's server pill (spec "Screens"): its name, the
 * platform's detail lines (a server's address and user) and Sign out.
 */
class ServerBottomSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentServerBottomSheetBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ConnectionViewModel by navGraphViewModels(R.id.connection_graph)

    override fun getTheme(): Int = R.style.Theme_Hz_BottomSheet

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

        viewModel.connection.observe(viewLifecycleOwner) { connection ->
            connection ?: return@observe
            binding.tvSheetServerName.text = connection.name
            binding.tvSheetServerUrl.text = connection.details.joinToString(DETAIL_SEPARATOR)
            viewModel.checkAvailability()
        }

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            binding.serverStatusDot.setBackgroundResource(
                if (state.serverConnected && !state.signInRefused) {
                    R.drawable.circle_status_good
                } else {
                    R.drawable.circle_accent_muted
                }
            )
        }

        // The page stays open and shows its Sign in card. Playback, the queue and book progress
        // stay (D10).
        binding.btnLogout.setOnClickListener {
            viewModel.signOut()
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
