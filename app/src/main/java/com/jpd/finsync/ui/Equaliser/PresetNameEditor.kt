package com.jpd.finsync.ui

import android.text.InputFilter
import android.view.Window
import android.view.inputmethod.EditorInfo
import androidx.annotation.StringRes
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.jpd.finsync.R
import com.jpd.finsync.databinding.ViewPresetNameEditorBinding
import com.jpd.finsync.equaliser.MAX_PRESET_NAME_LENGTH
import com.jpd.finsync.equaliser.PresetNameCheck
import com.jpd.finsync.equaliser.SavedPreset
import com.jpd.finsync.equaliser.checkPresetName

// The action is drawn faded, and does nothing, while the name isn't allowed.
private const val ACTION_ENABLED_ALPHA = 1f
private const val ACTION_DISABLED_ALPHA = 0.35f

/** What the name editor is for: saving the curve on screen, or renaming a saved preset. */
sealed interface NameJob {
    data object Saving : NameJob
    data class Renaming(val id: Int) : NameJob
}

/**
 * The inline name editor (spec "The name editor"), in the Save button's place while open. It
 * checks the name on every keystroke and hands an allowed one to [onSubmit]; EqualiserFragment
 * changes the settings.
 */
class PresetNameEditor(
    private val binding: ViewPresetNameEditorBinding,
    private val window: Window,
    private val onSubmit: (NameJob, String) -> Unit,
    private val onCancel: () -> Unit
) {

    private val context = binding.root.context
    private val builtInTitles = context.resources.builtInTitles()
    private var savedPresets: List<SavedPreset> = emptyList()

    /** What the open editor is for, or null while it's closed. */
    var job: NameJob? = null
        private set

    init {
        binding.presetNameLayout.counterMaxLength = MAX_PRESET_NAME_LENGTH
        binding.etPresetName.filters =
            arrayOf<InputFilter>(InputFilter.LengthFilter(MAX_PRESET_NAME_LENGTH))
        binding.etPresetName.doAfterTextChanged { renderCheck() }
        binding.etPresetName.setOnEditorActionListener { _, actionId, _ ->
            val done = actionId == EditorInfo.IME_ACTION_DONE
            if (done) submit()
            done
        }
        binding.btnNameAction.setOnClickListener { submit() }
        binding.btnNameCancel.setOnClickListener { onCancel() }
    }

    /**
     * Opens the editor for [job] with [startName] in the field. [showKeyboard] is false when
     * restoring after rotation, so the keyboard doesn't come up unasked.
     */
    fun open(job: NameJob, startName: String, showKeyboard: Boolean) {
        this.job = job
        binding.tvNameEditorTitle.text = when (job) {
            NameJob.Saving -> context.getString(R.string.equaliser_name_title_save)
            is NameJob.Renaming ->
                context.getString(R.string.equaliser_name_title_rename, startName)
        }
        binding.etPresetName.setText(startName)
        binding.etPresetName.setSelection(startName.length)
        binding.root.isVisible = true
        renderCheck()
        if (showKeyboard) showKeyboard()
    }

    fun close() {
        job = null
        keyboard().hide(WindowInsetsCompat.Type.ime())
        binding.etPresetName.clearFocus()
        binding.root.isVisible = false
    }

    /** Checks the name again against [presets], which a delete or an Undo may have changed. */
    fun render(presets: List<SavedPreset>) {
        if (presets == savedPresets) return
        savedPresets = presets
        if (job != null) renderCheck()
    }

    private fun typedName(): String = binding.etPresetName.text?.toString().orEmpty()

    private fun check(): PresetNameCheck = checkPresetName(
        typedName(),
        builtInTitles,
        savedPresets,
        renamingId = (job as? NameJob.Renaming)?.id
    )

    private fun submit() {
        val current = job ?: return
        if (check().allowsSaving) onSubmit(current, typedName())
    }

    // An error for a built-in or taken name, the Replace note, and the action's word. The
    // messages are set only when they change, so TalkBack doesn't repeat them on every keystroke.
    private fun renderCheck() {
        val check = check()
        val error = errorText(check)
        if (binding.presetNameLayout.error?.toString() != error) {
            binding.presetNameLayout.error = error
        }
        val note = (check as? PresetNameCheck.Replaces)
            ?.let { context.getString(R.string.equaliser_name_replaces, it.preset.name) }
        if (binding.presetNameLayout.helperText?.toString() != note) {
            binding.presetNameLayout.helperText = note
        }
        binding.btnNameAction.setText(actionText(check))
        binding.btnNameAction.isEnabled = check.allowsSaving
        binding.btnNameAction.alpha =
            if (check.allowsSaving) ACTION_ENABLED_ALPHA else ACTION_DISABLED_ALPHA
    }

    private fun errorText(check: PresetNameCheck): String? = when (check) {
        is PresetNameCheck.BuiltIn ->
            context.getString(R.string.equaliser_name_built_in, check.title)
        is PresetNameCheck.Taken ->
            context.getString(R.string.equaliser_name_taken, check.preset.name)
        else -> null
    }

    @StringRes
    private fun actionText(check: PresetNameCheck): Int = when {
        job is NameJob.Renaming -> R.string.equaliser_rename
        check is PresetNameCheck.Replaces -> R.string.equaliser_replace
        else -> R.string.equaliser_save
    }

    // Posted, so the field is laid out and can take focus before the keyboard is asked for.
    private fun showKeyboard() {
        val field = binding.etPresetName
        field.post {
            field.requestFocus()
            keyboard().show(WindowInsetsCompat.Type.ime())
        }
    }

    private fun keyboard() = WindowCompat.getInsetsController(window, binding.etPresetName)
}
