package com.seldia.music.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Pista guardada en la biblioteca local (favoritos / playlists / caché de metadatos). */
@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String, // clave compuesta "SOURCE|id"
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkUrl: String?,
    val streamUrl: String?,
    val uri: String?,
    val source: String,
    val videoId: String?,
    val providerToken: String?,
    val isFavorite: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    indices = [Index("playlistId"), Index("trackId")]
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackId: String,
    val position: Int
)

/** Entrada del historial de reproducción (snapshot desnormalizado de la pista). */
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkUrl: String?,
    val streamUrl: String?,
    val uri: String?,
    val source: String,
    val videoId: String?,
    val providerToken: String?,
    val playedAt: Long
)
