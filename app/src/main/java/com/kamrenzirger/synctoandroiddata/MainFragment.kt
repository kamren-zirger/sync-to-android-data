package com.kamrenzirger.synctoandroiddata
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.kamrenzirger.synctoandroiddata.data.AppDatabase
import com.kamrenzirger.synctoandroiddata.data.SyncEntry
import com.kamrenzirger.synctoandroiddata.databinding.FragmentMainBinding
import com.kamrenzirger.synctoandroiddata.service.SyncAccessibilityService
import com.kamrenzirger.synctoandroiddata.ui.SyncEntryAdapter
import com.kamrenzirger.synctoandroiddata.util.NotificationHelper
import com.kamrenzirger.synctoandroiddata.util.SettingsManager
import com.kamrenzirger.synctoandroiddata.util.ShizukuHelper
import rikka.shizuku.Shizuku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
class MainFragment : Fragment() {
    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SyncEntryAdapter
    private val accessibilityObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            val context = context ?: return
            if (isAccessibilityServiceEnabled(context, SyncAccessibilityService::class.java)) {
                NotificationHelper.dismissNotification(context, NotificationHelper.ID_ACCESSIBILITY_ALERT)
                updateWarningCards()
            }
        }
    }
    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        updateWarningCards()
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        val isGranted = grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (isGranted) {
            NotificationHelper.showNotification(
                requireContext(), 
                getString(R.string.notif_shizuku_authorized_title), 
                getString(R.string.notif_shizuku_authorized_msg)
            )
            updateWarningCards()
        } else {
            NotificationHelper.showNotification(
                requireContext(), 
                getString(R.string.notif_shizuku_denied_title), 
                getString(R.string.notif_shizuku_denied_msg)
            )
        }
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        adapter = SyncEntryAdapter(
            onSyncToggle = { entry, isEnabled ->
                toggleSync(entry, isEnabled)
            },
            onForceSync = { entry, isOpening ->
                forceSync(entry, isOpening)
            },
            onClick = { entry ->
                val bundle = Bundle().apply {
                    putLong("entryId", entry.id)
                }
                findNavController().navigate(R.id.action_MainFragment_to_SyncEditFragment, bundle)
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.fab.setOnClickListener {
            findNavController().navigate(R.id.action_MainFragment_to_SyncEditFragment)
        }
        binding.btnGrantAccessibility.setOnClickListener {
            if (ShizukuHelper.isShizukuAvailable() && ShizukuHelper.checkPermission(0)) {
                lifecycleScope.launch {
                    binding.btnGrantAccessibility.visibility = View.INVISIBLE
                    binding.pbGrantAccessibility.visibility = View.VISIBLE
                    val success = ShizukuHelper.grantAccessibility(requireContext(), SyncAccessibilityService::class.java)
                    binding.pbGrantAccessibility.visibility = View.GONE
                    binding.btnGrantAccessibility.visibility = View.VISIBLE
                    if (success) {
                        updateWarningCards()
                    } else {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                }
            } else {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        observeSyncEntries()
        requireContext().contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            false,
            accessibilityObserver
        )
    }
    override fun onResume() {
        super.onResume()
        if (ShizukuHelper.isShizukuAvailable() && ShizukuHelper.checkPermission(0)) {
            NotificationHelper.dismissNotification(requireContext(), NotificationHelper.ID_SHIZUKU_ALERT)
        }
        updateWarningCards()
    }
    override fun onDestroyView() {
        super.onDestroyView()
        requireContext().contentResolver.unregisterContentObserver(accessibilityObserver)
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        _binding = null
    }
    private fun updateWarningCards() {
        if (_binding == null) return
        val isAccessEnabled = isAccessibilityServiceEnabled(requireContext(), SyncAccessibilityService::class.java)
        binding.cardAccessibilityWarning.visibility = if (isAccessEnabled) View.GONE else View.VISIBLE
        if (isAccessEnabled) {
            NotificationHelper.dismissNotification(requireContext(), NotificationHelper.ID_ACCESSIBILITY_ALERT)
        }
        val isAvailable = ShizukuHelper.isShizukuAvailable()
        val isAuthorized = if (isAvailable) ShizukuHelper.checkPermission(0) else false
        
        when {
            isAvailable && isAuthorized -> {
                binding.cardShizukuWarning.visibility = View.GONE
                NotificationHelper.dismissNotification(requireContext(), NotificationHelper.ID_SHIZUKU_ALERT)
            }
            isAvailable && !isAuthorized -> {
                binding.cardShizukuWarning.visibility = View.VISIBLE
                binding.tvShizukuWarningTitle.text = getString(R.string.shizuku_not_authorized_title)
                binding.tvShizukuWarningDescription.text = getString(R.string.shizuku_not_authorized_description)
                binding.btnShizukuAction.text = getString(R.string.btn_authorize_now)
                binding.btnShizukuAction.setOnClickListener {
                    try {
                        Shizuku.requestPermission(1001)
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), R.string.toast_shizuku_prompt_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            else -> {
                // Not available, check if any known package is installed
                val installedPackage = ShizukuHelper.SHIZUKU_PACKAGES.find { isPackageInstalled(it) }
                
                binding.cardShizukuWarning.visibility = View.VISIBLE
                if (installedPackage != null) {
                    binding.tvShizukuWarningTitle.text = getString(R.string.shizuku_not_running_title)
                    binding.tvShizukuWarningDescription.text = getString(R.string.shizuku_not_running_description)
                    binding.btnShizukuAction.text = getString(R.string.btn_open_shizuku)
                    binding.btnShizukuAction.setOnClickListener {
                        val intent = requireContext().packageManager.getLaunchIntentForPackage(installedPackage)
                        if (intent != null) startActivity(intent)
                    }
                } else {
                    binding.tvShizukuWarningTitle.text = getString(R.string.shizuku_not_installed_title)
                    binding.tvShizukuWarningDescription.text = getString(R.string.shizuku_not_installed_description)
                    binding.btnShizukuAction.text = getString(R.string.btn_get_shizuku)
                    binding.btnShizukuAction.setOnClickListener {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
                    }
                }
            }
        }
    }
    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            requireContext().packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
    private fun isAccessibilityServiceEnabled(context: Context, service: Class<*>): Boolean {
        val expectedComponentName = android.content.ComponentName(context, service)
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledService = android.content.ComponentName.unflattenFromString(componentNameString)
            if (enabledService != null && enabledService == expectedComponentName) return true
        }
        return false
    }
    private fun observeSyncEntries() {
        val context = context ?: return
        val db = AppDatabase.getDatabase(context)
        lifecycleScope.launch {
            db.syncEntryDao().getAllSyncEntriesWithPairs().collect { entries ->
                _binding?.let { b ->
                    adapter.submitList(entries)
                    b.llEmptyState.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }
    private fun toggleSync(entry: SyncEntry, isEnabled: Boolean) {
        val db = AppDatabase.getDatabase(requireContext())
        lifecycleScope.launch(Dispatchers.IO) {
            db.syncEntryDao().updateSyncEntry(entry.copy(isEnabled = isEnabled))
        }
    }
    private fun forceSync(entry: SyncEntry, isOpening: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val settings = SettingsManager(requireContext())
            db.syncEntryDao().getSyncEntryWithPairsById(entry.id).collect { entryWithPairs ->
                if (entryWithPairs == null || entryWithPairs.pairs.isEmpty()) {
                    if (settings.showToasts) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), getString(R.string.toast_no_pairs, entry.appName), Toast.LENGTH_SHORT).show()
                        }
                    }
                    return@collect
                }
                var allSuccess = true
                val directionLabel = if (isOpening) getString(R.string.sync_dir_ext_to_int) else getString(R.string.sync_dir_int_to_ext)
                for (pair in entryWithPairs.pairs) {
                    val rawSrc = if (isOpening) pair.externalPath else pair.internalPath
                    val rawDst = if (isOpening) pair.internalPath else pair.externalPath
                    val src = resolvePath(rawSrc)
                    val dst = resolvePath(rawDst)
                    if (entry.mirrorDeletions) {
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
                    if (!ShizukuHelper.isShizukuAvailable() || !ShizukuHelper.checkPermission(101)) {
                        if (settings.showToasts) {
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.sync_failed_shizuku, entry.appName),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        if (settings.showToasts) {
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.sync_status_toast, emoji, statusStr, directionLabel, entry.appName),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    NotificationHelper.showNotification(
                        requireContext(), 
                        getString(R.string.notif_force_sync_status, statusStr), 
                        if (allSuccess) getString(R.string.notif_success_msg, if (isOpening) "External -> Internal" else "Internal -> External", entry.appName)
                        else getString(R.string.notif_error_msg, entry.appName)
                    )
                }
            }
        }
    }
    private fun resolvePath(path: String): String {
        if (path.startsWith("/")) return path
        val root = android.os.Environment.getExternalStorageDirectory().absolutePath
        return "$root/$path".replace("//", "/")
    }
}
