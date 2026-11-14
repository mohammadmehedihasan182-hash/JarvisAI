package com.doodlejoy.jarvis

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.doodlejoy.jarvis.databinding.ActivityMainBinding
import com.doodlejoy.jarvis.ui.ChatFragment
import com.doodlejoy.jarvis.ui.HomeFragment
import com.doodlejoy.jarvis.ui.ScheduleFragment
import com.doodlejoy.jarvis.ui.SettingsFragment

/**
 * Bottom navigation (spec section 1): হোম, চ্যাট, শিডিউল, সেটিংস.
 * Also asks for all runtime permissions once, up front.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestAllPermissions()

        if (savedInstanceState == null) {
            show(HomeFragment())
        }
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> show(HomeFragment())
                R.id.nav_chat -> show(ChatFragment())
                R.id.nav_schedule -> show(ScheduleFragment())
                R.id.nav_settings -> show(SettingsFragment())
            }
            true
        }

        // Woken by the wake word? Start conversation mode on the Home tab.
        if (intent?.getBooleanExtra(EXTRA_START_CONVERSATION, false) == true) {
            binding.bottomNav.selectedItemId = R.id.nav_home
        }
    }

    private fun show(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    /** Called by HomeFragment after a wake-word wake-up. */
    fun startConversationFromWakeWord() {
        val home = supportFragmentManager.findFragmentById(R.id.fragmentContainer) as? HomeFragment
        home?.startConversationMode()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_START_CONVERSATION, false)) {
            binding.bottomNav.selectedItemId = R.id.nav_home
            startConversationFromWakeWord()
        }
    }

    private fun requestAllPermissions() {
        val wanted = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALENDAR
        )
        if (Build.VERSION.SDK_INT >= 33) wanted.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = wanted.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 42)
        }
    }

    companion object {
        const val EXTRA_START_CONVERSATION = "start_conversation"
    }
}
