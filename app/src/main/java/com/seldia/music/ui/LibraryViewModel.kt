package com.seldia.music.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.seldia.music.SeldiaApp
import com.seldia.music.data.model.Playlist
import com.seldia.music.data.model.Track
import kotlinx.coroutines.launch

/** ViewModel de la biblioteca (playlists, favoritos e historial). */
class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as SeldiaApp).container.libraryRepository

    val playlists: LiveData<List<Playlist>> = repository.observePlaylists()
    val favorites: LiveData<List<Track>> = repository.observeFavorites()
    val history: LiveData<List<Track>> = repository.observeHistory()

    fun createPlaylist(name: String) {
        viewModelScope.launch { repository.createPlaylist(name) }
    }

    fun renamePlaylist(id: Long, name: String) {
        viewModelScope.launch { repository.renamePlaylist(id, name) }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch { repository.deletePlaylist(id) }
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch { repository.toggleFavorite(track) }
    }

    fun addTrackToPlaylist(playlistId: Long, track: Track) {
        viewModelScope.launch { repository.addTrackToPlaylist(playlistId, track) }
    }

    fun removeTrackFromPlaylist(playlistId: Long, track: Track) {
        viewModelScope.launch { repository.removeTrackFromPlaylist(playlistId, track) }
    }

    fun clearHistory() {
        viewModelScope.launch { repository.clearHistory() }
    }
}
