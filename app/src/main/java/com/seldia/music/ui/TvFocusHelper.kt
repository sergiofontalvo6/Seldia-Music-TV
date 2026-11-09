package com.seldia.music.ui

import android.view.View
import androidx.core.content.ContextCompat
import com.seldia.music.R

/**
 * Foco TV universal con cristal del tema:
 * - El highlight es un StateListDrawable en el `foreground` (bg_tv_focus_foreground),
 *   por lo que el sistema lo muestra/oculta solo con el estado de foco.
 *   No hay código manual que pueda "quedarse seleccionado".
 * - Escala 1.06x al ganar foco y vuelve a 1.0 al perderlo; si la vista se recicla,
 *   el reinicio se hace en onBind/onViewRecycled de las adapters.
 *
 * Uso: TvFocusHelper.attach(decorView)
 */
object TvFocusHelper {

    fun attach(root: View) {
        fun noClip(v: View) {
            if (v is android.view.ViewGroup) {
                v.clipChildren = false
                v.clipToPadding = false
                for (i in 0 until v.childCount) noClip(v.getChildAt(i))
            }
        }
        noClip(root)
        ensureForeground(root)

        root.viewTreeObserver.addOnGlobalFocusChangeListener { oldFocus, newFocus ->
            if (oldFocus != null && oldFocus !== newFocus) {
                shrink(oldFocus)
            }
            if (newFocus != null && newFocus !== oldFocus) {
                ensureForegroundPath(newFocus)
                grow(newFocus)
            }
        }
    }

    /** Pone el foreground con estado en todos los enfocables ya presentes. */
    private fun ensureForeground(root: View) {
        if (root.isFocusable && root.isClickable) {
            installStateForeground(root)
        }
        if (root is android.view.ViewGroup) {
            for (i in 0 until root.childCount) ensureForeground(root.getChildAt(i))
        }
    }

    /** Instala el foreground en la vista recien enfocada (fragments inflados despues). */
    private fun ensureForegroundPath(view: View) {
        var v: View? = view
        var depth = 0
        while (v != null && depth < 6) {
            if (v.isFocusable && v.isClickable) installStateForeground(v)
            v = v.parent as? View
            depth++
        }
    }

    private fun installStateForeground(view: View) {
        // No pisar un foreground propio de la vista (ripple etc. ya gestionado)
        if (view.foreground == null) {
            view.foreground = ContextCompat.getDrawable(view.context, R.drawable.bg_tv_focus_foreground)
        }
    }

    private fun grow(view: View) {
        if (view.width > 900 || view.height > 900) return
        var p = view.parent as? View
        var depth = 0
        while (p != null && depth < 8) {
            if (p is android.view.ViewGroup) {
                p.clipChildren = false
                p.clipToPadding = false
            }
            p = p.parent as? View
            depth++
        }
        view.animate().cancel()
        view.animate()
            .scaleX(1.06f)
            .scaleY(1.06f)
            .setDuration(120)
            .start()
        view.elevation = 8f
    }

    private fun shrink(view: View) {
        view.animate().cancel()
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(100)
            .start()
        view.elevation = 0f
        // El foreground con estado se apaga solo al perder el foco de estado.
    }

    /** Reinicia escala/elevación al reciclar un item de RecyclerView. */
    fun resetItem(view: View) {
        view.animate().cancel()
        view.scaleX = 1f
        view.scaleY = 1f
        view.elevation = 0f
    }

    fun requestInitialFocus(target: View?, decorView: View?) {
        if (target == null || decorView == null) return
        if (target.requestFocus()) {
            target.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED)
            return
        }
        decorView.postDelayed({
            if (!target.isFocused) {
                val ok = target.requestFocus(View.FOCUS_FORWARD)
                if (ok || target.isFocused) {
                    target.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED)
                } else {
                    decorView.focusSearch(View.FOCUS_DOWN)?.requestFocus()
                }
            }
        }, 350)
    }
}
