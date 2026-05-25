package com.valoranttranslator

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.valoranttranslator.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mediaProjectionManager: MediaProjectionManager

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            requestMediaProjection()
        } else {
            showDialog(
                "Overlay Permission Required",
                "The app needs permission to draw over other apps to show translations on top of Valorant."
            )
        }
    }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startTranslatorService(result.resultCode, result.data!!)
        } else {
            setStatus("Permission denied - screen capture cancelled", isError = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        setupButtons()
        syncUI()
    }

    override fun onResume() {
        super.onResume()
        syncUI()
    }

    private fun setupButtons() {
        binding.btnStart.setOnClickListener {
            checkAndRequestPermissions()
        }
        binding.btnStop.setOnClickListener {
            stopTranslatorService()
        }
    }

    private fun syncUI() {
        val running = OverlayTranslatorService.isRunning
        binding.btnStart.isEnabled = !running
        binding.btnStop.isEnabled = running
        binding.btnStart.alpha = if (running) 0.5f else 1f

        binding.statusDot.setColorFilter(
            if (running) 0xFF00E676.toInt() else 0xFF546E7A.toInt()
        )

        setStatus(if (running) "Translator Active - overlay running" else "Ready to start")

        if (!running) {
            binding.tvDownloadNote.visibility = View.VISIBLE
        } else {
            binding.tvDownloadNote.visibility = View.GONE
        }
    }

    private fun setStatus(msg: String, isError: Boolean = false) {
        binding.tvStatus.text = msg
        binding.tvStatus.setTextColor(
            if (isError) 0xFFFF4655.toInt() else 0xFF8B9BB4.toInt()
        )
    }

    private fun checkAndRequestPermissions() {
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("Overlay Permission")
                .setMessage("To display translations on top of Valorant, this app needs the \"Display over other apps\" permission.\n\nTap OK to open settings.")
                .setPositiveButton("OK") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    overlayPermissionLauncher.launch(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            requestMediaProjection()
        }
    }

    private fun requestMediaProjection() {
        setStatus("Waiting for screen capture permission...")
        mediaProjectionLauncher.launch(
            mediaProjectionManager.createScreenCaptureIntent()
        )
    }

    private fun startTranslatorService(resultCode: Int, data: Intent) {
        val intent = Intent(this, OverlayTranslatorService::class.java).apply {
            putExtra(OverlayTranslatorService.EXTRA_RESULT_CODE, resultCode)
            putExtra(OverlayTranslatorService.EXTRA_PROJECTION_DATA, data)
        }
        startForegroundService(intent)
        setStatus("Starting translator...")
        syncUI()
    }

    private fun stopTranslatorService() {
        stopService(Intent(this, OverlayTranslatorService::class.java))
        OverlayTranslatorService.isRunning = false
        syncUI()
    }

    private fun showDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
