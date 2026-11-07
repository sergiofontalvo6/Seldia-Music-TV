package com.seldia.music.data.backup

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.seldia.music.data.db.AppDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Backup/Restore robusto: exporta a JSON toda la base Room
 * (tracks/favoritos, playlists, playlist_tracks e historial)
 * más las preferencias de la app.
 */
class BackupManager(
    private val context: Context,
    private val db: AppDatabase
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val gsonLenient: Gson = Gson()

    suspend fun exportBackup(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = context.getSharedPreferences("seldia_prefs", Context.MODE_PRIVATE).all
                .mapValues { it.value.toString() }
            val data = BackupData(
                tracks = db.trackDao().getAll(),
                playlists = db.playlistDao().getAllPlaylists().map {
                    com.seldia.music.data.db.PlaylistEntity(it.id, it.name, it.createdAt)
                },
                playlistTracks = db.playlistDao().getAllRefs(),
                history = db.historyDao().getAll(),
                prefs = prefs
            )
            val json = gson.toJson(data)
            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                output.write(json.toByteArray(Charsets.UTF_8))
            } ?: throw Exception("No se pudo abrir el documento de salida")
        }
    }

    suspend fun importBackup(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val json = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            } ?: throw Exception("No se pudo leer el archivo de copia")

            val data = gsonLenient.fromJson(json, BackupData::class.java)
                ?: throw Exception("Archivo de copia no válido")

            // Validación mínima antes de tocar la BD.
            data.tracks.forEachIndexed { index, t ->
                require(t.id.isNotEmpty()) { "Pista inválida en índice $index" }
            }

            db.withTransaction {
                // Sustitución completa (migración exacta a otro dispositivo).
                db.playlistDao().deleteAllRefs()
                db.playlistDao().deleteAllPlaylists()
                db.historyDao().clear()
                db.trackDao().deleteAll()

                if (data.tracks.isNotEmpty()) db.trackDao().upsertAll(data.tracks)
                data.playlists.forEach { playlist ->
                    db.playlistDao().insertPlaylist(playlist)
                }
                data.playlistTracks.forEach { ref ->
                    db.playlistDao().addTrack(ref)
                }
                data.history.forEach { entry -> db.historyDao().insert(entry) }
            }

            if (data.prefs.isNotEmpty()) {
                val editor = context.getSharedPreferences("seldia_prefs", Context.MODE_PRIVATE).edit()
                data.prefs.forEach { (key, value) ->
                    when (value) {
                        "true", "false" -> editor.putBoolean(key, value.toBoolean())
                        else -> {
                            val long = value.toLongOrNull()
                            val int = value.toIntOrNull()
                            when {
                                long != null -> editor.putLong(key, long)
                                int != null -> editor.putInt(key, int)
                                else -> editor.putString(key, value)
                            }
                        }
                    }
                }
                editor.apply()
            }
        }
    }
}
