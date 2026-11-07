package com.seldia.music.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.seldia.music.R
import com.seldia.music.SeldiaApp
import com.seldia.music.data.model.Track
import com.seldia.music.di.AppContainer
import com.seldia.music.ui.adapters.TrackTableAdapter
import com.seldia.music.util.OfflinePrefs
import com.seldia.music.util.showAddToPlaylistDialog
import com.seldia.music.util.showCreatePlaylistDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Base para fragmentos que muestran listas de pistas.
 * Centraliza reproducción, favoritos, cola, descarga y "añadir a playlist".
 */
abstract class TrackListFragment : Fragment() {

    protected val container: AppContainer
        get() = (requireActivity().application as SeldiaApp).container

    protected val playerVm: PlayerViewModel by activityViewModels()
    protected val libraryVm: LibraryViewModel by activityViewModels()

    protected lateinit var adapter: TrackTableAdapter

    private var pendingPermissionAction: (() -> Unit)? = null
    private var pendingDownloadAction: (() -> Unit)? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) pendingPermissionAction?.invoke()
            pendingPermissionAction = null
        }

    private val writePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val action = pendingDownloadAction
            pendingDownloadAction = null
            if (granted) {
                action?.invoke()
            } else {
                toast(getString(R.string.download_permission_denied))
            }
        }

    /**
     * Solicita el permiso de lectura de música local si es necesario.
     * API 27-32 -> READ_EXTERNAL_STORAGE ; API 33+ -> READ_MEDIA_AUDIO.
     */
    protected fun requestStoragePermission(then: () -> Unit) {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(requireContext(), permission) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            then()
        } else {
            pendingPermissionAction = then
            permissionLauncher.launch(permission)
        }
    }

    protected fun makeAdapter(
        onRemove: ((Track, Int) -> Unit)? = null,
        onDeleteLocal: ((Track) -> Unit)? = null
    ): TrackTableAdapter {
        adapter = TrackTableAdapter(
            onClick = ::onTrackClick,
            onFavoriteToggle = ::onFavoriteToggle,
            onAddToQueue = { playerVm.addToQueue(it) },
            onAddToPlaylist = ::promptAddToPlaylist,
            onRemove = onRemove,
            onDownload = ::promptDownloadTrack,
            onDeleteLocal = onDeleteLocal
        )
        observeDownloads()
        observeDownloadedKeys()
        observePlayingKey()
        return adapter
    }

    private fun onFavoriteToggle(track: Track) {
        libraryVm.toggleFavorite(track)
        // Descarga automática: ÚNICAMENTE el flujo de audio (M4A/WEBM), sin vídeo.
        if (OfflinePrefs.autoDownloadEnabled && track.source == com.seldia.music.data.model.Source.YOUTUBE) {
            container.mediaDownloadManager.autoDownloadAudio(track)
        }
    }

    /** Resalta en la tabla la pista que está sonando (flujo transparente). */
    private fun observePlayingKey() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                playerVm.state.collect { state ->
                    if (::adapter.isInitialized) {
                        adapter.setPlayingKey(state.currentTrack?.key)
                    }
                }
            }
        }
    }

    private fun onTrackClick(_track: Track, index: Int) {
        // Reproducción 100% interna para todas las fuentes (YouTube y Local)
        playerVm.playQueue(adapter.snapshotList(), index)
    }

    protected fun promptDownloadTrack(track: Track) {
        // Descarga directa y silenciosa: SOLO audio (M4A), sin diálogo de formato.
        // Sin conexión no hay transferencia posible; en modo automático/offline
        // tampoco se procesa vídeo jamás.
        if (!container.networkConnectivityManager.isCurrentlyOnline()) {
            toast(getString(R.string.download_offline_blocked))
            return
        }
        if (track.source == com.seldia.music.data.model.Source.LOCAL) {
            toast(getString(R.string.download_already_local))
            return
        }
        // Ya descargada (URI enlazada en Room): no duplicar el archivo.
        viewLifecycleOwner.lifecycleScope.launch {
            val already = runCatching {
                container.libraryRepository.findDownloadedUri(track.key)
            }.getOrNull()
            if (already != null) {
                toast(getString(R.string.download_already_local))
                return@launch
            }
            toast(getString(R.string.download_enqueued, track.title))
            withLegacyWritePermission {
                container.mediaDownloadManager.download(track, com.seldia.music.playback.DownloadFormat.AUDIO)
            }
        }
    }

    /**
     * En API 29+ MediaStore no necesita WRITE_EXTERNAL_STORAGE. En API 27-28
     * el proveedor todavía lo exige, así que se solicita antes de descargar.
     */
    private fun withLegacyWritePermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            action()
            return
        }
        val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(requireContext(), permission) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            action()
        } else {
            pendingDownloadAction = action
            writePermissionLauncher.launch(permission)
        }
    }

    protected fun observeFavorites() {
        container.libraryRepository.observeFavorites().observe(viewLifecycleOwner) { favs ->
            adapter.updateFavorites(favs.map { it.key }.toSet())
        }
    }

    protected fun observeDownloads() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.mediaDownloadManager.downloadStates.collect { states ->
                    if (::adapter.isInitialized) {
                        adapter.updateDownloadStates(states)
                    }
                }
            }
        }
    }

    /** Observa las pistas ya descargadas y persistidas en Room para mostrar el estado rojo en las listas. */
    protected fun observeDownloadedKeys() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.libraryRepository.observeDownloadedTrackKeys().observe(viewLifecycleOwner) { keys ->
                    if (::adapter.isInitialized) {
                        adapter.updateDownloadedKeys(keys)
                    }
                }
            }
        }
    }

    private fun promptAddToPlaylist(track: Track) {
        viewLifecycleOwner.lifecycleScope.launch {
            val playlists = withContext(Dispatchers.IO) { container.libraryRepository.getPlaylists() }
            showAddToPlaylistDialog(
                requireContext(),
                playlists,
                onCreateNew = {
                    showCreatePlaylistDialog(requireContext()) { name ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            val id = container.libraryRepository.createPlaylist(name)
                            container.libraryRepository.addTrackToPlaylist(id, track)
                            toast("Añadido a \"$name\"")
                        }
                    }
                },
                onPick = { pl ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        container.libraryRepository.addTrackToPlaylist(pl.id, track)
                        toast("Añadido a \"${pl.name}\"")
                    }
                }
            )
        }
    }

    protected fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
