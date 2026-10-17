package dev.dreamerslab.firebase

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.SystemClock

internal class Notifications(private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    val enabled: Boolean
        get() = manager.areNotificationsEnabled()

    fun createChannel(name: String, importance: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, name, importance))
        }
    }

    fun show(title: String, body: String, data: Map<String, String>, icon: Icon?) {
        val id = SystemClock.elapsedRealtime().toInt()
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        builder.setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(context, id, openIntent(data), PENDING_INTENT_FLAGS))
        if (icon == null) builder.setSmallIcon(context.applicationInfo.icon) else builder.setSmallIcon(icon)
        manager.notify(id, builder.build())
    }

    private fun openIntent(data: Map<String, String>): Intent =
        checkNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(OPENED, true)
            .apply { data.forEach(::putExtra) }

    companion object {
        const val CHANNEL_ID = "notifications"
        private const val OPENED = "dev.dreamerslab.firebase.opened"
        private const val MESSAGE_ID = "google.message_id"
        private val RESERVED_KEYS = setOf(OPENED, "from", "collapse_key")
        private const val PENDING_INTENT_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        fun takeOpenedData(intent: Intent?): Map<String, String>? {
            val extras = intent?.extras ?: return null
            if (!extras.containsKey(MESSAGE_ID) && !extras.getBoolean(OPENED)) return null
            val data = extras.keySet()
                .filterNot { it in RESERVED_KEYS || it.startsWith("google.") || it.startsWith("gcm.") }
                .associateWith { extras.getString(it).orEmpty() }
            extras.keySet().toList().forEach(intent::removeExtra)
            return data
        }
    }
}
