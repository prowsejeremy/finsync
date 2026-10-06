package com.jpd.hz.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.annotation.IdRes
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.jpd.hz.R
import com.jpd.hz.appearance.Accent
import com.jpd.hz.appearance.ThemeMode
import com.jpd.hz.appearance.applyThemeMode
import com.jpd.hz.appearance.nameRes
import com.jpd.hz.appearance.overlayRes
import com.jpd.hz.databinding.FragmentAppearanceBinding
import com.jpd.hz.databinding.ViewAccentCircleBinding

/**
 * Settings → Appearance (spec "Appearance (new screen)"): Dark, Light or System, and one of five
 * accents. A choice is saved and applied at once: the activity is recreated, the user comes back
 * to this screen, and SettingsViewModel (scoped to settings_graph) survives.
 */
class AppearanceFragment : Fragment() {

    private var _binding: FragmentAppearanceBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by navGraphViewModels(R.id.settings_graph)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppearanceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.header.tvTitle.setText(R.string.settings_appearance_title)
        binding.header.btnBack.setOnClickListener { findNavController().navigateUp() }

        bindThemeMode()
        bindAccents()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Theme ─────────────────────────────────────────────────────────────────

    private fun bindThemeMode() {
        // Checked before the listener is added, so showing the saved mode doesn't save it again.
        binding.toggleThemeMode.check(buttonIdOf(viewModel.themeMode()))
        binding.toggleThemeMode.addOnButtonCheckedListener { _, buttonId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val mode = modeOf(buttonId)
            // Also ignores the call a restored button can make after the redraw.
            if (mode == viewModel.themeMode()) return@addOnButtonCheckedListener
            viewModel.setThemeMode(mode)
            // AppCompat recreates the activity when the mode it shows changes (spec "Changing
            // mode"); on Android 12+ the next launch screen learns it too. System, when the phone
            // already matches, changes nothing on screen.
            requireContext().applyThemeMode(mode)
        }
    }

    @IdRes
    private fun buttonIdOf(mode: ThemeMode): Int = when (mode) {
        ThemeMode.DARK -> R.id.btnModeDark
        ThemeMode.LIGHT -> R.id.btnModeLight
        ThemeMode.SYSTEM -> R.id.btnModeSystem
    }

    private fun modeOf(@IdRes buttonId: Int): ThemeMode = when (buttonId) {
        R.id.btnModeLight -> ThemeMode.LIGHT
        R.id.btnModeSystem -> ThemeMode.SYSTEM
        else -> ThemeMode.DARK
    }

    // ── Accent colour ─────────────────────────────────────────────────────────

    private fun bindAccents() {
        val current = viewModel.accent()
        binding.tvAccentName.text =
            getString(R.string.appearance_accent_name, getString(current.nameRes))
        accentCircles().forEach { (accent, circle) ->
            val name = getString(accent.nameRes)
            val selected = accent == current
            // Each circle shows its accent in the current mode, read through that accent's
            // overlay, so no code names an accent colour (spec "Resources").
            val colour = ContextThemeWrapper(requireContext(), accent.overlayRes).accentColor()
            circle.accentDot.backgroundTintList = ColorStateList.valueOf(colour)
            circle.accentRing.visibility = if (selected) View.VISIBLE else View.INVISIBLE
            circle.root.contentDescription =
                if (selected) getString(R.string.appearance_accent_selected, name) else name
            circle.root.setOnClickListener { chooseAccent(accent) }
            circle.root.announceAsButton()
        }
    }

    // A clickable FrameLayout reads as plain text in TalkBack; this adds "button" after the name.
    private fun View.announceAsButton() {
        ViewCompat.setAccessibilityDelegate(this, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = Button::class.java.name
            }
        })
    }

    private fun chooseAccent(accent: Accent) {
        if (accent == viewModel.accent()) return
        viewModel.setAccent(accent)
        // Each activity applies the overlay in onCreate, so a redraw shows the new accent
        // everywhere (spec "Changing accent").
        requireActivity().recreate()
    }

    private fun accentCircles(): List<Pair<Accent, ViewAccentCircleBinding>> = listOf(
        Accent.GREEN to binding.accentGreen,
        Accent.BLUE to binding.accentBlue,
        Accent.PURPLE to binding.accentPurple,
        Accent.PINK to binding.accentPink,
        Accent.RED to binding.accentRed
    )
}
