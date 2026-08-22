package com.tuyen.callvolume

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager

class VolumeActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val stream = AudioManager.STREAM_VOICE_CALL

        when (intent.action) {
            VolumeNotificationHelper.ACTION_INCREASE -> {
                audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, 0)
                VolumeNotificationHelper.buildAndShow(context)
            }
            VolumeNotificationHelper.ACTION_DECREASE -> {
                audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, 0)
                VolumeNotificationHelper.buildAndShow(context)
            }
            VolumeNotificationHelper.ACTION_DISMISS -> {
                VolumeNotificationHelper.dismiss(context)
            }
        }
    }
}
