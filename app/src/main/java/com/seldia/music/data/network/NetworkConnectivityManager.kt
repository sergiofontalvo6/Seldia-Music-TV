package com.seldia.music.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Observa la conectividad del dispositivo y expone el estado en línea/sin conexión
 * como un [StateFlow] para que la UI y el reproductor conmuten de forma transparente.
 *
 * - `isOnline == true`  → catálogo YouTube vía NewPipe Extractor.
 * - `isOnline == false` → modo offline: solo pistas descargadas / MediaStore.
 */
class NetworkConnectivityManager(context: Context) {

    private val appContext: Context = context.applicationContext

    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isOnline = MutableStateFlow(currentOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh()
        override fun onLost(network: Network) = refresh()
        override fun onUnavailable() = refresh()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = refresh()
    }

    init {
        runCatching {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        }.onFailure {
            Log.w(TAG, "No se pudo registrar NetworkCallback; se usará consulta puntual", it)
        }
    }

    /** Consulta puntual y síncrona (segura en cualquier hilo). */
    fun isCurrentlyOnline(): Boolean = currentOnline().also { _isOnline.value = it }

    private fun refresh() {
        _isOnline.value = currentOnline()
        Log.d(TAG, "Conectividad actualizada: ${if (_isOnline.value) "en línea" else "sin conexión"}")
    }

    private fun currentOnline(): Boolean = runCatching {
        val network = connectivityManager.activeNetwork ?: return@runCatching false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return@runCatching false
        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        // Se acepta la capacidad INTERNET aunque el portal cautivo aún no valide la red:
        // en emuladores VALIDATED puede tardar o faltar; el failover offline sigue siendo automático.
        val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        Log.v(TAG, "caps: internet=$hasInternet validated=$validated transports=${caps.describeContents()}")
        hasInternet
    }.getOrDefault(false)

    fun release() {
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
    }

    private companion object {
        const val TAG = "NetworkConnectivity"
    }
}
