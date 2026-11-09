package com.seldia.music.di

import android.content.Context
import androidx.room.Room
import com.seldia.music.data.backup.BackupManager
import com.seldia.music.data.db.AppDatabase
import com.seldia.music.data.extractor.YouTubeAudioExtractor
import com.seldia.music.data.network.NetworkConnectivityManager
import com.seldia.music.data.offline.OfflineMusicRepository
import com.seldia.music.data.repository.LibraryRepository
import com.seldia.music.data.repository.LyricsRepository
import com.seldia.music.data.repository.YouTubeRepository
import com.seldia.music.playback.MusicPlayer

/**
 * Contenedor de dependencias manual (sin Hilt) para mantener un APK ligero
 * y reducir el trabajo de kapt en un dispositivo de 1 GB de RAM.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "seldia_music.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    val networkConnectivityManager: NetworkConnectivityManager by lazy {
        NetworkConnectivityManager(appContext)
    }

    val libraryRepository: LibraryRepository by lazy {
        LibraryRepository(database)
    }

    val offlineMusicRepository: OfflineMusicRepository by lazy {
        OfflineMusicRepository(appContext, database)
    }

    val youtubeRepository: YouTubeRepository by lazy {
        YouTubeRepository(YouTubeAudioExtractor())
    }

    val lyricsRepository: LyricsRepository by lazy {
        LyricsRepository()
    }

    val backupManager: BackupManager by lazy {
        BackupManager(appContext, database)
    }

    val spotifyPlaylistImporter: com.seldia.music.data.spotify.SpotifyPlaylistImporter by lazy {
        com.seldia.music.data.spotify.SpotifyPlaylistImporter(libraryRepository)
    }

    val musicPlayer: MusicPlayer by lazy {
        MusicPlayer(
            context = appContext,
            youtubeRepository = youtubeRepository,
            libraryRepository = libraryRepository,
            network = networkConnectivityManager
        )
    }
}
