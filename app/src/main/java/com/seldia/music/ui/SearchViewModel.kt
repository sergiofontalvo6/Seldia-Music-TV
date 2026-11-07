package com.seldia.music.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.seldia.music.SeldiaApp
import com.seldia.music.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Búsqueda unificada y transparente:
 * - En línea → consulta directa a YouTube vía NewPipe Extractor.
 * - Sin conexión → filtra automáticamente la biblioteca offline (MediaStore + Room).
 */
class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as SeldiaApp).container

    private val _results = MutableLiveData<List<Track>>(emptyList())
    val results: LiveData<List<Track>> = _results

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _message = MutableLiveData<String?>(null)
    val message: LiveData<String?> = _message

    /** true cuando la última búsqueda se resolvió en modo offline. */
    private val _offlineMode = MutableLiveData(!container.networkConnectivityManager.isCurrentlyOnline())
    val offlineMode: LiveData<Boolean> = _offlineMode

    fun search(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _loading.value = true
            _message.value = null
            try {
                if (container.networkConnectivityManager.isCurrentlyOnline()) {
                    _offlineMode.value = false
                    container.youtubeRepository.search(query)
                        .onSuccess { _results.value = it }
                        .onFailure { e ->
                            // Interceptor centralizado ya entrega mensajes amigables; sin códigos crudos.
                            _message.value = com.seldia.music.data.network.NetworkError.friendlyMessage(e)
                            _results.value = emptyList()
                        }
                } else {
                    // Conmutación transparente: búsqueda sobre la biblioteca offline.
                    _offlineMode.value = true
                    _message.value = "Sin conexión · resultados de tu dispositivo"
                    val all = withContext(Dispatchers.IO) {
                        container.offlineMusicRepository.loadTracks()
                    }
                    _results.value = all.filter {
                        it.title.contains(query, ignoreCase = true) ||
                            it.artist.contains(query, ignoreCase = true) ||
                            it.album.contains(query, ignoreCase = true)
                    }
                }
            } finally {
                _loading.value = false
            }
        }
    }

    /** Limpia el estado cuando el campo queda vacío. */
    fun clear() {
        _results.value = emptyList()
        _message.value = null
        _loading.value = false
    }
}
