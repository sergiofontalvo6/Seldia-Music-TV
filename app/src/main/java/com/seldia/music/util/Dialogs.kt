package com.seldia.music.util

import android.content.Context
import android.widget.EditText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.seldia.music.R
import com.seldia.music.data.model.Playlist

/** Muestra el diálogo para elegir una playlist existente o crear una nueva. */
fun showAddToPlaylistDialog(
    context: Context,
    playlists: List<Playlist>,
    onCreateNew: () -> Unit,
    onPick: (Playlist) -> Unit
) {
    val builder = MaterialAlertDialogBuilder(context)
        .setTitle("Añadir a playlist")
        .setNeutralButton("Nueva playlist") { _, _ -> onCreateNew() }
        .setNegativeButton("Cancelar", null)

    if (playlists.isEmpty()) {
        builder.setMessage("Todavía no hay playlists.")
    } else {
        val labels = playlists.map { "${it.name} (${it.trackCount})" }.toTypedArray()
        builder.setItems(labels) { _, which -> onPick(playlists[which]) }
    }
    builder.show()
}

/** Muestra el diálogo de creación de playlist. */
fun showCreatePlaylistDialog(context: Context, onName: (String) -> Unit) {
    val input = EditText(context).apply {
        hint = "Nombre de la playlist"
        setPadding(48, 32, 48, 32)
    }
    MaterialAlertDialogBuilder(context)
        .setTitle("Nueva playlist")
        .setView(input)
        .setPositiveButton("Crear") { _, _ ->
            val name = input.text.toString().trim()
            if (name.isNotEmpty()) onName(name)
        }
        .setNegativeButton("Cancelar", null)
        .show()
}

/** Muestra un diálogo de confirmación. */
fun showConfirmDialog(context: Context, title: String, message: String, onConfirm: () -> Unit) {
    MaterialAlertDialogBuilder(context)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton("Sí") { _, _ -> onConfirm() }
        .setNegativeButton("Cancelar", null)
        .show()
}

/** Muestra un diálogo de confirmación estilo Glass listo. */
fun showModernConfirmDialog(
    context: Context,
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit
) {
    com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(confirmText) { _, _ -> onConfirm() }
        .setNegativeButton("Cancelar", null)
        .show()
}

/** Muestra el diálogo para renombrar una playlist. */
fun showRenamePlaylistDialog(context: Context, currentName: String, onName: (String) -> Unit) {
    val input = EditText(context).apply {
        setText(currentName)
        setSelection(currentName.length)
        setPadding(48, 32, 48, 32)
    }
    MaterialAlertDialogBuilder(context)
        .setTitle("Renombrar playlist")
        .setView(input)
        .setPositiveButton("Guardar") { _, _ ->
            val name = input.text.toString().trim()
            if (name.isNotEmpty()) onName(name)
        }
        .setNegativeButton("Cancelar", null)
        .show()
}


