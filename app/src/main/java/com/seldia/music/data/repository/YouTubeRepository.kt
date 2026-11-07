package com.seldia.music.data.repository

import com.seldia.music.data.extractor.YouTubeAudioExtractor
import com.seldia.music.data.model.Source
import com.seldia.music.data.model.Track

/** Flujo listo para descargar, con el MIME y la extensión reales del contenedor. */
data class DownloadMediaInfo(
    val url: String,
    val mimeType: String,
    val extension: String
)

class YouTubeRepository(
    private val audioExtractor: YouTubeAudioExtractor = YouTubeAudioExtractor()
) {    suspend fun search(query: String): Result<List<Track>> {
        return runCatching {
            val searchResults = audioExtractor.search(query).getOrThrow()
            searchResults.map { item ->
                Track(
                    id = item.id,
                    title = item.title,
                    artist = item.author,
                    durationMs = item.durationMs,
                    artworkUrl = item.thumbnailUrl,
                    source = Source.YOUTUBE,
                    streamUrl = item.id
                )
            }
        }
    }

    /** URLs candidatas (de mejor a peor): de audio, o de vídeo con audio incluido si [video] es true. */
    suspend fun getStreamUrls(videoIdOrUrl: String, video: Boolean): Result<List<String>> {
        return runCatching {
            val result = audioExtractor.extractAudioTrack(videoIdOrUrl)
            if (result.isFailure) {
                val exception = result.exceptionOrNull()
                exception?.printStackTrace()
                throw exception ?: Exception("Error desconocido al extraer el contenido")
            }
            val trackInfo = result.getOrThrow()
            val urls = if (video) trackInfo.videoUrls else trackInfo.audioStreams.map { it.url }
            urls.ifEmpty {
                throw Exception(
                    if (video) "No hay vídeo disponible para esta pista"
                    else "No se encontró ningún flujo de audio disponible"
                )
            }
        }
    }

    /**
     * Devuelve el flujo que se va a guardar en disco junto con su MIME y extensión,
     * para que MediaStore registre el archivo con el formato correcto.
     */
    suspend fun getDownloadMedia(videoIdOrUrl: String, video: Boolean): Result<DownloadMediaInfo> {
        return runCatching {
            val info = audioExtractor.extractAudioTrack(videoIdOrUrl).getOrThrow()
            if (video) {
                val stream = info.videoStreams.firstOrNull()
                    ?: throw Exception("No hay vídeo disponible para esta pista")
                DownloadMediaInfo(stream.url, stream.mimeType, stream.extension)
            } else {
                val stream = info.audioStreams.firstOrNull()
                    ?: throw Exception("No se encontró ningún flujo de audio disponible")
                val isMp4 = stream.codec.contains("mp4a", ignoreCase = true) ||
                    stream.codec.contains("m4a", ignoreCase = true) ||
                    stream.codec.equals("aac", ignoreCase = true)
                if (isMp4) {
                    DownloadMediaInfo(stream.url, "audio/mp4", "m4a")
                } else {
                    DownloadMediaInfo(stream.url, "audio/webm", "webm")
                }
            }
        }
    }
}
