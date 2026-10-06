package com.jpd.hz.ui

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import com.jpd.hz.R
import com.jpd.hz.databinding.ItemEqualiserPresetBinding
import com.jpd.hz.databinding.ViewEqualiserPresetsBinding
import com.jpd.hz.equaliser.EqChoice
import com.jpd.hz.equaliser.EqPreset
import com.jpd.hz.equaliser.EqSettings
import com.jpd.hz.equaliser.SavedPreset

// The chevron points down while the list is closed, and turns to point up while it's open.
private const val CHEVRON_CLOSED_DEGREES = 0f
private const val CHEVRON_OPEN_DEGREES = 180f
private const val CHEVRON_TURN_MS = 150L

/**
 * The Preset card (spec "The preset card"): a header naming the choice, which opens a list of the
 * built-in presets, the user's presets and Custom. It draws the settings it's given and passes
 * taps on; EqualiserFragment changes the settings.
 */
class PresetList(
    private val binding: ViewEqualiserPresetsBinding,
    private val onToggle: () -> Unit,
    private val onChoose: (EqChoice) -> Unit,
    private val onRename: (SavedPreset) -> Unit,
    private val onDelete: (SavedPreset) -> Unit
) {

    /** A row: what it chooses, the name it shows, and its views. */
    private class Row(
        val choice: EqChoice,
        val name: String,
        val views: ItemEqualiserPresetBinding
    )

    private val context = binding.root.context
    private val inflater = LayoutInflater.from(context)
    private val regularFont: Typeface? = ResourcesCompat.getFont(context, R.font.hk_regular)
    private val boldFont: Typeface? = ResourcesCompat.getFont(context, R.font.hk_extrabold)
    private val fixedRows: List<Row>
    private var savedRows: List<Row> = emptyList()
    // The presets the saved rows show; null until the first render.
    private var shownPresets: List<SavedPreset>? = null
    private var title = ""

    /** Whether the list is showing below the header. */
    var isOpen = false
        private set

    init {
        binding.presetHeader.setOnClickListener { onToggle() }
        val builtIns = EqPreset.entries.filter { it != EqPreset.CUSTOM }
            .map { preset -> fixedRow(preset, binding.builtInRows) }
        fixedRows = builtIns + fixedRow(EqPreset.CUSTOM, binding.customRow)
    }

    /** Draws [settings]: the header's [title], the user's presets, and the chosen row's mark. */
    fun render(settings: EqSettings, title: String) {
        this.title = title
        binding.tvPresetChoice.text = title
        showSavedPresets(settings.savedPresets)
        (fixedRows + savedRows).forEach { row -> renderRow(row, row.choice == settings.choice) }
        renderHeaderDescription()
    }

    /** Opens or closes the list. [animate] turns the chevron; a restore after rotation doesn't. */
    fun setOpen(open: Boolean, animate: Boolean) {
        isOpen = open
        binding.presetOptions.isVisible = open
        val degrees = if (open) CHEVRON_OPEN_DEGREES else CHEVRON_CLOSED_DEGREES
        if (animate) {
            binding.ivPresetChevron.animate().rotation(degrees).setDuration(CHEVRON_TURN_MS)
        } else {
            binding.ivPresetChevron.rotation = degrees
        }
        renderHeaderDescription()
    }

    private fun fixedRow(preset: EqPreset, parent: ViewGroup): Row =
        addRow(EqChoice.Preset(preset), context.getString(preset.titleRes), parent)

    // Every row chooses on a tap; only the user's presets get ⋮.
    private fun addRow(choice: EqChoice, name: String, parent: ViewGroup): Row {
        val views = ItemEqualiserPresetBinding.inflate(inflater, parent, true)
        views.tvPresetName.text = name
        views.root.setOnClickListener { onChoose(choice) }
        return Row(choice, name, views)
    }

    // Rebuilt only when the user's presets change, not on every slider step.
    private fun showSavedPresets(presets: List<SavedPreset>) {
        if (presets == shownPresets) return
        shownPresets = presets
        binding.savedRows.removeAllViews()
        savedRows = presets.map { preset ->
            addRow(EqChoice.Saved(preset.id), preset.name, binding.savedRows)
                .also { row -> bindOptions(row.views, preset) }
        }
        binding.tvYourPresets.isVisible = presets.isNotEmpty()
    }

    private fun bindOptions(views: ItemEqualiserPresetBinding, preset: SavedPreset) {
        views.btnPresetOptions.isVisible = true
        views.btnPresetOptions.contentDescription =
            context.getString(R.string.cd_equaliser_preset_options, preset.name)
        views.btnPresetOptions.setOnClickListener { anchor -> showOptions(anchor, preset) }
    }

    // ⋮'s menu: Rename and Delete.
    private fun showOptions(anchor: View, preset: SavedPreset) {
        val menu = PopupMenu(context, anchor)
        menu.inflate(R.menu.menu_equaliser_preset)
        menu.setOnMenuItemClickListener { item ->
            val action = when (item.itemId) {
                R.id.action_rename_preset -> onRename
                R.id.action_delete_preset -> onDelete
                else -> null
            }
            action?.invoke(preset)
            action != null
        }
        menu.show()
    }

    // The chosen row is marked and bold; TalkBack hears ", selected", as with the old pills.
    private fun renderRow(row: Row, selected: Boolean) {
        row.views.radio.isChecked = selected
        row.views.tvPresetName.typeface = if (selected) boldFont else regularFont
        row.views.root.contentDescription = if (selected) {
            context.getString(R.string.equaliser_preset_selected, row.name)
        } else {
            row.name
        }
    }

    // "Preset, Bass boost", with "Expanded" or "Collapsed" as the header's state.
    private fun renderHeaderDescription() {
        binding.presetHeader.contentDescription =
            context.getString(R.string.cd_equaliser_preset, title)
        val state =
            if (isOpen) R.string.cd_equaliser_list_expanded else R.string.cd_equaliser_list_collapsed
        ViewCompat.setStateDescription(binding.presetHeader, context.getString(state))
    }
}
