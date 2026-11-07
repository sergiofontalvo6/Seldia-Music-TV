package com.seldia.music.ui.player

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.seldia.music.util.accentPrimaryColor
import com.seldia.music.R
import com.seldia.music.data.repository.LyricLine
import com.seldia.music.databinding.ItemLyricLineBinding

/**
 * Lista de líneas de letra con resaltado karaoke:
 * - línea activa: primaria, negrita, 24sp, con animación suave de escala/alfa;
 * - líneas anteriores: atenuadas; posteriores: atenuadas menos intensas.
 */
class LyricsAdapter(
    private val lines: List<LyricLine>
) : RecyclerView.Adapter<LyricsAdapter.VH>() {

    var activeIndex: Int = -1
        private set

    /** Actualiza el índice activo y anima únicamente las filas afectadas. */
    fun setActiveIndex(index: Int) {
        if (index == activeIndex) return
        val previous = activeIndex
        activeIndex = index
        if (previous >= 0) notifyItemChanged(previous)
        if (index >= 0) notifyItemChanged(index)
    }

    override fun getItemCount(): Int = lines.size

    /** Marca de tiempo de la línea indicada (lista ordenada). */
    fun lineTimeAt(index: Int): Long = lines[index].timeMs

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemLyricLineBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(lines[position], position)

    inner class VH(private val binding: ItemLyricLineBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(line: LyricLine, position: Int) {
            val context = binding.root.context
            val isActive = position == activeIndex
            val isPast = activeIndex >= 0 && position < activeIndex

            val text = line.text.ifBlank { "♪" }
            binding.tvLine.text = text

            when {
                isActive -> {
                    binding.tvLine.textSize = 24f
                    binding.tvLine.setTextColor(context.accentPrimaryColor())
                    binding.tvLine.alpha = 1f
                    binding.tvLine.paint.isFakeBoldText = true
                    animateActive(binding.root)
                }
                isPast -> {
                    binding.tvLine.textSize = 19f
                    binding.tvLine.setTextColor(dimmed(context, 0.42f))
                    binding.tvLine.alpha = 0.75f
                    binding.tvLine.paint.isFakeBoldText = false
                    resetTransform(binding.root)
                }
                else -> {
                    binding.tvLine.textSize = 20f
                    binding.tvLine.setTextColor(dimmed(context, 0.82f))
                    binding.tvLine.alpha = 0.9f
                    binding.tvLine.paint.isFakeBoldText = false
                    resetTransform(binding.root)
                }
            }
        }

        private fun dimmed(context: Context, factor: Float): Int {
            val base = ContextCompat.getColor(context, R.color.text_primary)
            val a = (Color.alpha(base) * factor).toInt().coerceIn(0, 255)
            return Color.argb(a, Color.red(base), Color.green(base), Color.blue(base))
        }

        private fun resetTransform(view: android.view.View) {
            view.scaleX = 1f
            view.scaleY = 1f
        }

        private fun animateActive(view: android.view.View) {
            view.animate().cancel()
            view.scaleX = 0.96f
            view.scaleY = 0.96f
            view.alpha = 0.4f
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 320L
                interpolator = DecelerateInterpolator(1.6f)
                addUpdateListener {
                    val f = it.animatedValue as Float
                    view.scaleX = 0.96f + 0.04f * f
                    view.scaleY = 0.96f + 0.04f * f
                    view.alpha = 0.4f + 0.6f * f
                }
                start()
            }
        }
    }
}
