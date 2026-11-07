package com.seldia.music.ui.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin
import kotlin.random.Random

/**
 * Ecualizador decorativo estático para el panel "Sonando ahora".
 * Sin animaciones por frame: se dibuja una única vez (y al redimensionar),
 * evitando consumo de CPU/GPU y bloqueos de captura de UI.
 */
class VisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private val barCount = 42
    private val barHeights = FloatArray(barCount) { i ->
        // Patrón armónico fijo y agradable (pseudo-espectro), sin estado mutable.
        val wave = sin(i * 0.42f) * 0.5f + 0.5f
        val jitter = Random(1337 + i).nextFloat() * 0.22f
        0.18f + wave * 0.55f + jitter
    }

    fun setPlaying(active: Boolean) {
        // Sin animaciones: el ecualizador es puramente decorativo y estático.
        @Suppress("UNUSED_PARAMETER")
        active
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val gap = w * 0.012f
        val barW = (w - gap * (barCount - 1)) / barCount
        val radius = barW / 2f

        barPaint.shader = LinearGradient(
            0f, h, 0f, 0f,
            intArrayOf(0xFFFF0033.toInt(), 0xFFFF4D6D.toInt(), 0xFFF8FAFC.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )

        val centerY = h / 2f
        for (i in 0 until barCount) {
            val amplitude = (barHeights[i] * h).coerceAtLeast(h * 0.07f)
            val left = i * (barW + gap)
            rect.set(left, centerY - amplitude / 2f, left + barW, centerY + amplitude / 2f)
            canvas.drawRoundRect(rect, radius, radius, barPaint)
        }
    }
}
