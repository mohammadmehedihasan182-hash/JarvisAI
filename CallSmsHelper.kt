package com.doodlejoy.jarvis.core

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

/**
 * Calls & SMS (spec section 3).
 * SECURITY RULE: nothing in this file is ever called directly by the brain.
 * The brain only ASKS "কল করব?" and calls these functions after the user says "হ্যাঁ".
 */
object CallSmsHelper {

    /** Find a phone number from the contacts by (partial) name. */
    fun findNumberByName(context: Context, name: String): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) return null
        val uri = Uri.withAppendedPath(
            ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(name)
        )
        context.contentResolver.query(uri, arrayOf(ContactsContract.Contacts._ID), null, null, null)
            ?.use { c ->
                if (!c.moveToFirst()) return null
                val id = c.getString(0)
                context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                    arrayOf(id), null
                )?.use { p -> if (p.moveToFirst()) return p.getString(0) }
            }
        return null
    }

    /** Display name for an incoming number (null = unknown number). */
    fun nameForNumber(context: Context, number: String): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) return null
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)
        )
        context.contentResolver.query(
            uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
        )?.use { c -> if (c.moveToFirst()) return c.getString(0) }
        return null
    }

    /** Start the call. Requires CALL_PHONE permission (checked by caller UI). */
    fun call(context: Context, number: String) {
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /** Send an SMS directly (used only after an explicit "হ্যাঁ"). */
    fun sendSms(context: Context, number: String, message: String): Boolean {
        return try {
            val sms = context.getSystemService(SmsManager::class.java)
            sms.sendTextMessage(number, null, message, null, null)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Open another installed app by package name / known app name. */
    fun openApp(context: Context, appQuery: String): String? {
        val pm = context.packageManager
        val known = mapOf(
            "youtube" to "com.google.android.youtube", "ইউটিউব" to "com.google.android.youtube",
            "facebook" to "com.facebook.katana", "ফেসবুক" to "com.facebook.katana",
            "whatsapp" to "com.whatsapp", "হোয়াটসঅ্যাপ" to "com.whatsapp",
            "gmail" to "com.google.android.gm", "জিমেইল" to "com.google.android.gm",
            "chrome" to "com.android.chrome", "ক্রোম" to "com.android.chrome",
            "camera" to "com.android.camera", "ক্যামেরা" to "com.android.camera",
            "maps" to "com.google.android.apps.maps", "ম্যাপ" to "com.google.android.apps.maps"
        )
        val pkg = known[appQuery.lowercase().trim()]
            ?: pm.getInstalledApplications(0).firstOrNull {
                pm.getApplicationLabel(it).toString().contains(appQuery, ignoreCase = true)
            }?.packageName
            ?: return null
        val intent = pm.getLaunchIntentForPackage(pkg) ?: return null
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
        return pkg
    }

    /** Popular app names JARVIS understands for "X খোলো" commands. */
    val knownApps = listOf("YouTube", "Facebook", "WhatsApp", "Gmail", "Chrome", "Camera", "Maps")
}
