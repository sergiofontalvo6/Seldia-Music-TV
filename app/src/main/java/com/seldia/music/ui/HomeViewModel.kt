package com.seldia.music.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import com.seldia.music.SeldiaApp
import com.seldia.music.data.model.Track

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as SeldiaApp).container

    val favorites: LiveData<List<Track>> = container.libraryRepository.observeFavorites()

    val favoritesCount: LiveData<Int> = container.libraryRepository.observeFavorites().map { it.size }

    val playlistsCount: LiveData<Int> = container.libraryRepository.observePlaylists().map { it.size }
}
