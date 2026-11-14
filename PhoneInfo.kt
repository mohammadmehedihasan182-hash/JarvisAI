package com.doodlejoy.jarvis.core

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Phone info (spec section 3): battery, time, storage. */
object PhoneInfo {

    fun batteryPercent(context: Context): Int {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) level * 100 / scale else -1
    }

    fun isCharging(context: Context): Boolean {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    fun nowText(bn: Boolean): String {
        val pattern = if (bn) "hh:mm a, EEEE" else "hh:mm a, EEEE"
        return SimpleDateFormat(pattern, if (bn) Locale("bn", "BD") else Locale.US).format(Date())
    }

    fun storageText(): String {
        val stat = StatFs(Environment.getDataDirectory().path)
        val totalGb = stat.totalBytes / 1_000_000_000.0
        val freeGb = stat.availableBytes / 1_000_000_000.0
        return String.format(Locale.US, "মোট %.1f GB, ফাঁকা %.1f GB", totalGb, freeGb)
    }

    fun summary(context: Context, bn: Boolean): String {
        val battery = batteryPercent(context)
        val charging = if (isCharging(context)) if (bn) " (চার্জ হচ্ছে)" else " (charging)" else ""
        return if (bn) {
            "এখন সময় ${nowText(true)}। ব্যাটারি $battery%$charging। স্টোরেজ: ${storageText()}।"
        } else {
            "It is ${nowText(false)}. Battery is $battery%$charging. Storage: ${storageText()}."
        }
    }
}
