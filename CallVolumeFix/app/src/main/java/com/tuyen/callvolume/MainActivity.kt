package com.tuyen.callvolume

import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.tuyen.callvolume.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var audioManager: AudioManager
    private val stream = AudioManager.STREAM_VOICE_CALL

    private var minVolume = 0
    private var maxVolume = 1

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
