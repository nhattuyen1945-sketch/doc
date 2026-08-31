package com.viettelchecker.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.viettelchecker.app.databinding.ActivityMainBinding

private const val CHECK_CODE = "TK"
private const val CHECK_NUMBER = "191"
private const val TIMEOUT_MS = 60_000L

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val mainHandler = Handler(Looper.getMainLooper())
    private var smsReceiver: BroadcastReceiver? = null
    private var timeoutRunnable: Runnable? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results.values.all { it }) {
                startCheck()
            } else {
                setStatus("Cần cấp quyền Gửi SMS và Nhận SMS để kiểm tra tài khoản.", isError = true)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnCheck.setOnClickListener { onCheckClicked() }
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanupReceiver()
    }

    private fun onCheckClicked() {
        val needed = listOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS)
            .filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }

        if (needed.isEmpty()) {
            startCheck()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun startCheck() {
        cleanupReceiver()

        binding.btnCheck.isEnabled = false
        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.tvResult.text = getString(R.string.waiting_placeholder)
        setStatus("Đang gửi tin nhắn \"$CHECK_CODE\" đến $CHECK_NUMBER...")

        registerSmsReceiver()

        try {
            sendCheckSms()
            setStatus("Đang chờ phản hồi từ $CHECK_NUMBER...")
        } catch (e: Exception) {
            setStatus("Gửi tin nhắn thất bại: ${e.message}", isError = true)
            finishCheck()
            return
        }

        timeoutRunnable = Runnable {
            setStatus("Không nhận được phản hồi sau 60 giây. Vui lòng thử lại.", isError = true)
            finishCheck()
        }
        mainHandler.postDelayed(timeoutRunnable!!, TIMEOUT_MS)
    }

    private fun sendCheckSms() {
        val smsManager: SmsManager =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        smsManager.sendTextMessage(CHECK_NUMBER, null, CHECK_CODE, null, null)
    }

    private fun registerSmsReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (messages.isNullOrEmpty()) return

                val sender = messages[0].originatingAddress ?: ""
                if (!sender.contains(CHECK_NUMBER)) return

                val fullBody = messages.joinToString(separator = "") { it.messageBody ?: "" }
                timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                binding.tvResult.text = fullBody
                setStatus("Đã nhận được phản hồi từ $CHECK_NUMBER.")
                finishCheck()
            }
        }
        smsReceiver = receiver
        val filter = IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    private fun finishCheck() {
        binding.btnCheck.isEnabled = true
        binding.progressBar.visibility = android.view.View.GONE
        cleanupReceiver()
    }

    private fun cleanupReceiver() {
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
        smsReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: IllegalArgumentException) {
                // already unregistered
            }
        }
        smsReceiver = null
    }

    private fun setStatus(message: String, isError: Boolean = false) {
        binding.tvStatus.text = message
        binding.tvStatus.setTextColor(
            if (isError) getColor(R.color.viettel_red) else android.graphics.Color.parseColor("#333333")
        )
    }
}
