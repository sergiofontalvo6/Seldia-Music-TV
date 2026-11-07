package com.seldia.music.data.offline

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.room.withTransaction
import com.seldia.music.data.db.AppDatabase
import com.seldia.music.data.db.TrackEntity
import com.seldia.music.data.model.Source
import com.seldia.music.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repositorio de la biblioteca offline: consulta MediaStore (música del
 * dispositivo y las pistas descargadas por SeldiaMusic en /Music/SeldiaMusic,
 * que se indexan ahí al guardarse como audio).
 *
 * Las descargas de YouTube se cruzan con Room por su URI para recuperar
 * título, artista, duración y carátula (miniatura de YouTube), y para
 * respetar el estado de favorito fusionando ambas claves.
 */
class OfflineMusicRepository(
    private val context: Context,
    private val database: AppDatabase? = null
) {

    companion object {
        private fun cleanTitle(raw: String?): String =
            com.seldia.music.data.db.sanitizeTitle(raw)

        private fun cleanArtist(raw: String?): String =
            com.seldia.music.data.db.sanitizeArtist(raw)

        private fun cleanAlbum(raw: String?): String =
            com.seldia.music.data.db.sanitizeAlbum(raw)

        private fun cleanArtwork(raw: String?): String? =
            com.seldia.music.data.db.sanitizeArtworkUrl(raw)
    }

    suspend fun loadTracks(): List<Track> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Track>()
        val albumArtIds = loadAlbumArtIds()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATE_ADDED
        )
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        // IS_MUSIC puede quedar NULL en pistas insertadas por SeldiaMusic en API <= 28;
        // el MIME audio/* garantiza que las descargas offline aparezcan igualmente.
        val selection = "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%')"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val albumId = cursor.getLong(albumIdCol)
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                val artwork = if (albumId != 0L && albumId in albumArtIds) {
                    "content://media/external/audio/albumart/$albumId"
                } else {
                    null
                }
                result.add(
                    Track(
                        id = id.toString(),
                        title = cleanTitle(cursor.getString(titleCol)),
                        artist = cleanArtist(cursor.getString(artistCol)),
                        album = cleanAlbum(cursor.getString(albumCol)),
                        durationMs = cursor.getLong(durCol),
                        artworkUrl = cleanArtwork(artwork),
                        uri = uri.toString(),
                        source = Source.LOCAL,
                        addedAt = cursor.getLong(dateCol) * 1000L
                    )
                )
            }
        }
        mergeRoomMetadata(result)
    }

    /**
     * Repara filas Room "LOCAL|id" asociadas a la misma URI: les copia los
     * metadatos ricos del registro YOUTUBE (miniatura, duración, título).
     * Corrige los favoritos guardados antes de la fusión, una sola vez.
     */
    private suspend fun healLinkedRows(
        db: AppDatabase,
        rows: List<com.seldia.music.data.db.TrackEntity>,
        yt: com.seldia.music.data.db.TrackEntity?
    ) {
        yt ?: return
        rows.filter { it.source != Source.YOUTUBE.name }.forEach { local ->
            val healed = local.copy(
                title = yt.title,
                artist = yt.artist,
                album = yt.album,
                durationMs = if (yt.durationMs > 0L) yt.durationMs else local.durationMs,
                artworkUrl = local.artworkUrl ?: yt.artworkUrl,
                isFavorite = local.isFavorite || yt.isFavorite
            )
            if (healed != local) {
                runCatching { db.trackDao().upsert(healed) }
            }
        }
    }

    /**
     * Cruza las filas de MediaStore con Room por URI: sustituye título,
     * artista, álbum, duración y carátula por los metadatos ricos de Room
     * (miniatura de YouTube incluida) y fusiona el estado de favorito de
     * ambas claves (LOCAL|id y YOUTUBE|videoId).
     */
    private suspend fun mergeRoomMetadata(tracks: List<Track>): List<Track> {
        val db = database ?: return tracks
        val withUri = runCatching { db.trackDao().getWithUri() }.getOrNull() ?: return tracks
        val byUri: Map<String, List<TrackEntity>> = withUri.groupBy { it.uri!! }

        return tracks.map { track ->
            val rows = byUri[track.uri] ?: return@map track
            val yt = rows.firstOrNull { it.source == Source.YOUTUBE.name }
            val fav = rows.any { it.isFavorite }
            healLinkedRows(db, rows, yt)
            track.copy(
                title = cleanTitle(yt?.title ?: track.title),
                artist = cleanArtist(yt?.artist ?: track.artist),
                album = cleanAlbum(yt?.album?.takeIf { it.isNotBlank() } ?: track.album),
                durationMs = yt?.durationMs?.takeIf { it > 0L } ?: track.durationMs,
                artworkUrl = cleanArtwork(yt?.artworkUrl)
                    ?: rows.firstNotNullOfOrNull { cleanArtwork(it.artworkUrl) }
                    ?: track.artworkUrl,
                addedAt = maxOf(yt?.addedAt ?: 0L, track.addedAt),
                isFavorite = fav
            )
        }
    }

    /**
     * Elimina una canción de la biblioteca offline: borra el archivo físico
     * (MediaStore + ruta real en API <= 28) y todos los registros Room
     * asociados a su URI (tracks, playlist_tracks e historial).
     * No es reversible.
     */
    suspend fun delete(track: Track): Boolean = withContext(Dispatchers.IO) {
        val uriStr = track.uri ?: return@withContext false
        val uri = Uri.parse(uriStr)

        // Ruta física previa (API <= 28 la exige; en 29+ MediaStore borra el archivo).
        val dataPath = runCatching {
            context.contentResolver.query(uri, arrayOf(MediaStore.Audio.Media.DATA), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()

        val deleted = runCatching {
            context.contentResolver.delete(uri, null, null)
        }.getOrDefault(0)

        // Respaldo: en API <= 28 o si la fila ya no existía, borra el archivo físico.
        dataPath?.takeIf { it.isNotBlank() }?.let { path ->
            runCatching { java.io.File(path).apply { if (exists()) delete() } }
        }

        database?.let { db ->
            runCatching {
                db.withTransaction {
                    val ids = db.trackDao().getIdsByUri(uriStr)
                    if (ids.isNotEmpty()) {
                        db.playlistDao().deleteRefs(ids)
                    }
                    db.trackDao().deleteByUri(uriStr)
                    db.historyDao().deleteByUri(uriStr)
                }
            }
        }
        // Se considera borrada si desapareció la fila/archivo o si se limpió Room.
        deleted > 0 || dataPath == null || database != null
    }

    /**
     * Consulta una sola vez qué álbumes tienen carátula real en MediaStore.
     * Así no se asignan URIs inexistentes que provocan FileNotFoundException
     * en Media3 y en el proveedor de notificaciones.
     */
    private fun loadAlbumArtIds(): Set<Long> = runCatching {
        val ids = mutableSetOf<Long>()
        context.contentResolver.query(
            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM_ART),
            null,
            null,
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)
            val artCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM_ART)
            while (cursor.moveToNext()) {
                if (!cursor.getString(artCol).isNullOrBlank()) {
                    ids.add(cursor.getLong(idCol))
                }
            }
        }
        ids
    }.getOrDefault(emptySet())
}
