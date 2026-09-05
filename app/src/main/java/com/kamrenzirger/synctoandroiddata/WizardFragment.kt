package com.kamrenzirger.synctoandroiddata
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.kamrenzirger.synctoandroiddata.databinding.FragmentWizardBinding
import com.kamrenzirger.synctoandroiddata.service.SyncAccessibilityService
import com.kamrenzirger.synctoandroiddata.util.AppLogger
import com.kamrenzirger.synctoandroiddata.util.SettingsManager
import com.kamrenzirger.synctoandroiddata.util.ShizukuHelper
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
class WizardFragment : Fragment() {
    private var _binding: FragmentWizardBinding? = null
    private val binding get() = _binding!!
    private var currentStep = 0
    enum class Step {
        WELCOME, 
        SHIZUKU, 
        NOTIFICATIONS,
        ACCESSIBILITY, 
        BATTERY_OPTIMIZATION,
        HOW_TO_ADD,
        SELECT_APP,
        DIRECTORY_PAIRS,
        SAVE_AND_MANAGE,
        CAUTION,
        FINISH
    }
    private val steps = Step.entries.toTypedArray()
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        updateStep()
    }
    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        updateStep()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        requireActivity().onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
            }
        })
    }
    override fun onPrepareOptionsMenu(menu: android.view.Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.action_settings)?.isVisible = false
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWizardBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        } catch (e: Exception) {
            AppLogger.e("WizardFragment", "Failed to add Shizuku listener", requireContext(), e)
        }
        updateStep()
        binding.btnNext.setOnClickListener {
            if (currentStep < steps.size - 1) {
                currentStep++
                updateStep()
            } else {
                SettingsManager(requireContext()).setupCompleted = true
                findNavController().navigate(R.id.action_WizardFragment_to_MainFragment)
            }
        }
    }
    override fun onResume() {
        super.onResume()
        updateStep() 
    }
    private fun updateStep() {
        if (_binding == null) return
        val step = steps[currentStep]
        binding.btnWizardAction.visibility = View.GONE
        binding.pbWizardAction.visibility = View.GONE
        binding.tvWizardError.visibility = View.GONE
        binding.btnNext.isEnabled = true 
        hideAllMocks()
        when (step) {
            Step.WELCOME -> {
                binding.tvWizardTitle.text = getString(R.string.wizard_welcome_title)
                binding.tvWizardDescription.text = getString(R.string.wizard_welcome_desc)
                binding.btnNext.text = getString(R.string.wizard_btn_start)
            }
            Step.SHIZUKU -> {
                binding.tvWizardTitle.text = getString(R.string.wizard_shizuku_title)
                val isInstalled = isPackageInstalled("moe.shizuku.privileged.api")
                val isAvailable = ShizukuHelper.isShizukuAvailable()
                val isAuthorized = ShizukuHelper.checkPermission(0)
                when {
                    !isInstalled -> {
                        binding.tvWizardDescription.text = getString(R.string.shizuku_not_installed_description)
                        binding.btnWizardAction.visibility = View.VISIBLE
                        binding.btnWizardAction.text = getString(R.string.btn_get_shizuku)
                        binding.btnWizardAction.setOnClickListener {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
                        }
                        binding.btnNext.isEnabled = false
                    }
                    !isAvailable -> {
                        binding.tvWizardDescription.text = getString(R.string.shizuku_not_running_description)
                        binding.btnWizardAction.visibility = View.VISIBLE
                        binding.btnWizardAction.text = getString(R.string.btn_open_shizuku)
                        binding.btnWizardAction.setOnClickListener {
                            val intent = requireContext().packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                            if (intent != null) {
                                startActivity(intent)
                            } else {
                                Toast.makeText(requireContext(), R.string.toast_no_shizuku_app, Toast.LENGTH_SHORT).show()
                            }
                        }
                        binding.btnNext.isEnabled = false
                    }
                    !isAuthorized -> {
                        binding.tvWizardDescription.text = getString(R.string.shizuku_not_authorized_description)
                        binding.btnWizardAction.visibility = View.VISIBLE
                        binding.btnWizardAction.text = getString(R.string.btn_authorize_now)
                        binding.btnWizardAction.setOnClickListener {
                            try {
                                Shizuku.requestPermission(1001)
                            } catch (e: Exception) {
                                AppLogger.e("WizardFragment", "Failed to request Shizuku permission", requireContext(), e)
                                Toast.makeText(requireContext(), R.string.toast_shizuku_prompt_failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                        binding.btnNext.isEnabled = false
                    }
                    else -> {
                        binding.tvWizardDescription.text = "✅ Shizuku is authorized and ready to go!"
                        binding.btnNext.isEnabled = true
                    }
                }
                binding.btnNext.text = getString(R.string.next)
            }
            Step.NOTIFICATIONS -> {
                binding.tvWizardTitle.text = getString(R.string.wizard_notifications_title)
                binding.tvWizardDescription.text = getString(R.string.wizard_notifications_desc)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val status = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                    if (status != PackageManager.PERMISSION_GRANTED) {
                        binding.btnWizardAction.visibility = View.VISIBLE
                        binding.btnWizardAction.text = getString(R.string.wizard_btn_grant)
                        binding.btnWizardAction.setOnClickListener {
                            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    } else {
                        binding.tvWizardDescription.text = getString(R.string.wizard_notifications_granted)
                    }
                }
                binding.btnNext.text = getString(R.string.next)
            }
            Step.ACCESSIBILITY -> {
                binding.tvWizardTitle.text = getString(R.string.wizard_accessibility_title)
                val isEnabled = isAccessibilityServiceEnabled(requireContext(), SyncAccessibilityService::class.java)
                if (!isEnabled) {
                    val fullDesc = getString(R.string.wizard_accessibility_desc) + "\n\n" + getString(R.string.accessibility_enable_steps)
                    binding.tvWizardDescription.text = fullDesc
                    binding.btnWizardAction.visibility = View.VISIBLE
                    binding.btnWizardAction.text = getString(R.string.wizard_btn_grant)
                    binding.btnWizardAction.setOnClickListener {
                        if (ShizukuHelper.isShizukuAvailable() && ShizukuHelper.checkPermission(0)) {
                            lifecycleScope.launch {
                                binding.btnWizardAction.visibility = View.INVISIBLE
                                binding.pbWizardAction.visibility = View.VISIBLE
                                val success = ShizukuHelper.grantAccessibility(requireContext(), SyncAccessibilityService::class.java)
                                binding.pbWizardAction.visibility = View.GONE
                                binding.btnWizardAction.visibility = View.VISIBLE
                                if (success) {
                                    updateStep()
                                } else {
                                    try {
                                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                    } catch (e: Exception) {
                                        AppLogger.e("WizardFragment", "Failed to open accessibility settings", requireContext(), e)
                                        binding.tvWizardError.text = getString(R.string.error_device_not_supported)
                                        binding.tvWizardError.visibility = View.VISIBLE
                                    }
                                }
                            }
                        } else {
                            try {
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            } catch (e: Exception) {
                                AppLogger.e("WizardFragment", "Failed to open accessibility settings", requireContext(), e)
                                binding.tvWizardError.text = getString(R.string.error_device_not_supported)
                                binding.tvWizardError.visibility = View.VISIBLE
                            }
                        }
                    }
                    binding.btnNext.isEnabled = false
                } else {
                    binding.tvWizardDescription.text = getString(R.string.wizard_accessibility_enabled)
                    binding.btnNext.isEnabled = true
                }
                binding.btnNext.text = getString(R.string.next)
            }
            Step.BATTERY_OPTIMIZATION -> {
                binding.tvWizardTitle.text = getString(R.string.wizard_battery_title)
                val pm = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
                val isIgnoring = pm.isIgnoringBatteryOptimizations(requireContext().packageName)
                if (!isIgnoring) {
                    binding.tvWizardDescription.text = getString(R.string.wizard_battery_desc)
                    binding.btnWizardAction.visibility = View.VISIBLE
                    binding.btnWizardAction.text = getString(R.string.wizard_btn_battery_settings)
                    binding.btnWizardAction.setOnClickListener {
                        try {
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(requireContext(), "Could not open battery settings", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    binding.tvWizardDescription.text = getString(R.string.wizard_battery_optimized)
                }
                binding.btnNext.text = getString(R.string.next)
            }
            Step.HOW_TO_ADD -> {
                binding.tvWizardTitle.text = getString(R.string.wizard_how_to_add_title)
                binding.tvWizardDescription.text = getString(R.string.wizard_how_to_add_desc)
                showMock(binding.mockFabLayout.root)
                binding.btnNext.text = getString(R.string.wizard_btn_continue)
            }
            Step.SELECT_APP -> {
                binding.tvWizardTitle.text = getString(R.string.wizard_select_app_title)
                binding.tvWizardDescription.text = getString(R.string.wizard_select_app_desc)
                showMock(binding.mockAppPickerCard)
                binding.btnNext.text = getString(R.string.next)
            }
            Step.DIRECTORY_PAIRS -> {
                binding.tvWizardTitle.text = getString(R.string.wizard_directory_pairs_title)
                binding.tvWizardDescription.text = getString(R.string.wizard_directory_pairs_desc)
                val directoryMock = binding.mockDirectoryPairLayout.root
                directoryMock.findViewById<EditText>(R.id.etExternalPath)?.apply {
                    setText("/storage/emulated/0/Sync/GameSaves")
                    isEnabled = false
                }
                directoryMock.findViewById<EditText>(R.id.etInternalPath)?.apply {
                    setText("/storage/emulated/0/Android/data/com.example.game/files")
                    isEnabled = false
                }
                showMock(directoryMock)
                binding.btnNext.text = getString(R.string.next)
            }
            Step.SAVE_AND_MANAGE -> {
                binding.tvWizardTitle.text = getString(R.string.wizard_save_manage_title)
                binding.tvWizardDescription.text = getString(R.string.wizard_save_manage_desc)
                val itemMock = binding.mockSyncItemLayout.root
                itemMock.findViewById<TextView>(R.id.appName)?.text = getString(R.string.app_name)
                itemMock.findViewById<TextView>(R.id.packageName)?.text = requireContext().packageName
                itemMock.findViewById<ImageView>(R.id.appIcon)?.setImageResource(R.mipmap.ic_launcher)
                itemMock.findViewById<SwitchCompat>(R.id.syncSwitch)?.isChecked = true
                showMock(itemMock)
                binding.btnNext.text = getString(R.string.next)
            }
            Step.CAUTION -> {
                binding.tvWizardTitle.text = getString(R.string.wizard_caution_title)
                binding.tvWizardDescription.text = getString(R.string.wizard_caution_desc)
                binding.btnNext.text = getString(R.string.wizard_btn_understand)
            }
            Step.FINISH -> {
                binding.tvWizardTitle.text = getString(R.string.wizard_finish_title)
                binding.tvWizardDescription.text = getString(R.string.wizard_finish_desc)
                binding.btnNext.text = getString(R.string.wizard_btn_finish)
            }
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
    private fun showMock(mockView: View) {
        mockView.visibility = View.VISIBLE
        disableViewRecursive(mockView)
    }
    private fun disableViewRecursive(view: View) {
        view.isEnabled = false
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                disableViewRecursive(view.getChildAt(i))
            }
        }
    }
    private fun hideAllMocks() {
        binding.mockFabLayout.root.visibility = View.GONE
        binding.mockAppPickerCard.visibility = View.GONE
        binding.mockSyncItemLayout.root.visibility = View.GONE
        binding.mockDirectoryPairLayout.root.visibility = View.GONE
    }
    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            requireContext().packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        } catch (e: Exception) {
            AppLogger.e("WizardFragment", "Failed to remove Shizuku listener", requireContext(), e)
        }
        _binding = null
    }
}
