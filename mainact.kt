package com.valoranttranslator

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class OverlayTranslatorService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_PROJECTION_DATA = "extra_projection_data"
        private const val CHANNEL_ID = "vt_channel_01"
        private const val NOTIFICATION_ID = 42
        private const val CAPTURE_INTERVAL_MS = 900L

        @Volatile var isRunning = false
    }

    private lateinit var mediaProjection: MediaProjection
    private lateinit var virtualDisplay: VirtualDisplay
    private lateinit var imageReader: ImageReader
    private lateinit var windowManager: WindowManager
    private lateinit var translationEngine: TranslationEngine
    private lateinit var overlayManager: OverlayManager
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        resolveScreenMetrics()
        createNotificationChannel()
        translationEngine = TranslationEngine()
        overlayManager = OverlayManager(this, windowManager)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: return START_NOT_STICKY
        
        val projectionData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_PROJECTION_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_PROJECTION_DATA)
        }

        if (projectionData == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val projMgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projMgr.getMediaProjection(resultCode, projectionData)

        setupImageReader()
        startCaptureLoop()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
        overlayManager.clearAll()
        runCatching { virtualDisplay.release() }
        runCatching { imageReader.close() }
        runCatching { mediaProjection.stop() }
        translationEngine.close()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun resolveScreenMetrics() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = windowManager.currentWindowMetrics.bounds
            screenWidth = wm.width()
            screenHeight = wm.height()
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
        }
        screenDensity = resources.displayMetrics.densityDpi
    }

    private fun setupImageReader() {
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "ValorantTranslatorCapture",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            null, null
        )
    }

    private fun startCaptureLoop() {
        serviceScope.launch {
            while (isActive) {
                captureAndTranslate()
                delay(CAPTURE_INTERVAL_MS)
            }
        }
    }

    private fun captureAndTranslate() {
        val bitmap = acquireLatestBitmap() ?: return
        try {
            val results = runBlocking { translationEngine.processFrame(bitmap) }
            Handler(Looper.getMainLooper()).post {
                overlayManager.updateOverlays(results)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            bitmap.recycle()
        }
    }

    private fun acquireLatestBitmap(): Bitmap? {
        val image = imageReader.acquireLatestImage() ?: return null
        return try {
            val plane = image.planes[0]
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val rowPadding = rowStride - pixelStride * screenWidth

            val raw = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            raw.copyPixelsFromBuffer(plane.buffer)
            if (rowPadding == 0) raw
            else Bitmap.createBitmap(raw, 0, 0, screenWidth, screenHeight).also { raw.recycle() }
        } finally {
            image.close()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Valorant Translator",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Live screen translation service"
            setShowBadge(false)
        }
        (getSystemService(NotificationManager::class.java)).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, OverlayTranslatorService::class.java).apply { action = "STOP" }
        val stopPi = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openPi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🎮 Valorant Translator Active")
            .setContentText("Translating Chinese → English in real-time")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPi)
            .setOngoing(true)
            .build()
    }
}
