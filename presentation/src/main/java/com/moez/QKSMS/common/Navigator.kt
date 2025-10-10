/*
 * Copyright (C) 2017 Moez Bhatti <moez.bhatti@gmail.com>
 *
 * This file is part of QKSMS.
 *
 * QKSMS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * QKSMS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QKSMS.  If not, see <http://www.gnu.org/licenses/>.
 */
package dev.octoshrimpy.quik.common

import android.app.Activity
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import android.provider.Telephony
import dev.octoshrimpy.quik.BuildConfig
import dev.octoshrimpy.quik.compat.TelephonyCompat
import dev.octoshrimpy.quik.extensions.resourceExists
import dev.octoshrimpy.quik.feature.backup.BackupActivity
import dev.octoshrimpy.quik.feature.blocking.BlockingActivity
import dev.octoshrimpy.quik.feature.compose.ComposeActivity
import dev.octoshrimpy.quik.feature.conversationinfo.ConversationInfoActivity
import dev.octoshrimpy.quik.feature.gallery.GalleryActivity
import dev.octoshrimpy.quik.feature.main.MainActivity
import dev.octoshrimpy.quik.feature.notificationprefs.NotificationPrefsActivity
import dev.octoshrimpy.quik.feature.plus.PlusActivity
import dev.octoshrimpy.quik.feature.scheduled.ScheduledActivity
import dev.octoshrimpy.quik.feature.settings.SettingsActivity
import dev.octoshrimpy.quik.manager.BillingManager
import dev.octoshrimpy.quik.manager.NotificationManager
import dev.octoshrimpy.quik.manager.PermissionManager
import dev.octoshrimpy.quik.model.ScheduledMessage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Navigator @Inject constructor(
    private val context: Context,
    private val billingManager: BillingManager,
    private val notificationManager: NotificationManager,
    private val permissions: PermissionManager
) {

    private fun startActivity(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun startActivityExternal(intent: Intent) {
        if (intent.resolveActivity(context.packageManager) != null) {
            startActivity(intent)
        } else {
            startActivity(Intent.createChooser(intent, null))
        }
    }

    /**
     * @param source String to indicate where this QKSMS+ screen was launched from. This should be
     * one of [main_menu, compose_schedule, settings_night, settings_theme]
     */
    fun showQksmsPlusActivity(source: String) {
        val intent = Intent(context, PlusActivity::class.java)
        startActivity(intent)
    }

    /**
     * This won't work unless we use startActivityForResult
     */
    fun showDefaultSmsDialog(context: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java) as RoleManager
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
            context.startActivityForResult(intent, 42389)
        } else {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
            context.startActivity(intent)
        }
    }

    fun showMainActivity() {
        val intent = Intent(context, MainActivity::class.java)
        startActivity(intent)
    }

    fun showCompose(body: String? = null, attachments: List<Uri>? = null, mode: String? = null) {
        val intent = Intent(context, ComposeActivity::class.java)
        intent.putExtra(Intent.EXTRA_TEXT, body)
        intent.putExtra("mode", mode)

        attachments
            ?.takeIf { it.isNotEmpty() }
            ?.mapNotNull {
                if (it.resourceExists(context)) it
                else null
            }
            ?.let { intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(it)) }

        startActivity(intent)
    }

    fun showCompose(scheduledMessage: ScheduledMessage) {
        val scheduledThreadId = TelephonyCompat.getOrCreateThreadId(
            context,
            scheduledMessage.recipients
        )

        val intent = Intent(context, ComposeActivity::class.java)
        intent.putExtra(Intent.EXTRA_TEXT, scheduledMessage.body)
        intent.putExtra("threadId", scheduledThreadId)
        intent.putExtra("subscriptionId", scheduledMessage.subId)
        intent.putExtra("sendAsGroup", scheduledMessage.sendAsGroup)
        intent.putExtra("scheduleDateTime", scheduledMessage.date)

        scheduledMessage.recipients
            .takeIf { it.isNotEmpty() }
            ?.let { intent.putStringArrayListExtra("addresses", ArrayList(it)) }

        scheduledMessage.attachments
            .takeIf { it.isNotEmpty() }
            ?.mapNotNull {
                val uri = Uri.parse(it)
                if (uri.resourceExists(context)) uri
                else null
            }
            ?.let { intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(it)) }

        startActivity(intent)
    }

    fun showConversation(threadId: Long, query: String? = null) {
        val intent = Intent(context, ComposeActivity::class.java)
                .putExtra("threadId", threadId)
                .putExtra("query", query)
        startActivity(intent)
    }

    fun showConversationInfo(threadId: Long) {
        val intent = Intent(context, ConversationInfoActivity::class.java)
        intent.putExtra("threadId", threadId)
        startActivity(intent)
    }

    fun showMedia(partId: Long) {
        val intent = Intent(context, GalleryActivity::class.java)
        intent.putExtra("partId", partId)
        startActivity(intent)
    }

    fun showBackup() {
        startActivity(Intent(context, BackupActivity::class.java))
    }

    fun showScheduled(conversationId: Long?) {
        val intent = Intent(context, ScheduledActivity::class.java)
        conversationId?.let { intent.putExtra("conversationId", it) }
        startActivity(intent)
    }

    fun showSettings() {
        val intent = Intent(context, SettingsActivity::class.java)
        startActivity(intent)
    }

    fun showDeveloper() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/octoshrimpy"))
        startActivityExternal(intent)
    }

    fun showSourceCode() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/octoshrimpy/quik"))
        startActivityExternal(intent)
    }

    fun showChangelog() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/octoshrimpy/quik/releases"))
        startActivityExternal(intent)
    }

    fun showLicense() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/octoshrimpy/quik/blob/master/LICENSE"))
        startActivityExternal(intent)
    }

    fun showBlockedConversations() {
        val intent = Intent(context, BlockingActivity::class.java)
        startActivity(intent)
    }

    fun makePhoneCall(address: String) {
        val action = if (permissions.hasCalling()) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val intent = Intent(action, Uri.parse("tel:$address"))
        startActivityExternal(intent)
    }

    fun showDonation() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/octoshrimpy/quik"))
        startActivityExternal(intent)
    }

    fun showRating() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/octoshrimpy/quik"))
                .addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY
                        or Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                        or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)

        try {
            startActivityExternal(intent)
        } catch (e: ActivityNotFoundException) {
            val url = "https://github.com/octoshrimpy/quik"
            startActivityExternal(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    /**
     * Launch the Play Store and display the Call Blocker listing
     */
    fun installCallBlocker() {
        val url = "https://play.google.com/store/apps/details?id=com.cuiet.blockCalls"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivityExternal(intent)
    }

    /**
     * Launch the Play Store and display the Call Control listing
     */
    fun installCallControl() {
        val url = "https://play.google.com/store/apps/details?id=com.flexaspect.android.everycallcontrol"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivityExternal(intent)
    }

    /**
     * Launch the Play Store and display the Should I Answer? listing
     */
    fun installSia() {
        val url = "https://play.google.com/store/apps/details?id=org.mistergroup.shouldianswer"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivityExternal(intent)
    }

    fun showSupport() {
        val intent = Intent(Intent.ACTION_SENDTO)
        intent.data = Uri.parse("mailto:")
        intent.putExtra(Intent.EXTRA_EMAIL, arrayOf("quik@octo.sh"))
        intent.putExtra(Intent.EXTRA_SUBJECT, "QUIK Support")
        intent.putExtra(Intent.EXTRA_TEXT, StringBuilder("\n\n")
                .append("\n\n--- Please write your message above this line ---\n\n")
                .append("Package: ${context.packageName}\n")
                .append("Version: ${BuildConfig.VERSION_NAME}\n")
                .append("Device: ${Build.BRAND} ${Build.MODEL}\n")
                .append("SDK: ${Build.VERSION.SDK_INT}\n")
                .append("Upgraded"
                        .takeIf { billingManager.upgradeStatus.blockingFirst() } ?: "")
                .toString())
        startActivityExternal(intent)
    }

    fun showInvite() {
        Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "https://github.com/octoshrimpy/quik/releases/latest")
                .let { Intent.createChooser(it, null) }
                .let(::startActivityExternal)
    }

    fun addContact(address: String) {
        val intent = Intent(Intent.ACTION_INSERT)
                .setType(ContactsContract.Contacts.CONTENT_TYPE)
                .putExtra(ContactsContract.Intents.Insert.PHONE, address)

        startActivityExternal(intent)
    }

    fun showContact(lookupKey: String) {
        val intent = Intent(Intent.ACTION_VIEW)
                .setData(Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey))

        startActivityExternal(intent)
    }

    fun viewFile(uri: Uri, mimeType: String) {
        val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mimeType.lowercase())
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .let { Intent.createChooser(it, null) }

        startActivityExternal(intent)
    }

    fun shareFile(uri: Uri, mimeType: String) {
        val intent = Intent(Intent.ACTION_SEND)
                .setType(mimeType.lowercase())
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .let { Intent.createChooser(it, null) }

        startActivityExternal(intent)
    }

    fun showPermissions() {
        val intent = Intent()
        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        intent.data = Uri.fromParts("package", context.packageName, null)

        startActivity(intent)
    }

    fun showNotificationSettings(threadId: Long = 0) {
        val intent = Intent(context, NotificationPrefsActivity::class.java)
        intent.putExtra("threadId", threadId)
        startActivity(intent)
    }

    fun showNotificationChannel(threadId: Long = 0) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (threadId != 0L) {
                notificationManager.createNotificationChannel(threadId)
            }

            val channelId = notificationManager.buildNotificationChannelId(threadId)
            val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            startActivity(intent)
        }
    }

    fun showExactAlarmsSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData(Uri.parse("package:${context.packageName}"))
            startActivity(intent)
        }
    }

}
