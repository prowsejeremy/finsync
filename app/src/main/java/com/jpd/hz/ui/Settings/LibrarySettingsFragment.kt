package com.jpd.hz.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jpd.hz.R
import com.jpd.hz.adapter.folders.AdapterFolder
import com.jpd.hz.adapter.folders.LibraryChange
import com.jpd.hz.adapter.folders.LibraryChangePlan
import com.jpd.hz.adapter.folders.PlannedFolder
import com.jpd.hz.databinding.FragmentLibrarySettingsBinding
import com.jpd.hz.library.scan.ScanState
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "LibrarySettings"

/**
 * Settings → Library (spec "Settings → Library"): the Library folder with its Change button, which
 * uses the folder picker that was the Sync screen's, and the scan card with Rescan.
 */
class LibrarySettingsFragment : Fragment() {

    private var _binding: FragmentLibrarySettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LibrarySettingsViewModel by navGraphViewModels(R.id.settings_graph)

    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? -> if (uri != null) onFolderPicked(uri) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibrarySettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.header.tvTitle.setText(R.string.settings_library_title)
        binding.header.btnBack.setOnClickListener { findNavController().navigateUp() }

        viewModel.folder.observe(viewLifecycleOwner) { binding.tvLibraryFolder.text = it }
        binding.btnChangeFolder.setOnClickListener {
            folderPicker.launch(Uri.fromFile(File(viewModel.folderPath())))
        }
        viewModel.scanState.observe(viewLifecycleOwner) { renderScan(it) }
        binding.btnRescan.setOnClickListener { viewModel.rescan() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.changes.collect { reportChange(it) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun renderScan(state: ScanState) {
        binding.tvScanStatus.text = resources.scanStatusLine(state, viewModel::displayPathOf)
        binding.btnRescan.isEnabled = state !is ScanState.Scanning
    }

    private fun onFolderPicked(uri: Uri) {
        requireContext().contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        val path = uriToPath(uri)
        if (path == null) {
            showMessage(getString(R.string.library_unreadable_picked))
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            when (val plan = viewModel.plan(path)) {
                LibraryChangePlan.Same -> Unit
                LibraryChangePlan.Unreadable -> showMessage(
                    getString(R.string.home_library_unreadable, viewModel.displayPathOf(path))
                )
                LibraryChangePlan.Refused -> showMessage(getString(R.string.library_refused))
                is LibraryChangePlan.NotFound -> showMessage(
                    getString(
                        R.string.library_not_found,
                        nameOf(plan.folder),
                        viewModel.displayPathOf(path)
                    )
                )
                is LibraryChangePlan.Ready -> when {
                    plan.movesFiles -> confirmMove(path, plan)
                    plan.found != null -> confirmFound(path, plan.found!!)
                    else -> viewModel.change(path)
                }
            }
        }
    }

    // "kurage's files will move to …", or "Files from kurage, home will move to …" (spec
    // "Changing the Library folder"; adapter harness spec H9.3).
    private fun confirmMove(path: String, plan: LibraryChangePlan.Ready) {
        val moving = plan.folders.filter { it.rename }
        val into = viewModel.displayPathOf(path)
        val message = if (moving.size == 1) {
            getString(R.string.library_move_confirm, nameOf(moving.single().folder), into)
        } else {
            val names = moving.joinToString { nameOf(it.folder) }
            getString(R.string.library_move_confirm_many, names, into)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(message)
            .setPositiveButton(R.string.library_move) { _, _ -> viewModel.change(path) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // A folder found by searching is the connection's from now on, and sync's cleanup works
    // there, so the user says so first (A4).
    private fun confirmFound(path: String, found: PlannedFolder) {
        val name = nameOf(found.folder)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(
                getString(R.string.library_found_title, name, viewModel.displayPathOf(found.target))
            )
            .setMessage(getString(R.string.library_found_confirm, name))
            .setPositiveButton(R.string.library_use_folder) { _, _ -> viewModel.change(path) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun reportChange(change: LibraryChange) {
        val message = when (change) {
            is LibraryChange.Changed, LibraryChange.Unchanged -> return
            LibraryChange.Busy -> getString(R.string.library_busy)
            LibraryChange.MoveFailed -> getString(R.string.library_move_failed)
            LibraryChange.MoveUnfinished -> getString(R.string.library_move_unfinished)
            LibraryChange.Refused -> getString(R.string.library_refused)
            is LibraryChange.NotFound ->
                getString(R.string.library_not_found_any, nameOf(change.folder))
            LibraryChange.Unreadable -> getString(R.string.library_unreadable_picked)
        }
        showMessage(message)
    }

    // A connection's folder is named after it ("kurage"), signed in or not.
    private fun nameOf(folder: AdapterFolder): String = File(folder.path).name

    private fun showMessage(message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // Moved from SyncSettingsFragment: a document-tree URI as a filesystem path, when its storage
    // volume is recognisable.
    private fun uriToPath(uri: Uri): String? = try {
        val docId = DocumentFile.fromTreeUri(requireContext(), uri)?.uri?.lastPathSegment
        docId?.let {
            val parts = it.split(":")
            if (parts.size == 2) {
                val (volume, rel) = parts
                if (volume == "primary") "/storage/emulated/0/$rel" else "/storage/$volume/$rel"
            } else null
        }
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't turn $uri into a path", e)
        null
    }
}
