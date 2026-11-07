package com.seldia.music.ui

import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.seldia.music.data.network.NetworkError

/**
 * Notificación amigable de errores de red sin romper el flujo ni la app.
 *
 * Intenta [Snackbar] estilizado Glass (`glass_surface_high` + borde
 * `glass_stroke`, texto `text_primary`, animación larga) sobre un ancla;
 * si no hay ancla, cae a [Toast] extremo.
 */
object NetworkUi {

    fun messageFor(error: Throwable): String = NetworkError.friendlyMessage(error)

    fun friendlySnackbar(anchor: View?, message: String): Snackbar? {
        if (anchor == null) return null
        return Snackbar.make(anchor, message, Snackbar.LENGTH_LONG).apply {
            setBackgroundTint(ContextCompat.getColor(anchor.context, com.seldia.music.R.color.glass_surface_high))
            setTextColor(ContextCompat.getColor(anchor.context, com.seldia.music.R.color.text_primary))
            show()
        }
    }

    /** Atajo para Fragments: muestra Snackbar con mensaje amigable o Toast si no hay vista. */
    fun Fragment.showNetworkError(error: Throwable) {
        val message = NetworkError.friendlyMessage(error)
        logError(error, message)
        val anchor = view
        if (anchor != null) {
            friendlySnackbar(anchor, message)
        } else if (isAdded) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }

    fun Fragment.showErrorText(text: String) {
        val anchor = view
        if (anchor != null) friendlySnackbar(anchor, text)
        else if (isAdded) Toast.makeText(requireContext(), text, Toast.LENGTH_LONG).show()
    }

    @Suppress("unused")
    private fun logError(error: Throwable, friendly: String) {
        android.util.Log.w("NetworkError", "friendly=$friendly", error)
    }
}
