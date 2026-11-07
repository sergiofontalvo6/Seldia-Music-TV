package com.seldia.music.ui.library

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.seldia.music.R
import com.seldia.music.data.model.Track
import com.seldia.music.ui.TableListFragment
import com.seldia.music.util.showModernConfirmDialog
import kotlinx.coroutines.launch

/**
 * Biblioteca offline: música del dispositivo y pistas descargadas por SeldiaMusic.
 * Sustituye al antiguo concepto "Local" dentro del flujo híbrido Online ⇄ Offline.
 * Permite eliminar canciones descargadas (archivo físico + registros en Room).
 */
class OfflineFragment : TableListFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        kicker(getString(R.string.kicker_library))
        headerTitle(getString(R.string.offline_title))
        requestStoragePermission { loadTracks() }
        // Recargado reactivo: cuando Room notifica descargas/actualizaciones,
        // la lista offline refleja el archivo local sin recarga manual.
        container.libraryRepository.observeDownloadedTrackKeys().observe(viewLifecycleOwner) {
            loadTracks()
        }
    }

    override fun deleteAction(): ((Track) -> Unit)? = ::confirmDelete

    /**
     * Borrado en bloque con UNA sola confirmación para todos los elementos
     * (antes se pedía confirmación por cada pista seleccionada).
     */
    override fun batchDeleteSelected(selected: List<Track>) {
        if (!isAdded || view == null) return
        com.seldia.music.util.showModernConfirmDialog(
            context = requireContext(),
            title = getString(R.string.delete_selected),
            message = resources.getQuantityString(
                com.seldia.music.R.plurals.confirm_delete_selected, selected.size, selected.size
            ),
            confirmText = getString(R.string.delete)
        ) {
            viewLifecycleOwner.lifecycleScope.launch {
                selected.forEach { container.offlineMusicRepository.delete(it) }
                loadTracks()
            }
        }
    }

    private fun confirmDelete(track: Track) {
        showModernConfirmDialog(
            requireContext(),
            getString(R.string.offline_delete_title),
            getString(R.string.offline_delete_message, track.title),
            getString(R.string.delete)
        ) {
            viewLifecycleOwner.lifecycleScope.launch {
                val deleted = container.offlineMusicRepository.delete(track)
                toast(
                    if (deleted) getString(R.string.offline_deleted, track.title)
                    else getString(R.string.offline_delete_failed)
                )
                loadTracks()
            }
        }
    }

    private fun loadTracks() {
        viewLifecycleOwner.lifecycleScope.launch {
            val tracks = container.offlineMusicRepository.loadTracks()
            submitTracks(tracks)
        }
    }

    override fun emptyText(): String = getString(R.string.no_offline_music)
}
