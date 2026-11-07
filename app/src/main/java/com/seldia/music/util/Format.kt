package com.seldia.music.util

import android.content.Context
import java.util.Locale

/** Color de acento resuelto desde el tema (`?attr/accentPrimary`), con fallback rojo. */
fun Context.accentPrimaryColor(): Int {
    val fallback = androidx.core.content.ContextCompat.getColor(this, com.seldia.music.R.color.primary)
    val typed = android.util.TypedValue()
    if (!theme.resolveAttribute(com.seldia.music.R.attr.accentPrimary, typed, true)) return fallback
    return when (typed.type) {
        android.util.TypedValue.TYPE_ATTRIBUTE -> {
            val inner = android.util.TypedValue()
            if (theme.resolveAttribute(typed.data, inner, true)) inner.data else fallback
        }
        android.util.TypedValue.TYPE_REFERENCE ->
            androidx.core.content.ContextCompat.getColor(this, typed.data)
        else -> typed.data
    }
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%d:%02d", m, s)
    }
}

private val DATE_FORMAT = java.text.SimpleDateFormat("d MMM yyyy", Locale.getDefault())

/** Fecha corta para la columna "FECHA"; "—" cuando el dato es desconocido. */
fun formatDate(epochMs: Long): String {
    if (epochMs <= 0L) return "—"
    return DATE_FORMAT.format(java.util.Date(epochMs))
}

/** Duración total legible ("1 h 45 min" / "32 min") para los encabezados de playlist. */
fun formatTotalDuration(totalMs: Long): String {
    if (totalMs <= 0L) return "0 min"
    val totalMin = totalMs / 60_000L
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "$h h $m min" else "$m min"
}
