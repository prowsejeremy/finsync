package com.jpd.finsync.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.jpd.finsync.R
import com.jpd.finsync.databinding.FragmentSpeedSheetBinding
import com.jpd.finsync.playback.BOOK_SPEEDS

/**
 * The six speeds, the current one in accent (spec "Sheets"). A tap sets the speed for every book
 * and closes the sheet.
 */
class SpeedSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentSpeedSheetBinding? = null
    private val binding get() = _binding!!
    private val playbackViewModel: PlaybackViewModel by activityViewModels()

    override fun getTheme(): Int = R.style.Theme_Finsync_BottomSheet

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSpeedSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Let bg_bottom_sheet's rounded corners show instead of Material's default background.
        dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundResource(android.R.color.transparent)

        val choices = listOf(
            binding.btnSpeed1, binding.btnSpeed2, binding.btnSpeed3,
            binding.btnSpeed4, binding.btnSpeed5, binding.btnSpeed6
        ).zip(BOOK_SPEEDS)
        choices.forEach { (button, speed) ->
            button.text = formatSpeed(speed)
            button.setOnClickListener {
                playbackViewModel.setSpeed(speed)
                dismiss()
            }
        }

        playbackViewModel.state.observe(viewLifecycleOwner) { state ->
            val book = state.book ?: run {
                dismiss()
                return@observe
            }
            // The player snaps the speed to a step, so an exact match marks the current one.
            choices.forEach { (button, speed) -> style(button, selected = speed == book.speed) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun style(button: MaterialButton, selected: Boolean) {
        val background = if (selected) R.color.accent_green else R.color.surface_2
        val text = if (selected) R.color.bg_primary else R.color.text_primary
        button.backgroundTintList = ColorStateList.valueOf(color(background))
        button.setTextColor(color(text))
    }

    private fun color(@ColorRes colorRes: Int) = ContextCompat.getColor(requireContext(), colorRes)

    companion object {
        const val TAG = "SpeedSheet"
    }
}
