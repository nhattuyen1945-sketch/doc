package com.tuyen.callvolume

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.tuyen.callvolume.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var audioManager: AudioManager
    private val stream = AudioManager.STREAM_VOICE_CALL

    private var minVolume = 0
    private var maxVolume = 1

    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            VolumeNotificationHelper.buildAndShow(this)
            updateToggleButtonText()
        } else {
            Toast.makeText(
                this,
                "Cần cấp quyền thông báo để hiện thanh âm lượng",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        minVolume = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            audioManager.getStreamMinVolume(stream)
        } else {
            0
        }
        maxVolume = audioManager.getStreamMaxVolume(stream)
        binding.seekBarVolume.max = (maxVolume - minVolume).coerceAtLeast(1)

        refreshUi()

        binding.btnIncrease.setOnClickListener { adjustVolume(AudioManager.ADJUST_RAISE) }
        binding.btnDecrease.setOnClickListener { adjustVolume(AudioManager.ADJUST_LOWER) }
        binding.btnToggleNotifBar.setOnClickListener { toggleNotificationBar() }

        binding.seekBarVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    setVolume(progress + minVolume)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
        updateToggleButtonText()
    }

    private fun toggleNotificationBar() {
        if (VolumeNotificationHelper.isActive(this)) {
            VolumeNotificationHelper.dismiss(this)
            updateToggleButtonText()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            VolumeNotificationHelper.buildAndShow(this)
            updateToggleButtonText()
        }
    }

    private fun updateToggleButtonText() {
        binding.btnToggleNotifBar.text = if (VolumeNotificationHelper.isActive(this)) {
            getString(R.string.hide_notif_bar)
        } else {
            getString(R.string.show_notif_bar)
        }
    }

    private fun adjustVolume(direction: Int) {
        try {
            audioManager.adjustStreamVolume(stream, direction, 0)
        } catch (e: SecurityException) {
            Toast.makeText(this, "Không thể chỉnh âm lượng: ${e.message}", Toast.LENGTH_SHORT).show()
        }
        refreshUi()
    }

    private fun setVolume(level: Int) {
        try {
            audioManager.setStreamVolume(stream, level.coerceIn(minVolume, maxVolume), 0)
        } catch (e: SecurityException) {
            Toast.makeText(this, "Không thể chỉnh âm lượng: ${e.message}", Toast.LENGTH_SHORT).show()
        }
        refreshUi()
    }

    private fun refreshUi() {
        val current = audioManager.getStreamVolume(stream)
        binding.seekBarVolume.progress = (current - minVolume).coerceIn(0, maxVolume - minVolume)
        binding.tvVolumeLevel.text = getString(R.string.volume_level_format, current, maxVolume)
    }
}
