package com.seldia.music.data.backup

import com.seldia.music.data.db.HistoryEntity
import com.seldia.music.data.db.PlaylistEntity
import com.seldia.music.data.db.PlaylistTrackCrossRef
import com.seldia.music.data.db.TrackEntity

/** Formato JSON de copia de seguridad de Seldia Music. */
data class BackupData(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val tracks: List<TrackEntity> = emptyList(),
    val playlists: List<PlaylistEntity> = emptyList(),
    val playlistTracks: List<PlaylistTrackCrossRef> = emptyList(),
    val history: List<HistoryEntity> = emptyList(),
    val prefs: Map<String, String> = emptyMap()
)
