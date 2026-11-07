package com.seldia.music.data.spotify

import android.util.Log
import com.seldia.music.data.db.sanitizeAlbum
import com.seldia.music.data.db.sanitizeArtist
import com.seldia.music.data.db.sanitizeTitle
import com.seldia.music.data.extractor.YouTubeAudioExtractor
import com.seldia.music.data.model.Source
import com.seldia.music.data.model.Track
import com.seldia.music.data.repository.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SpotifyImportProgress(
    val total: Int,
    val processed: Int,
    val currentTitle: String
)

data class SpotifyImportResult(
    val playlistId: Long,
    val playlistName: String,
    val importedCount: Int,
    val totalCount: Int
)

/**
 * Orquesta la importación completa:
 * parseo Spotify → búsqueda NewPipe por pista → guardado Room transaccional.
 *
 * La UI "Mis Listas" es reactiva (LiveData observePlaylists), así la lista
 * aparece al instante tras el insert.
 */
class SpotifyPlaylistImporter(
    private val libraryRepository: LibraryRepository,
    private val extractor: YouTubeAudioExtractor = YouTubeAudioExtractor()
) {

    companion object {
        private const val TAG = "SpotifyImport"
    }

    suspend fun importFromUrl(
        urlOrId: String,
        onProgress: (SpotifyImportProgress) -> Unit = {}
    ): Result<SpotifyImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val parsed = SpotifyPlaylistParser.fetchPlaylist(urlOrId).getOrThrow()
            Log.d(TAG, "Parsed '${parsed.name}' tracks=${parsed.tracks.size}")
            importParsed(parsed.name, parsed.tracks, onProgress)
        }
    }

    /**
     * Importa playlists autenticadas (API Web): las entradas ya vienen de
     * `/playlists/{id}/tracks`, se mapean vía NewPipe igual que la URL pública.
     */
    suspend fun importFromEntries(
        name: String,
        entries: List<SpotifyTrackEntry>,
        coverUrl: String? = null,
        onProgress: (SpotifyImportProgress) -> Unit = {}
    ): Result<SpotifyImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            Log.d(TAG, "Import API '$name' tracks=${entries.size} cover=${coverUrl != null}")
            importParsed(name, entries, onProgress)
        }
    }

    private suspend fun importParsed(
        name: String,
        entries: List<SpotifyTrackEntry>,
        onProgress: (SpotifyImportProgress) -> Unit = {}
    ): SpotifyImportResult {
        val mapped = mutableListOf<Track>()
            val seenVideoIds = mutableSetOf<String>()
            // Cache de búsquedas para no repetir NewPipe en títulos duplicados.
            val searchCache = mutableMapOf<String, Track?>()

            for ((index, entry) in entries.withIndex()) {
                val safeTitle = sanitizeTitle(entry.title)
                val safeArtist = sanitizeArtist(entry.artist)
                onProgress(SpotifyImportProgress(entries.size, index, safeTitle))
                val queryKey = "$safeTitle|$safeArtist".lowercase()
                val cached = searchCache[queryKey]
                val track = if (cached != null) {
                    cached
                } else {
                    mapSingleTrack(safeTitle, safeArtist, entry.durationMs)
                        .also { searchCache[queryKey] = it }
                }
                if (track != null) {
                    // Para carátula dinámica, la miniatura YouTube ya viene en artworkUrl.
                    // Deduplica por videoId para respetar la PK compuesta de playlist_tracks.
                    if (seenVideoIds.add(track.id)) {
                        mapped.add(track)
                    } else {
                        Log.d(TAG, "Skipped duplicate ${track.id} for '$safeTitle'")
                    }
                } else {
                    Log.w(TAG, "No YouTube match for '$safeTitle' - $safeArtist")
                }
            }

            onProgress(SpotifyImportProgress(entries.size, entries.size, ""))

            val playlistId = libraryRepository.importPlaylist(name, mapped)
            Log.d(TAG, "Imported playlist $playlistId '$name' ${mapped.size}/${entries.size}")
            return SpotifyImportResult(
                playlistId = playlistId,
                playlistName = name,
                importedCount = mapped.size,
                totalCount = entries.size
            )
    }

    private suspend fun mapSingleTrack(title: String, artist: String, spotifyDurationMs: Long): Track? {
        val query = "$title $artist".trim()
        val result = extractor.search(query)
        if (result.isFailure) {
            Log.w(TAG, "Search failed for '$query': ${result.exceptionOrNull()}")
            return null
        }
        val first = result.getOrThrow().firstOrNull() ?: return null
        val safeTitle = sanitizeTitle(first.title).ifBlank { title }
        val safeArtist = sanitizeArtist(first.author).ifBlank { artist }
        val duration = if (first.durationMs > 0L) first.durationMs else spotifyDurationMs
        return Track(
            id = first.id,
            title = safeTitle,
            artist = safeArtist,
            // YouTube no entrega álbum; "" evita grupos "<unknown>" y deja layout limpio.
            album = sanitizeAlbum(""),
            durationMs = duration.coerceAtLeast(0L),
            artworkUrl = first.thumbnailUrl.takeIf { it.isNotBlank() },
            source = Source.YOUTUBE,
            streamUrl = first.id
        )
    }
}
