package com.seldia.music.data.spotify

import android.util.Log
import com.google.gson.JsonParser
import com.seldia.music.data.network.ApiException
import com.seldia.music.data.network.NetworkError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class SpotifyTrackEntry(
    val title: String,
    val artist: String,
    val durationMs: Long,
    val spotifyId: String? = null
)

data class SpotifyPlaylistData(
    val name: String,
    val tracks: List<SpotifyTrackEntry>,
    val coverUrl: String? = null
)

/**
 * Extrae nombre y pistas de una playlist o álbum público de Spotify sin autenticación.
 *
 * Estrategia principal: página embed (`/embed/{playlist|album}/{id}`) que expone
 * `__NEXT_DATA__` con JSON estructurado (entity + trackList).
 * Fallback: meta tags + filas HTML `TracklistRow_title/subtitle`.
 * No requiere Jsoup: parsing ligero con regex + Gson.
 */
object SpotifyPlaylistParser {

    private const val TAG = "SpotifyParser"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val SPOTIFY_URL_REGEX = Regex("""(playlist|album)/([A-Za-z0-9]{22})""")
    private val PLAYLIST_ID_REGEX = Regex("""playlist/([A-Za-z0-9]{22})""")
    private val NEXT_DATA_REGEX =
        Regex("""<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
    private val OG_TITLE_REGEX = Regex("""property="og:title"\s+content="([^"]+)"""")
    private val TITLE_TAG_REGEX = Regex("""<title>(.*?)</title>""", RegexOption.DOT_MATCHES_ALL)
    private val JSONLD_NAME_REGEX = Regex(""""name"\s*:\s*"([^"]+)"""")
    private val ROW_TITLE_REGEX =
        Regex("""<h3[^>]*TracklistRow_title[^>]*[^>]*>([^<]+)</h3>""")
    private val ROW_SUBTITLE_REGEX =
        Regex("""<h4[^>]*TracklistRow_subtitle[^>]*[^>]*>([^<]+)</h4>""")
    private val ROW_DURATION_REGEX =
        Regex("""<div[^>]*TracklistRow_durationCell[^>]*[^>]*>(\d+):(\d+)</div>""")

    fun extractSpotifyId(urlOrId: String): String? {
        val trimmed = urlOrId.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.matches(Regex("[A-Za-z0-9]{22}"))) return trimmed
        return SPOTIFY_URL_REGEX.find(trimmed)?.groupValues?.get(2)
    }

    /** Tipo de recurso Spotify (`playlist` | `album`). Por defecto playlist. */
    fun extractType(urlOrId: String): String {
        val match = SPOTIFY_URL_REGEX.find(urlOrId.trim())
        return match?.groupValues?.get(1) ?: "playlist"
    }

    fun extractPlaylistId(urlOrId: String): String? {
        // Compatibilidad: solo playlists (o ID desnudo).
        val trimmed = urlOrId.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.matches(Regex("[A-Za-z0-9]{22}"))) return trimmed
        return PLAYLIST_ID_REGEX.find(trimmed)?.groupValues?.get(1)
    }

    suspend fun fetchPlaylist(urlOrId: String): Result<SpotifyPlaylistData> = withContext(Dispatchers.IO) {
        runCatching {
            val spotifyId = extractSpotifyId(urlOrId) ?: throw IllegalArgumentException("URL de Spotify no válida")
            val type = extractType(urlOrId)
            val embedUrl = "https://open.spotify.com/embed/$type/$spotifyId"
            val embedHtml = fetchHtml(embedUrl)
            parseEmbedHtml(embedHtml).getOrElse {
                // Fallback a página principal
                val mainUrl = "https://open.spotify.com/$type/$spotifyId"
                val mainHtml = fetchHtml(mainUrl)
                parseEmbedHtml(mainHtml).getOrElse { fallback ->
                    parseMetaHtml(mainHtml).getOrThrow()
                }
            }
        }
    }

    private fun fetchHtml(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
            setRequestProperty("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
        }
        try {
            val code = connection.responseCode
            if (code != 200) throw ApiException(code, NetworkError.classify(IOException("HTTP $code")).second)
            val input = connection.inputStream
            val text = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
            Log.d(TAG, "Fetched ${url.take(40)} len=${text.length}")
            return text
        } catch (e: java.io.IOException) {
            throw NetworkError.toApiException(e)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseEmbedHtml(html: String): Result<SpotifyPlaylistData> {
        val match = NEXT_DATA_REGEX.find(html) ?: return Result.failure(IllegalStateException("NEXT_DATA no encontrado"))
        val jsonText = match.groupValues[1].trim()
        if (jsonText.isEmpty()) return Result.failure(IllegalStateException("NEXT_DATA vacío"))
        return runCatching {
            val root = JsonParser.parseString(jsonText).asJsonObject
            val entity = root.getAsJsonObject("props")
                .getAsJsonObject("pageProps")
                .getAsJsonObject("state")
                .getAsJsonObject("data")
                .getAsJsonObject("entity")
            val name = entity.get("name")?.takeIf { !it.isJsonNull }?.asString
                ?.trim()?.takeIf { it.isNotEmpty() } ?: entity.get("title")?.asString?.trim()
                ?: extractTitleFromHtml(html)
            val coverUrl = runCatching {
                val sources = entity.getAsJsonObject("coverArt").getAsJsonArray("sources")
                if (sources.size() > 0) sources[0].asJsonObject.get("url")?.asString else null
            }.getOrNull()
            val trackList = entity.getAsJsonArray("trackList")
            val tracks = mutableListOf<SpotifyTrackEntry>()
            for (el in trackList) {
                val obj = el.asJsonObject
                val title = obj.get("title")?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
                val subtitle = obj.get("subtitle")?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
                if (title.isEmpty()) continue
                val durationMs = obj.get("duration")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
                val uri = obj.get("uri")?.asString ?: ""
                val spotifyId = uri.substringAfterLast(":").takeIf { it.isNotEmpty() }
                tracks.add(
                    SpotifyTrackEntry(
                        title = title,
                        artist = if (subtitle.isNotBlank()) subtitle else "Artista desconocido",
                        durationMs = durationMs,
                        spotifyId = spotifyId
                    )
                )
            }
            if (tracks.isEmpty()) throw IllegalStateException("Sin pistas en NEXT_DATA")
            SpotifyPlaylistData(name = name.ifBlank { "Lista Spotify" }, tracks = tracks, coverUrl = coverUrl)
        }
    }

    private fun parseMetaHtml(html: String): Result<SpotifyPlaylistData> {
        return runCatching {
            val name = extractTitleFromHtml(html)
            val titles = ROW_TITLE_REGEX.findAll(html).map { it.groupValues[1].trim() }.toList()
            val subtitles = ROW_SUBTITLE_REGEX.findAll(html).map { it.groupValues[1].trim() }.toList()
            val durations = ROW_DURATION_REGEX.findAll(html).map {
                val min = it.groupValues[1].toLongOrNull() ?: 0L
                val sec = it.groupValues[2].toLongOrNull() ?: 0L
                min * 60_000L + sec * 1000L
            }.toList()
            if (titles.isEmpty()) throw IllegalStateException("No se pudieron extraer pistas de la página")
            val tracks = titles.mapIndexed { idx, title ->
                SpotifyTrackEntry(
                    title = title,
                    artist = subtitles.getOrNull(idx)?.takeIf { it.isNotBlank() } ?: "Artista desconocido",
                    durationMs = durations.getOrNull(idx) ?: 0L
                )
            }
            SpotifyPlaylistData(name = name, tracks = tracks)
        }
    }

    private fun extractTitleFromHtml(html: String): String {
        OG_TITLE_REGEX.find(html)?.let { return decodeHtml(it.groupValues[1]).trim() }
        TITLE_TAG_REGEX.find(html)?.let {
            val raw = decodeHtml(it.groupValues[1].trim())
            val withoutSuffix = raw.substringBefore(" - playlist by").substringBefore(" | Spotify").trim()
            if (withoutSuffix.isNotEmpty()) return withoutSuffix
        }
        JSONLD_NAME_REGEX.find(html)?.let { return decodeHtml(it.groupValues[1]).trim() }
        return "Lista Spotify"
    }

    private fun decodeHtml(value: String): String {
        return value
            .replace("&amp;", "&")
            .replace("&#x2F;", "/")
            .replace("&#x27;", "'")
            .replace("&quot;", "\"")
    }
}
