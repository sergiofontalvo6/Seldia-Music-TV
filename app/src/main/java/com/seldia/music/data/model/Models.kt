package com.seldia.music.data.model

/** Origen de una pista de audio. */
enum class Source { LOCAL, YOUTUBE }

/**
 * Modelo de dominio compartido (independiente de la plataforma).
 * Esta clase es la frontera preparada para una futura versión iOS:
 * no depende de Android, por lo que puede extraerse a un módulo común.
 */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val artworkUrl: String? = null,
    /** URL de reproducción ya resuelta, si el proveedor la entrega. */
    val streamUrl: String? = null,
    /** URI Android de contenido local (content://media/...). */
    val uri: String? = null,
    val source: Source,
    /** ID del vídeo de YouTube. */
    val videoId: String? = null,
    /** Token opcional del proveedor (no usado actualmente; se conserva por compatibilidad de la base de datos). */
    val providerToken: String? = null,
    val isFavorite: Boolean = false,
    /** Fecha de alta (epoch ms) para la columna "Fecha" de la tabla; 0 si se desconoce. */
    val addedAt: Long = 0L
) {
    /** Clave única y estable en la base de datos local. */
    val key: String get() = "${source.name}|$id"

    val subtitle: String
        get() = if (album.isNotBlank()) "$artist · $album" else artist
}

data class Playlist(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val trackCount: Int = 0,
    /** Reproducciones reales del historial (para ordenar de más a menos escuchada). */
    val playCount: Int = 0
)
