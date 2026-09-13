package com.tuyen.callvolume

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat

object VolumeNotificationHelper {

    const val CHANNEL_ID = "call_volume_channel"
    const val NOTIF_ID = 1001

    const val ACTION_INCREASE = "com.tuyen.callvolume.ACTION_INCREASE"
    const val ACTION_DECREASE = "com.tuyen.callvolume.ACTION_DECREASE"
    const val ACTION_DISMISS = "com.tuyen.callvolume.ACTION_DISMISS"

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notif_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun pendingIntentFor(context: Context, action: String): PendingIntent {
        val intent = Intent(context, VolumeActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun buildAndShow(context: Context) {
        ensureChannel(context)

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val stream = AudioManager.STREAM_VOICE_CALL
        val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            audioManager.getStreamMinVolume(stream)
        } else {
            0
        }
        val max = audioManager.getStreamMaxVolume(stream)
        val current = audioManager.getStreamVolume(stream)

        val views = RemoteViews(context.packageName, R.layout.notification_volume).apply {
            setTextViewText(
                R.id.notifVolumeLevel,
                context.getString(R.string.volume_level_format, current, max)
            )
            setProgressBar(
                R.id.notifProgress,
                (max - min).coerceAtLeast(1),
                (current - min).coerceIn(0, max - min),
                false
            )
            setOnClickPendingIntent(R.id.notifBtnIncrease, pendingIntentFor(context, ACTION_INCREASE))
            setOnClickPendingIntent(R.id.notifBtnDecrease, pendingIntentFor(context, ACTION_DECREASE))
            setOnClickPendingIntent(R.id.notifBtnDismiss, pendingIntentFor(context, ACTION_DISMISS))
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_volume)
            .setCustomContentView(views)
            .setCustomBigContentView(views)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID, notification)
    }

    fun dismiss(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIF_ID)
    }

    fun isActive(context: Context): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.activeNotifications.any { it.id == NOTIF_ID }
    }
}
