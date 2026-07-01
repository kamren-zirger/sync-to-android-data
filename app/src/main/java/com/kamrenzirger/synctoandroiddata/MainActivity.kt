package com.kamrenzirger.synctoandroiddata
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.kamrenzirger.synctoandroiddata.databinding.ActivityMainBinding
import com.kamrenzirger.synctoandroiddata.service.PermissionActionReceiver
import com.kamrenzirger.synctoandroiddata.service.SyncAccessibilityService
import com.kamrenzirger.synctoandroiddata.util.NotificationHelper
import com.kamrenzirger.synctoandroiddata.util.SettingsManager
import com.kamrenzirger.synctoandroiddata.util.ShizukuHelper
class MainActivity : AppCompatActivity() {
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private val accessibilityObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            if (isAccessibilityServiceEnabled(this@MainActivity, SyncAccessibilityService::class.java)) {
                NotificationHelper.dismissNotification(this@MainActivity, NotificationHelper.ID_ACCESSIBILITY_ALERT)
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        navController = navHostFragment.navController
        val settings = SettingsManager(this)
        if (!settings.setupCompleted) {
            navController.navigate(R.id.WizardFragment)
        } else {
            checkPermissionsOnStartup()
        }
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.WizardFragment) {
                binding.appBarLayout.visibility = View.GONE
            } else {
                binding.appBarLayout.visibility = View.VISIBLE
            }
        }
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            false,
            accessibilityObserver
        )
    }
    override fun onResume() {
        super.onResume()
        if (ShizukuHelper.isShizukuAvailable() && ShizukuHelper.checkPermission(0)) {
            NotificationHelper.dismissNotification(this, NotificationHelper.ID_SHIZUKU_ALERT)
        }
        if (isAccessibilityServiceEnabled(this, SyncAccessibilityService::class.java)) {
            NotificationHelper.dismissNotification(this, NotificationHelper.ID_ACCESSIBILITY_ALERT)
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(accessibilityObserver)
    }
    private fun checkPermissionsOnStartup() {
        val isShizukuInstalled = try {
            packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (e: Exception) {
            false
        }
        val isShizukuAvailable = ShizukuHelper.isShizukuAvailable()
        val isShizukuAuthorized = ShizukuHelper.checkPermission(0)
        if (!isShizukuInstalled || !isShizukuAvailable || !isShizukuAuthorized) {
            val shizukuIntent = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api") 
                ?: Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://shizuku.rikka.app/"))
            val (title, message, action) = when {
                !isShizukuInstalled -> Triple(
                    getString(R.string.shizuku_not_installed_title),
                    getString(R.string.shizuku_not_installed_description),
                    getString(R.string.btn_get_shizuku)
                )
                !isShizukuAvailable -> Triple(
                    getString(R.string.shizuku_not_running_title),
                    getString(R.string.shizuku_not_running_description),
                    getString(R.string.btn_open_shizuku)
                )
                else -> Triple(
                    getString(R.string.shizuku_not_authorized_title),
                    getString(R.string.shizuku_not_authorized_description),
                    getString(R.string.btn_authorize_now)
                )
            }
            NotificationHelper.showPermissionAlert(
                this,
                NotificationHelper.ID_SHIZUKU_ALERT,
                title,
                message,
                action,
                shizukuIntent
            )
        }
        if (!isAccessibilityServiceEnabled(this, SyncAccessibilityService::class.java)) {
            val title = getString(R.string.accessibility_disabled_title)
            val message = getString(R.string.accessibility_disabled_description)
            val actionText = getString(R.string.btn_grant_permission)
            val accessIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            val broadcastIntent = Intent(this, PermissionActionReceiver::class.java).apply {
                action = PermissionActionReceiver.ACTION_GRANT_ACCESSIBILITY
                putExtra("notificationId", NotificationHelper.ID_ACCESSIBILITY_ALERT)
                putExtra("title", title)
                putExtra("message", message)
                putExtra("actionText", actionText)
                putExtra("actionIntent", accessIntent)
            }
            NotificationHelper.showPermissionAlert(
                this,
                NotificationHelper.ID_ACCESSIBILITY_ALERT,
                title,
                message,
                actionText,
                broadcastIntent,
                isBroadcast = true
            )
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
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            R.id.action_settings -> {
                val currentId = navController.currentDestination?.id
                if (currentId == R.id.MainFragment) {
                    navController.navigate(R.id.action_global_SettingsFragment)
                } else if (currentId == R.id.SyncEditFragment) {
                    navController.navigate(R.id.action_global_SettingsFragment_from_edit)
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}
