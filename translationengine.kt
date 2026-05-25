package com.valoranttranslator

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.tasks.await

data class TranslationResult(
    val original: String,
    val translated: String,
    val bounds: Rect,
    val fontSize: Float
)

class TranslationEngine {

    private val recognizer = TextRecognition.getClient(
        ChineseTextRecognizerOptions.Builder().build()
    )
    private val translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.CHINESE)
            .setTargetLanguage(TranslateLanguage.ENGLISH)
            .build()
    )

    private val cache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) =
            size > 120
    }

    init {
        translator.downloadModelIfNeeded()
            .addOnFailureListener { it.printStackTrace() }
    }

    suspend fun processFrame(bitmap: Bitmap): List<TranslationResult> {
        val image = InputImage.fromBitmap(bitmap, 0)
        val visionText = recognizer.process(image).await()
        val results = mutableListOf<TranslationResult>()
        for (block in visionText.textBlocks) {
            val raw = block.text.trim()
            if (raw.isBlank() || !hasChinese(raw)) continue
            val bounds = block.boundingBox ?: continue
            val translated = translateCached(raw)
            if (translated.isBlank() || translated == raw) continue
            val estimatedFontPx = estimateFontSize(block.lines.firstOrNull()?.boundingBox)
            results.add(
                TranslationResult(
                    original = raw,
                    translated = postProcess(translated),
                    bounds = bounds,
                    fontSize = estimatedFontPx
                )
            )
        }
        return results
    }

    fun close() {
        recognizer.close()
        translator.close()
    }

    private suspend fun translateCached(text: String): String {
        cache[text]?.let { return it }
        return try {
            val result = translator.translate(text).await()
            cache[text] = result
            result
        } catch (e: Exception) {
            ""
        }
    }

    private fun hasChinese(text: String): Boolean =
        text.any { c -> c.code in 0x4E00..0x9FFF || c.code in 0x3400..0x4DBF }

    private fun estimateFontSize(lineBox: Rect?): Float =
        (lineBox?.height()?.toFloat() ?: 18f).coerceIn(12f, 30f)

    private fun postProcess(text: String): String {
        return text.trim()
            .replace(Regex("\\s{2,}"), " ")
            .replace(Regex("^[^a-zA-Z0-9(\\[{]+"), "")
            .replaceFirstChar { it.uppercaseChar() }
            .let { if (it.length > 60) it.take(57) + "…" else it }
    }
}
