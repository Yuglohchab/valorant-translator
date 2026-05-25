package com.valoranttranslator

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView

class OverlayManager(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private val activeViews = mutableMapOf<String, View>()
    private val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    fun updateOverlays(results: List<TranslationResult>) {
        val incomingKeys = results.associateBy { key(it) }
        val stale = activeViews.keys.filter { it !in incomingKeys }
        stale.forEach { k ->
            safeRemove(activeViews[k])
            activeViews.remove(k)
        }
        results.forEach { result ->
            val k = key(result)
            if (k !in activeViews) {
                addOverlay(result, k)
            }
        }
    }

    fun clearAll() {
        activeViews.values.forEach { safeRemove(it) }
        activeViews.clear()
    }

    private fun addOverlay(result: TranslationResult, key: String) {
        val view = buildLabel(result)
        val params = buildParams(result)
        try {
            windowManager.addView(view, params)
            activeViews[key] = view
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildLabel(result: TranslationResult): TextView = TextView(context).apply {
        text = result.translated
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, result.fontSize)
        setTextColor(Color.WHITE)
        setShadowLayer(3f, 1f, 1f, Color.argb(200, 0, 0, 0))
        background = buildBackground()
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        val hPad = (result.fontSize * 0.5f).toInt().coerceAtLeast(6)
        val vPad = (result.fontSize * 0.15f).toInt().coerceAtLeast(3)
        setPadding(hPad + 4, vPad, hPad, vPad)
        maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
        isClickable = false
        isFocusable = false
    }

    private fun buildBackground(): android.graphics.drawable.Drawable {
        val base = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(5f)
            setColor(Color.argb(210, 10, 12, 16))
        }
        val stripe = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setColor(Color.parseColor("#FF4655"))
            cornerRadii = floatArrayOf(dpToPx(5f), dpToPx(5f), 0f, 0f, 0f, 0f, dpToPx(5f), dpToPx(5f))
        }
        val layer = android.graphics.drawable.LayerDrawable(arrayOf(base, stripe))
        layer.setLayerInset(1, 0, 0, dpToPx(4f).toInt() * -1 + 4, 0)
        return layer
    }

    private fun buildParams(result: TranslationResult): WindowManager.LayoutParams {
        val b = result.bounds
        return WindowManager.LayoutParams(
            b.width(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            b.left,
            b.top,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).also {
            it.gravity = Gravity.TOP or Gravity.START
        }
    }

    private fun safeRemove(view: View?) {
        view ?: return
        try { windowManager.removeView(view) } catch (_: Exception) {}
    }

    private fun key(r: TranslationResult): String {
        val bx = (r.bounds.left / 20) * 20
        val by = (r.bounds.top / 20) * 20
        return "$bx,$by:${r.original.hashCode()}"
    }

    private fun dpToPx(dp: Float): Float =
        dp * context.resources.displayMetrics.density
}
