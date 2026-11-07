package com.seldia.music.data.extractor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.regex.Pattern

data class YouTubeSearchResult(
    val id: String,
    val title: String,
    val author: String,
    val durationText: String,
    val durationMs: Long,
    val thumbnailUrl: String
)

data class AudioStream(
    val url: String,
    val quality: String,
    val bitrate: Long,
    val codec: String
)

data class YouTubeVideoStream(
    val url: String,
    val mimeType: String,
    val extension: String,
    val resolution: String
)

data class YouTubeTrackInfo(
    val id: String,
    val title: String,
    val author: String,
    val audioStreams: List<AudioStream>,
    val videoStreams: List<YouTubeVideoStream>,
    val videoUrls: List<String>
)

class YouTubeAudioExtractor {

    init {
        NewPipe.init(NewPipeDownloader.instance)
    }

    private companion object {
        const val MAX_VIDEO_HEIGHT = 720
    }

    /**
     * Búsqueda directa en YouTube vía NewPipe Extractor (ALL): devuelve todo
     * (vídeos, canciones, etc.) sin filtrar por música. Los fallbacks a
     * VIDEOS y MUSIC_SONGS solo se usan si la búsqueda general viene vacía.
     * Devuelve resultados listos para reproducir sin pasar por WebView ni
     * scraping de HTML.
     */
    suspend fun search(query: String): Result<List<YouTubeSearchResult>> = withContext(Dispatchers.IO) {
        runCatching {
            val all = runCatching {
                searchWithFilter(query, YoutubeSearchQueryHandlerFactory.ALL)
            }.getOrDefault(emptyList())
            if (all.isNotEmpty()) {
                all
            } else {
                val videos = runCatching {
                    searchWithFilter(query, YoutubeSearchQueryHandlerFactory.VIDEOS)
                }.getOrDefault(emptyList())
                if (videos.isNotEmpty()) {
                    videos
                } else {
                    searchWithFilter(query, YoutubeSearchQueryHandlerFactory.MUSIC_SONGS)
                }
            }
        }
    }

    private fun searchWithFilter(query: String, filter: String): List<YouTubeSearchResult> {
        val handler = YoutubeSearchQueryHandlerFactory.getInstance()
            .fromQuery(query, listOf(filter), null)
        val info = SearchInfo.getInfo(ServiceList.YouTube, handler)
        return info.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { item ->
                val videoId = videoIdFromUrl(item.url) ?: return@mapNotNull null
                val durationSec = item.duration.takeIf { it > 0 } ?: 0L
                YouTubeSearchResult(
                    id = videoId,
                    title = item.name ?: "Desconocido",
                    author = item.uploaderName ?: "Artista desconocido",
                    durationText = formatDurationText(durationSec),
                    durationMs = durationSec * 1000L,
                    thumbnailUrl = item.thumbnails
                        .maxByOrNull { it.height }
                        ?.url
                        ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                )
            }
    }

    private fun videoIdFromUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return Regex("(?:v=|/shorts/|youtu\\.be/)([A-Za-z0-9_-]{11})")
            .find(url)?.groupValues?.get(1)
    }

    private fun formatDurationText(totalSeconds: Long): String {
        if (totalSeconds <= 0) return "0:00"
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(java.util.Locale.US, "%d:%02d", m, s)
    }

    /**
     * Obtiene los flujos de audio del vídeo con NewPipe Extractor, que mantiene la lógica
     * de YouTube (clientes, firmas y tokens) actualizada.
     */
    suspend fun extractAudioTrack(videoIdOrUrl: String): Result<YouTubeTrackInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val videoId = extractVideoId(videoIdOrUrl)
                ?: throw IllegalArgumentException("ID de video no válido: $videoIdOrUrl")

            val info = StreamInfo.getInfo(
                ServiceList.YouTube,
                "https://www.youtube.com/watch?v=$videoId"
            )

            val audioStreams = info.audioStreams
                .filter { it.isUrl && it.content.isNotBlank() }
                .map { stream ->
                    AudioStream(
                        url = stream.content,
                        quality = stream.quality ?: "",
                        bitrate = stream.averageBitrate.takeIf { it > 0 }?.toLong() ?: stream.bitrate.toLong(),
                        codec = stream.codec ?: ""
                    )
                }
                .sortedWith(
                    // M4A (AAC) primero: lo decodifican todos los dispositivos
                    compareByDescending<AudioStream> { it.codec.contains("mp4a") || it.codec.contains("m4a") }
                        .thenByDescending { it.bitrate }
                )

            if (audioStreams.isEmpty()) {
                throw Exception("No se encontraron streams de audio para $videoId")
            }

            // Flujos con audio y vídeo incluidos, sin depender del reproductor incrustado.
            // Se limita a 720p para que el head unit reproduzca con fluidez.
            val allVideoCandidates = info.videoStreams.filter { it.isUrl && it.content.isNotBlank() }
            val videoStreams = allVideoCandidates
                .filter { heightOf(it.resolution) in 1..MAX_VIDEO_HEIGHT }
                .ifEmpty { allVideoCandidates }
                .sortedWith(
                    compareByDescending<org.schabi.newpipe.extractor.stream.VideoStream> {
                        it.format == MediaFormat.MPEG_4
                    }.thenByDescending { heightOf(it.resolution) }
                )
                .map { stream ->
                    val isMp4 = stream.format == MediaFormat.MPEG_4
                    YouTubeVideoStream(
                        url = stream.content,
                        mimeType = if (isMp4) "video/mp4" else "video/webm",
                        extension = if (isMp4) "mp4" else "webm",
                        resolution = stream.resolution ?: ""
                    )
                }

            YouTubeTrackInfo(
                id = videoId,
                title = info.name ?: "YouTube Audio",
                author = info.uploaderName ?: "YouTube",
                audioStreams = audioStreams,
                videoStreams = videoStreams,
                videoUrls = videoStreams.map { it.url }
            )
        }
    }

    private fun heightOf(resolution: String): Int =
        Regex("(\\d+)").find(resolution)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    private fun extractVideoId(input: String): String? {
        if (input.length == 11 && !input.contains("/")) return input
        val pattern = Pattern.compile("(?<=watch\\?v=|/videos/|embed/|youtu.be/|/v/|/e/|watch\\?v%3D|watch\\?feature=player_embedded&v=|%2Fvideos%2F|embed%2F)([^#&?\\n]+)")
        val matcher = pattern.matcher(input)
        return if (matcher.find()) matcher.group(1) else null
    }

}
