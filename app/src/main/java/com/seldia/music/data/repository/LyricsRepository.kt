package com.seldia.music.data.repository

import android.util.LruCache
import com.google.gson.JsonParser
import com.seldia.music.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Una línea de letra con marca de tiempo (formato LRC de LRCLIB). */
data class LyricLine(
    val timeMs: Long,
    val text: String
)

/** Resultado de una búsqueda de letra: sincronizada (LRC) o texto plano. */
sealed class LyricsResult {
    data class Synced(val lines: List<LyricLine>) : LyricsResult()
    data class Plain(val text: String) : LyricsResult()
}

/**
 * Repositorio ligero para obtener letras desde LRCLIB.
 * Conserva las marcas de tiempo cuando LRCLIB entrega `syncedLyrics`,
 * lo que permite el resaltado karaoke sincronizado con la reproducción.
 * Incluye una caché LRU pequeña en memoria para el head unit.
 */
class LyricsRepository {

    private val cache = LruCache<String, LyricsResult>(15)

    suspend fun getLyrics(track: Track): Result<LyricsResult> = withContext(Dispatchers.IO) {
        runCatching {
            cache.get(track.key)?.let { return@runCatching it }

            val cleanedTitle = cleanQueryText(track.title)
            val cleanedArtist = cleanQueryText(track.artist)
            val primaryQuery = buildString {
                append(cleanedTitle)
                if (cleanedArtist.isNotBlank() &&
                    !cleanedTitle.contains(cleanedArtist, ignoreCase = true) &&
                    !cleanedArtist.equals("Artista desconocido", ignoreCase = true)
                ) {
                    append(" ").append(cleanedArtist)
                }
            }.trim()

            val result = fetchFromLrcLib(primaryQuery)
                ?: if (cleanedTitle != primaryQuery) fetchFromLrcLib(cleanedTitle) else null

            if (result == null) {
                throw Exception("No se encontró letra")
            }
            cache.put(track.key, result)
            result
        }
    }

    private fun fetchFromLrcLib(query: String): LyricsResult? {
        if (query.isBlank()) return null
        val encoded = URLEncoder.encode(query, "UTF-8")
        val conn = URL("https://lrclib.net/api/search?q=$encoded").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", "SeldiaMusic/1.0 (Android)")
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val array = JsonParser.parseString(body).takeIf { it.isJsonArray }?.asJsonArray ?: return null

            var bestPlain: String? = null
            for (element in array) {
                val obj = element.asJsonObject
                val synced = obj.get("syncedLyrics")?.takeIf { !it.isJsonNull }?.asString
                val parsed = synced?.let { parseLrc(it) }
                if (parsed != null && parsed.isNotEmpty()) {
                    return LyricsResult.Synced(parsed)
                }
                if (bestPlain == null) {
                    bestPlain = obj.get("plainLyrics")?.takeIf { !it.isJsonNull }?.asString?.trim()
                }
            }
            return bestPlain?.takeIf { it.isNotBlank() }?.let { LyricsResult.Plain(it) }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Parsea un documento LRC (`[mm:ss.xx] texto`). Admite varias etiquetas
     * por línea (`[t1][t2] texto`) y `[offset:+/-ms]` global.
     */
    private fun parseLrc(lrc: String): List<LyricLine>? {
        return runCatching {
            val tag = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
            val offsetRegex = Regex("\\[offset:([+-]?\\d+)]")
            val offsetMs = offsetRegex.find(lrc)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

            val out = ArrayList<LyricLine>()
            lrc.lineSequence().forEach { raw ->
                val matches = tag.findAll(raw).toList()
                if (matches.isEmpty()) return@forEach
                val text = raw.substring(matches.last().range.last + 1).trim()
                matches.forEach { m ->
                    val min = m.groupValues[1].toLongOrNull() ?: return@forEach
                    val sec = m.groupValues[2].toLongOrNull() ?: return@forEach
                    val frac = m.groupValues[3]
                    val millis = when (frac.length) {
                        0 -> 0L
                        1 -> frac.toLong() * 100
                        2 -> frac.toLong() * 10
                        else -> frac.take(3).toLong()
                    }
                    val ms = min * 60_000 + sec * 1_000 + millis - offsetMs
                    out.add(LyricLine(ms.coerceAtLeast(0L), text))
                }
            }
            out.sortBy { it.timeMs }
            // Elimina líneas duplicadas consecutivas generadas por etiquetas repetidas.
            out.distinctBy { it.timeMs to it.text }
        }.getOrNull()
    }

    private fun cleanQueryText(raw: String): String {
        return raw
            .replace(Regex("\\([^)]*\\)"), " ")
            .replace(Regex("\\[[^]]*]"), " ")
            .replace(
                Regex("(?i)\\b(official|video|audio|lyrics?|visualizer|hd|4k|hq|remastered|feat\\.?|ft\\.?)\\b"),
                " "
            )
            .replace(Regex("[|\u0000-\u001F]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
