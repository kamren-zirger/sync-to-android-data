package com.kamrenzirger.synctoandroiddata
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.kamrenzirger.synctoandroiddata.databinding.FragmentSettingsBinding
import com.kamrenzirger.synctoandroiddata.util.AppLogger
import com.kamrenzirger.synctoandroiddata.util.SettingsManager

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }
    override fun onPrepareOptionsMenu(menu: android.view.Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.action_settings)?.isVisible = false
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val settings = SettingsManager(requireContext())
        binding.tvAppVersion.text = getString(R.string.settings_version_format, BuildConfig.VERSION_NAME)
        binding.switchStartOnBoot.isChecked = settings.startOnBoot
        binding.switchStartOnBoot.setOnCheckedChangeListener { _, isChecked ->
            settings.startOnBoot = isChecked
        }
        binding.switchShowToasts.isChecked = settings.showToasts
        binding.switchShowToasts.setOnCheckedChangeListener { _, isChecked ->
            settings.showToasts = isChecked
        }
        binding.switchEnableLogging.isChecked = settings.enableLogging
        binding.switchEnableLogging.setOnCheckedChangeListener { _, isChecked ->
            settings.enableLogging = isChecked
            if (!isChecked) {
                AppLogger.clear()
            }
        }
        binding.btnCopyLogs.setOnClickListener {
            val logs = AppLogger.getFormattedLogs()
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("SyncToAndroidData Logs", logs)
            clipboard.setPrimaryClip(clip)
        }
        binding.btnNotificationSettings.setOnClickListener {
            val intent = android.content.Intent().apply {
                action = android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
            }
            startActivity(intent)
        }
        binding.btnGitHubRepo.setOnClickListener {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse(getString(R.string.settings_github_url))
            }
            startActivity(intent)
        }
        binding.btnDonateKofi.setOnClickListener {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse(getString(R.string.settings_kofi_url))
            }
            startActivity(intent)
        }
        binding.btnRerunWizard.setOnClickListener {
            settings.setupCompleted = false
            findNavController().navigate(R.id.WizardFragment)
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
