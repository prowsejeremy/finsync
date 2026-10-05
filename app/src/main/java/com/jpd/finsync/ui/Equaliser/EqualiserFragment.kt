package com.jpd.finsync.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import com.jpd.finsync.R
import com.jpd.finsync.databinding.FragmentEqualiserBinding
import com.jpd.finsync.databinding.ItemEqualiserBandBinding
import com.jpd.finsync.equaliser.BAND_COUNT
import com.jpd.finsync.equaliser.EqChoice
import com.jpd.finsync.equaliser.EqSettings
import com.jpd.finsync.equaliser.EqualiserStore
import com.jpd.finsync.equaliser.SavedPreset

// While off, the bands, the Preset card and the Save button are faded but still respond (spec
// "While the EQ is off").
private const val OFF_ALPHA = 0.4f
private const val ON_ALPHA = 1f

// Only the open list and the name editor exist on the screen alone, so they're kept across
// rotation here; the settings come from the store (spec "Screen state").
private const val STATE_LIST_OPEN = "preset_list_open"
private const val STATE_NAME_JOB = "preset_name_job"
// Saved preset ids start at 1, so STATE_NAME_JOB holds 0 while saving and the id while renaming.
private const val SAVING_JOB_ID = 0

/**
 * The Equaliser (spec "Screen"): the switch, ten band sliders, the Preset card and saving
 * presets. Each change is saved at once, and PlaybackService hears it through the store. The
 * settings are read from the store whenever the view is made.
 */
class EqualiserFragment : Fragment() {

    private var _binding: FragmentEqualiserBinding? = null
    private val binding get() = _binding!!
    private var _presetList: PresetList? = null
    private val presetList get() = _presetList!!
    private var _nameEditor: PresetNameEditor? = null
    private val nameEditor get() = _nameEditor!!
    private lateinit var store: EqualiserStore
    private var settings = EqSettings.DEFAULT
    private var bandRows: List<ItemEqualiserBandBinding> = emptyList()
    private var undoBar: Snackbar? = null

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
        bindBands()
        bindPresets()
        savedInstanceState?.let(::restoreScreenState)
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // This also runs while the screen is on the back stack, with no view.
        val list = _presetList ?: return
        outState.putBoolean(STATE_LIST_OPEN, list.isOpen)
        when (val job = _nameEditor?.job) {
            NameJob.Saving -> outState.putInt(STATE_NAME_JOB, SAVING_JOB_ID)
            is NameJob.Renaming -> outState.putInt(STATE_NAME_JOB, job.id)
            null -> Unit
        }
    }

    override fun onDestroyView() {
        // The field going doesn't take the keyboard with it, so it would stay up over the
        // screen underneath. Rotation has already saved the open editor by now.
        if (_nameEditor?.job != null) nameEditor.close()
        super.onDestroyView()
        // Leaving or rotating ends the chance to undo; the delete stands.
        undoBar?.dismiss()
        undoBar = null
        bandRows = emptyList()
        _presetList = null
        _nameEditor = null
        _binding = null
    }

    private fun bindSwitch() {
        // render() sets the switch too, so only a change from the saved state is the user's.
        binding.switchEqualiser.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != settings.enabled) update(settings.withEnabled(isChecked))
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

    private fun bindPresets() {
        _presetList = PresetList(
            binding.presetCard,
            onToggle = { setListOpen(!presetList.isOpen) },
            onChoose = ::choose,
            onRename = ::startRename,
            onDelete = ::delete
        )
        _nameEditor = PresetNameEditor(
            binding.nameEditor,
            requireActivity().window,
            onSubmit = ::submitName,
            onCancel = ::closeEditor
        )
        binding.btnSavePreset.setOnClickListener { openEditor(NameJob.Saving, startName = "") }
    }

    // The list and the editor as they were. The editor's field restores its own text afterwards,
    // in onViewStateRestored.
    private fun restoreScreenState(state: Bundle) {
        presetList.setOpen(state.getBoolean(STATE_LIST_OPEN), animate = false)
        if (!state.containsKey(STATE_NAME_JOB)) return
        val jobId = state.getInt(STATE_NAME_JOB)
        if (jobId == SAVING_JOB_ID) {
            nameEditor.open(NameJob.Saving, startName = "", showKeyboard = false)
            return
        }
        val preset = settings.savedPreset(jobId) ?: return
        nameEditor.open(NameJob.Renaming(preset.id), preset.name, showKeyboard = false)
    }

    private fun setListOpen(open: Boolean) {
        if (open == presetList.isOpen) return
        presetList.setOpen(open, animate = true)
        // The open list has its height only after the next layout.
        if (open) binding.presetCard.root.doOnNextLayout { revealPresetList() }
    }

    // If the open list ends below the screen, scroll down to it, but never past the card's top
    // (spec "The preset card").
    private fun revealPresetList() {
        val views = _binding ?: return
        val card = views.presetCard.root
        val target = minOf(card.top, card.bottom - views.scroll.height)
        if (target > views.scroll.scrollY) views.scroll.smoothScrollTo(0, target)
    }

    // Choosing closes the list. Saving belongs to Custom, so choosing anything else ends it.
    private fun choose(choice: EqChoice) {
        setListOpen(false)
        if (nameEditor.job == NameJob.Saving && choice != EqChoice.CUSTOM) nameEditor.close()
        update(settings.withChoice(choice))
    }

    // Renaming closes the list, so the editor below it is in view.
    private fun startRename(preset: SavedPreset) {
        setListOpen(false)
        openEditor(NameJob.Renaming(preset.id), startName = preset.name)
    }

    private fun openEditor(job: NameJob, startName: String) {
        nameEditor.open(job, startName, showKeyboard = true)
        render()
    }

    private fun closeEditor() {
        nameEditor.close()
        render()
    }

    private fun submitName(job: NameJob, name: String) {
        nameEditor.close()
        when (job) {
            NameJob.Saving -> update(settings.savedAs(name))
            is NameJob.Renaming -> update(settings.withRenamed(job.id, name))
        }
    }

    private fun delete(preset: SavedPreset) {
        val before = settings
        if (nameEditor.job == NameJob.Renaming(preset.id)) nameEditor.close()
        update(settings.withDeleted(preset.id))
        showUndo(preset, before)
    }

    // Undo puts back exactly what was there before the delete (spec "Delete and Undo"). Material 3
    // colours a Snackbar with inverse roles the theme doesn't set, so the colours are set here.
    private fun showUndo(preset: SavedPreset, before: EqSettings) {
        val message = getString(R.string.equaliser_deleted, preset.name)
        val bar = Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction(R.string.equaliser_undo) { update(before) }
            .setBackgroundTint(color(R.color.text_primary))
            .setTextColor(color(R.color.bg_primary))
            .setActionTextColor(color(R.color.bg_primary))
        bar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_action)
            ?.typeface = ResourcesCompat.getFont(requireContext(), R.font.hk_extrabold)
        undoBar = bar
        bar.show()
    }

    // Saved straight away, so the sound follows a drag step by step. Any change ends the chance
    // to undo a delete, so Undo never reverses more than the delete.
    private fun update(newSettings: EqSettings) {
        if (newSettings != settings) {
            undoBar?.dismiss()
            undoBar = null
            settings = newSettings
            store.save(newSettings)
        }
        render()
    }

    private fun render() {
        binding.switchEqualiser.isChecked = settings.enabled
        val alpha = if (settings.enabled) ON_ALPHA else OFF_ALPHA
        binding.bandsCard.alpha = alpha
        binding.presetCard.root.alpha = alpha
        binding.btnSavePreset.alpha = alpha
        settings.gainsDb.forEachIndexed { band, gainDb -> renderBand(bandRows[band], gainDb) }
        presetList.render(settings, resources.choiceTitle(settings))
        nameEditor.render(settings.savedPresets)
        // The Save button belongs to Custom, and the open editor takes its place.
        binding.btnSavePreset.isVisible =
            settings.choice == EqChoice.CUSTOM && nameEditor.job == null
    }

    private fun renderBand(row: ItemEqualiserBandBinding, gainDb: Float) {
        // Setting the value a slider already has does nothing, so a drag isn't disturbed. Saved
        // gains are whole tenths, which the slider's 0.1 step accepts.
        row.slider.value = gainDb
        row.tvBandValue.text = resources.gainText(gainDb)
    }

    private fun color(@ColorRes colorRes: Int) = ContextCompat.getColor(requireContext(), colorRes)
}
