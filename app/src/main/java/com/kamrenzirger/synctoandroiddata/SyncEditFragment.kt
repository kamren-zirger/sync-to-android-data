package com.kamrenzirger.synctoandroiddata
import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.storage.StorageManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.kamrenzirger.synctoandroiddata.data.AppDatabase
import com.kamrenzirger.synctoandroiddata.data.DirectoryPair
import com.kamrenzirger.synctoandroiddata.data.SyncEntry
import com.kamrenzirger.synctoandroiddata.databinding.DialogAppPickerBinding
import com.kamrenzirger.synctoandroiddata.databinding.DialogDirectoryPickerBinding
import com.kamrenzirger.synctoandroiddata.databinding.FragmentSyncEditBinding
import com.kamrenzirger.synctoandroiddata.ui.AppPickerAdapter
import com.kamrenzirger.synctoandroiddata.ui.DirectoryPairAdapter
import com.kamrenzirger.synctoandroiddata.ui.DirectoryPickerAdapter
import com.kamrenzirger.synctoandroiddata.util.NotificationHelper
import com.kamrenzirger.synctoandroiddata.util.SettingsManager
import com.kamrenzirger.synctoandroiddata.util.ShizukuHelper
import com.kamrenzirger.synctoandroiddata.util.StorageUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
class SyncEditFragment : Fragment() {
    private var _binding: FragmentSyncEditBinding? = null
    private val binding get() = _binding!!
    private var selectedPackageName: String? = null
    private var selectedAppName: String? = null
    private var existingEntryId: Long = -1
    private lateinit var pairAdapter: DirectoryPairAdapter
    private var activePairPosition: Int = -1
    private var isPickingInternal: Boolean = true
    private var initialPackageName: String? = null
    private var initialAppName: String? = null
    private var initialPairs: List<DirectoryPair> = emptyList()
    private var initialMirrorDeletions: Boolean = false
    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (hasUnsavedChanges()) {
                showUnsavedChangesDialog()
            } else {
                isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSyncEditBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, onBackPressedCallback)
        pairAdapter = DirectoryPairAdapter(
            onPickInternal = { position ->
                activePairPosition = position
                isPickingInternal = true
                showCustomPicker(position, isInternal = true)
            },
            onPickExternal = { position ->
                activePairPosition = position
                isPickingInternal = false
                showCustomPicker(position, isInternal = false)
            },
            onRemove = { position ->
                pairAdapter.removePair(position)
            }
        )
        binding.rvPairs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPairs.adapter = pairAdapter
        binding.btnSelectApp.setOnClickListener {
            showAppPicker()
        }
        binding.btnAddPair.setOnClickListener {
            pairAdapter.addPair(DirectoryPair(syncEntryId = existingEntryId, internalPath = "", externalPath = ""))
        }
        binding.btnSave.setOnClickListener {
            saveEntry()
        }
        binding.btnDelete.setOnClickListener {
            deleteEntry()
        }
        binding.btnForceInToExt.setOnClickListener {
            triggerForceSync(isOpening = true)
        }
        binding.btnForceExtToIn.setOnClickListener {
            triggerForceSync(isOpening = false)
        }
        arguments?.let {
            existingEntryId = it.getLong("entryId", -1)
            if (existingEntryId != -1L) {
                loadExistingEntry(existingEntryId)
                binding.llActionGroup.visibility = View.VISIBLE
            } else {
                initialPairs = listOf(DirectoryPair(syncEntryId = 0, internalPath = "", externalPath = ""))
                pairAdapter.addPair(initialPairs[0])
            }
        }
    }
    private fun hasUnsavedChanges(): Boolean {
        val currentPairs = pairAdapter.getPairs().map { it.copy(id = 0, syncEntryId = 0) }
        val sanitizedInitialPairs = initialPairs.map { it.copy(id = 0, syncEntryId = 0) }
        return selectedPackageName != initialPackageName ||
                selectedAppName != initialAppName ||
                currentPairs != sanitizedInitialPairs ||
                binding.switchMirrorDeletions.isChecked != initialMirrorDeletions
    }
    private fun showUnsavedChangesDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_unsaved_changes_title)
            .setMessage(R.string.dialog_unsaved_changes_msg)
            .setPositiveButton(R.string.dialog_btn_discard) { _, _ ->
                onBackPressedCallback.isEnabled = false
                findNavController().popBackStack()
            }
            .setNegativeButton(R.string.dialog_btn_keep_editing, null)
            .show()
    }
    private fun showCustomPicker(position: Int, isInternal: Boolean) {
        if (!ShizukuHelper.isShizukuAvailable() || !ShizukuHelper.checkPermission(0)) {
            Toast.makeText(requireContext(), R.string.toast_shizuku_browse_req, Toast.LENGTH_LONG).show()
            return
        }
        val baseDir = "/storage/emulated/0"
        var currentPath = if (isInternal && selectedPackageName != null) {
            "$baseDir/Android/data/$selectedPackageName"
        } else {
            baseDir
        }
        val dialogBinding = DialogDirectoryPickerBinding.inflate(layoutInflater)
        val pickerAdapter = DirectoryPickerAdapter { name, _ ->
            if (name == "..") {
                val file = File(currentPath)
                val parent = file.parent
                if (parent != null && parent.startsWith("/storage/emulated")) {
                    currentPath = parent
                    refreshPicker(currentPath, dialogBinding)
                }
            } else {
                currentPath = if (currentPath.endsWith("/")) "$currentPath$name" else "$currentPath/$name"
                refreshPicker(currentPath, dialogBinding)
            }
        }
        dialogBinding.rvDirectoryPicker.layoutManager = LinearLayoutManager(requireContext())
        dialogBinding.rvDirectoryPicker.adapter = pickerAdapter
        val title = if (isInternal) R.string.picker_internal_title else R.string.edit_hint_external_dir
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.picker_btn_select_current) { _, _ ->
                if (isInternal) {
                    pairAdapter.updateInternalPath(position, currentPath)
                } else {
                    pairAdapter.updateExternalPath(position, currentPath)
                }
            }
            .setNegativeButton(R.string.dialog_btn_cancel, null)
            .create()
        refreshPicker(currentPath, dialogBinding)
        dialog.show()
    }
    private fun refreshPicker(path: String, binding: DialogDirectoryPickerBinding) {
        binding.etCurrentPath.setText(path)
        binding.pbLoadingFiles.visibility = View.VISIBLE
        binding.rvDirectoryPicker.alpha = 0.5f
        val adapter = binding.rvDirectoryPicker.adapter as DirectoryPickerAdapter
        lifecycleScope.launch(Dispatchers.IO) {
            val rawFiles = ShizukuHelper.listFiles(path)
            val items = mutableListOf<String>()
            if (path != "/storage/emulated") {
                items.add("..")
            }
            items.addAll(rawFiles.sortedByDescending { it.endsWith("/") })
            withContext(Dispatchers.Main) {
                adapter.setItems(items)
                binding.pbLoadingFiles.visibility = View.GONE
                binding.rvDirectoryPicker.alpha = 1.0f
            }
        }
    }
    private fun loadExistingEntry(id: Long) {
        val db = AppDatabase.getDatabase(requireContext())
        lifecycleScope.launch {
            db.syncEntryDao().getSyncEntryWithPairsById(id).collect { entryWithPairs ->
                entryWithPairs?.let {
                    updateSelectedAppUi(it.entry.packageName, it.entry.appName)
                    pairAdapter.setPairs(it.pairs)
                    binding.switchMirrorDeletions.isChecked = it.entry.mirrorDeletions
                    initialPackageName = it.entry.packageName
                    initialAppName = it.entry.appName
                    initialPairs = it.pairs.map { p -> p.copy() }
                    initialMirrorDeletions = it.entry.mirrorDeletions
                }
            }
        }
    }
    private fun showAppPicker() {
        val pm = requireContext().packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val sortedPackages = packages.filter { 
            (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        }.sortedBy { pm.getApplicationLabel(it).toString() }
        val dialogBinding = DialogAppPickerBinding.inflate(layoutInflater)
        dialogBinding.pbLoadingApps.visibility = View.VISIBLE
        dialogBinding.rvAppPicker.alpha = 0.5f
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.edit_btn_select_app)
            .setView(dialogBinding.root)
            .create()
        val pickerAdapter = AppPickerAdapter(pm) { app ->
            updateSelectedAppUi(app.packageName, pm.getApplicationLabel(app).toString())
            dialog.dismiss()
        }
        dialogBinding.rvAppPicker.layoutManager = LinearLayoutManager(requireContext())
        dialogBinding.rvAppPicker.adapter = pickerAdapter
        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                pickerAdapter.setApps(sortedPackages)
                dialogBinding.pbLoadingApps.visibility = View.GONE
                dialogBinding.rvAppPicker.alpha = 1.0f
                dialogBinding.etSearchApp.doAfterTextChanged { 
                    pickerAdapter.filter(it?.toString() ?: "")
                }
            }
        }
        dialog.show()
    }
    private fun updateSelectedAppUi(packageName: String, appName: String) {
        selectedPackageName = packageName
        selectedAppName = appName
        binding.tvAppInfo.text = appName
        binding.tvPackageInfo.text = packageName
        try {
            val icon = requireContext().packageManager.getApplicationIcon(packageName)
            binding.ivSelectedAppIcon.visibility = View.VISIBLE
            Glide.with(this).load(icon).into(binding.ivSelectedAppIcon)
        } catch (e: Exception) {
            binding.ivSelectedAppIcon.visibility = View.GONE
        }
    }
    private fun triggerForceSync(isOpening: Boolean) {
        if (existingEntryId == -1L) return
        val appName = selectedAppName ?: "App"
        val pairs = pairAdapter.getPairs()
        val mirrorDeletions = binding.switchMirrorDeletions.isChecked
        val settings = SettingsManager(requireContext())
        lifecycleScope.launch(Dispatchers.IO) {
            var allSuccess = true
            val direction = if (isOpening) "External -> Internal" else "Internal -> External"
            val targetDirLabel = if (isOpening) getString(R.string.edit_hint_internal_dir) else getString(R.string.edit_hint_external_dir)
            for (pair in pairs) {
                val rawSrc = if (isOpening) pair.externalPath else pair.internalPath
                val rawDst = if (isOpening) pair.internalPath else pair.externalPath
                val src = resolvePath(rawSrc)
                val dst = resolvePath(rawDst)
                if (mirrorDeletions) {
                    val mirrorResult = ShizukuHelper.mirrorDeletions(src, dst)
                    if (mirrorResult.isFailure) {
                        allSuccess = false
                    }
                }
                val result = ShizukuHelper.executeCp(src, dst)
                if (!result.isSuccess) {
                    allSuccess = false
                }
            }
            withContext(Dispatchers.Main) {
                val statusStr = if (allSuccess) getString(R.string.sync_status_success) else getString(R.string.sync_status_failed)
                val emoji = if (allSuccess) "✅" else "❌"
                val directionLabel = if (isOpening) getString(R.string.sync_dir_ext_to_int) else getString(R.string.sync_dir_int_to_ext)
                if (!ShizukuHelper.isShizukuAvailable() || !ShizukuHelper.checkPermission(101)) {
                    if (settings.showToasts) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.sync_failed_shizuku, appName),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    if (settings.showToasts) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.sync_status_toast, emoji, statusStr, directionLabel, appName),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                NotificationHelper.showNotification(
                    requireContext(), 
                    getString(R.string.notif_force_sync_status, statusStr), 
                    if (allSuccess) getString(R.string.notif_success_msg, direction, appName)
                    else getString(R.string.notif_error_msg, appName)
                )
            }
        }
    }
    private fun resolvePath(path: String): String {
        if (path.startsWith("/")) return path
        val root = android.os.Environment.getExternalStorageDirectory().absolutePath
        return "$root/$path".replace("//", "/")
    }
    private fun saveEntry() {
        if (selectedPackageName == null || selectedAppName == null) {
            Toast.makeText(requireContext(), R.string.toast_select_app, Toast.LENGTH_SHORT).show()
            return
        }
        val pairs = pairAdapter.getPairs()
        if (pairs.isEmpty() || pairs.any { it.internalPath.isEmpty() || it.externalPath.isEmpty() }) {
            Toast.makeText(requireContext(), R.string.toast_paths_required, Toast.LENGTH_SHORT).show()
            return
        }
        val db = AppDatabase.getDatabase(requireContext())
        lifecycleScope.launch(Dispatchers.IO) {
            val entry = SyncEntry(
                id = if (existingEntryId == -1L) 0 else existingEntryId,
                appName = selectedAppName!!,
                packageName = selectedPackageName!!,
                mirrorDeletions = binding.switchMirrorDeletions.isChecked
            )
            if (existingEntryId == -1L) {
                db.syncEntryDao().insertSyncEntryWithPairs(entry, pairs)
            } else {
                db.syncEntryDao().updateSyncEntryWithPairs(entry, pairs)
            }
            withContext(Dispatchers.Main) {
                onBackPressedCallback.isEnabled = false
                findNavController().popBackStack()
            }
        }
    }
    private fun deleteEntry() {
        if (existingEntryId == -1L) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_delete_entry_title)
            .setMessage(R.string.dialog_delete_entry_msg)
            .setPositiveButton(R.string.dialog_btn_delete) { _, _ ->
                val db = AppDatabase.getDatabase(requireContext())
                lifecycleScope.launch(Dispatchers.IO) {
                    val entry = SyncEntry(id = existingEntryId, appName = "", packageName = "")
                    db.syncEntryDao().deleteSyncEntry(entry)
                    withContext(Dispatchers.Main) {
                        onBackPressedCallback.isEnabled = false
                        findNavController().popBackStack()
                    }
                }
            }
            .setNegativeButton(R.string.dialog_btn_cancel, null)
            .show()
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
