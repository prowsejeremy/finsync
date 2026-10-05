package com.jpd.finsync.ui

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.jpd.finsync.R
import com.jpd.finsync.databinding.FragmentEqualiserBinding
import com.jpd.finsync.databinding.ItemEqualiserBandBinding
import com.jpd.finsync.databinding.ItemEqualiserPresetBinding
import com.jpd.finsync.equaliser.BAND_COUNT
import com.jpd.finsync.equaliser.EqPreset
import com.jpd.finsync.equaliser.EqSettings
import com.jpd.finsync.equaliser.EqualiserStore

// While off, the presets and bands are faded but still respond (spec "Equaliser").
private const val OFF_ALPHA = 0.4f
private const val ON_ALPHA = 1f

/**
 * The Equaliser (spec "Equaliser"): the switch, the presets and ten band sliders. Each change is
 * saved at once, and PlaybackService hears it through the store. The screen keeps nothing of its
 * own: it reads the store whenever its view is made, so rotation and process death need nothing
 * extra.
 */
class EqualiserFragment : Fragment() {

    private var _binding: FragmentEqualiserBinding? = null
    private val binding get() = _binding!!
    private lateinit var store: EqualiserStore
    private var settings = EqSettings.DEFAULT
    private var presetButtons: List<Pair<EqPreset, MaterialButton>> = emptyList()
    private var bandRows: List<ItemEqualiserBandBinding> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEqualiserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.header.tvTitle.setText(R.string.equaliser_title)
        binding.header.btnBack.setOnClickListener { findNavController().navigateUp() }

        store = EqualiserStore(requireContext())
        settings = store.load()
        bindSwitch()
        bindPresets()
        bindBands()
        render()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        presetButtons = emptyList()
        bandRows = emptyList()
        _binding = null
    }

    private fun bindSwitch() {
        // render() sets the switch too, so only a change from the saved state is the user's.
        binding.switchEqualiser.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != settings.enabled) update(settings.withEnabled(isChecked))
        }
    }

    // One pill per preset, in the presets' order, ending with Custom.
    private fun bindPresets() {
        presetButtons = EqPreset.entries.map { preset ->
            val button =
                ItemEqualiserPresetBinding.inflate(layoutInflater, binding.presets, true).root
            button.setText(preset.titleRes)
            button.setOnClickListener { update(settings.withPreset(preset)) }
            preset to button
        }
    }

    private fun bindBands() {
        bandRows = List(BAND_COUNT) { band ->
            val row = ItemEqualiserBandBinding.inflate(layoutInflater, binding.bands, true)
            bindBand(row, band)
            row
        }
    }

    // Slider's touch listener extends an interface Material marks library-only, which lint flags.
    @SuppressLint("RestrictedApi")
    private fun bindBand(row: ItemEqualiserBandBinding, band: Int) {
        val label = resources.bandLabelText(band)
        row.tvBandLabel.text = label
        // TalkBack reads the slider alone: the band, then the value from the label formatter.
        row.slider.contentDescription = label
        row.slider.setLabelFormatter { value -> row.root.resources.gainText(value) }
        // render() sets the sliders too; only a drag, a tap or TalkBack's adjust is the user's.
        // withBand rounds the value to its tenth.
        row.slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) update(settings.withBand(band, value))
        }
        row.slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            // Touching a slider while off turns the EQ on, even if its value doesn't change.
            override fun onStartTrackingTouch(slider: Slider) {
                if (!settings.enabled) update(settings.withEnabled(true))
            }

            override fun onStopTrackingTouch(slider: Slider) = Unit
        })
    }

    // Saved straight away, so the sound follows a drag step by step (spec "Equaliser").
    private fun update(newSettings: EqSettings) {
        if (newSettings == settings) return
        settings = newSettings
        store.save(newSettings)
        render()
    }

    private fun render() {
        binding.switchEqualiser.isChecked = settings.enabled
        val alpha = if (settings.enabled) ON_ALPHA else OFF_ALPHA
        binding.presetScroll.alpha = alpha
        binding.bandsCard.alpha = alpha
        presetButtons.forEach { (preset, button) -> renderPreset(preset, button) }
        settings.gainsDb.forEachIndexed { band, gainDb -> renderBand(bandRows[band], gainDb) }
    }

    // The selected pill is filled with the accent, as SpeedSheet marks the current speed.
    private fun renderPreset(preset: EqPreset, button: MaterialButton) {
        val selected = preset == settings.preset
        val context = requireContext()
        val background = if (selected) context.accentColor() else color(R.color.surface_2)
        val text = if (selected) context.onAccentColor() else color(R.color.text_primary)
        button.backgroundTintList = ColorStateList.valueOf(background)
        button.setTextColor(text)
        val name = getString(preset.titleRes)
        button.contentDescription =
            if (selected) getString(R.string.equaliser_preset_selected, name) else name
    }

    private fun renderBand(row: ItemEqualiserBandBinding, gainDb: Float) {
        // Setting the value a slider already has does nothing, so a drag isn't disturbed. Saved
        // gains are whole tenths, which the slider's 0.1 step accepts.
        row.slider.value = gainDb
        row.tvBandValue.text = resources.gainText(gainDb)
    }

    private fun color(@ColorRes colorRes: Int) = ContextCompat.getColor(requireContext(), colorRes)
}
