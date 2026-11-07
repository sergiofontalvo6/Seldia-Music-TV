package com.seldia.music.data.db

import com.seldia.music.data.model.Source
import com.seldia.music.data.model.Track

/** Valores centinela de MediaStore / proveedores que no deben guardarse ni mostrarse. */
private val UNKNOWN_TOKENS = setOf("<unknown>", "unknown", "<unknown artist>", "<unknown album>", "<unknown title>", "null")

private fun isUnknownValue(value: String?): Boolean {
    val trimmed = value?.trim().orEmpty()
    if (trimmed.isEmpty()) return true
    return trimmed.lowercase() in UNKNOWN_TOKENS
}

/**
 * Saneado central de metadatos (corrección <unknown>):
 * - title vacío/unknown → "Desconocido"
 * - artist vacío/unknown → "Artista desconocido"
 * - album vacío/unknown → "" (así los grupos por álbum no crean entradas "<unknown>")
 * - artworkUrl vacío/unknown → null
 */
fun sanitizeTitle(raw: String?, default: String = "Desconocido"): String =
    if (isUnknownValue(raw)) default else raw!!.trim()

fun sanitizeArtist(raw: String?, default: String = "Artista desconocido"): String =
    if (isUnknownValue(raw)) default else raw!!.trim()

fun sanitizeAlbum(raw: String?): String =
    if (isUnknownValue(raw)) "" else raw!!.trim()

fun sanitizeArtworkUrl(raw: String?): String? =
    if (isUnknownValue(raw)) null else raw!!.trim().takeIf { it.isNotEmpty() }

fun Track.toEntity(isFavorite: Boolean = false): TrackEntity = TrackEntity(
    id = key,
    title = sanitizeTitle(title),
    artist = sanitizeArtist(artist),
    album = sanitizeAlbum(album),
    durationMs = durationMs.coerceAtLeast(0L),
    artworkUrl = sanitizeArtworkUrl(artworkUrl),
    streamUrl = streamUrl,
    uri = uri,
    source = source.name,
    videoId = videoId,
    providerToken = providerToken,
    isFavorite = isFavorite,
    addedAt = if (addedAt > 0L) addedAt else System.currentTimeMillis()
)

fun TrackEntity.toTrack(): Track {
    val src = runCatching { Source.valueOf(source) }.getOrDefault(Source.LOCAL)
    return Track(
        id = id.removePrefix(src.name + "|"),
        title = sanitizeTitle(title),
        artist = sanitizeArtist(artist),
        album = sanitizeAlbum(album),
        durationMs = durationMs.coerceAtLeast(0L),
        artworkUrl = sanitizeArtworkUrl(artworkUrl),
        streamUrl = streamUrl,
        uri = uri,
        source = src,
        videoId = videoId,
        providerToken = providerToken,
        isFavorite = isFavorite,
        addedAt = addedAt
    )
}

fun HistoryEntity.toTrack(): Track {
    val src = runCatching { Source.valueOf(source) }.getOrDefault(Source.LOCAL)
    return Track(
        id = trackKey.removePrefix(src.name + "|"),
        title = sanitizeTitle(title),
        artist = sanitizeArtist(artist),
        album = sanitizeAlbum(album),
        durationMs = durationMs.coerceAtLeast(0L),
        artworkUrl = sanitizeArtworkUrl(artworkUrl),
        streamUrl = streamUrl,
        uri = uri,
        source = src,
        videoId = videoId,
        providerToken = providerToken,
        addedAt = playedAt
    )
}

fun Track.toHistoryEntity(playedAt: Long = System.currentTimeMillis()): HistoryEntity = HistoryEntity(
    trackKey = key,
    title = sanitizeTitle(title),
    artist = sanitizeArtist(artist),
    album = sanitizeAlbum(album),
    durationMs = durationMs.coerceAtLeast(0L),
    artworkUrl = sanitizeArtworkUrl(artworkUrl),
    streamUrl = streamUrl,
    uri = uri,
    source = source.name,
    videoId = videoId,
    providerToken = providerToken,
    playedAt = playedAt
)
