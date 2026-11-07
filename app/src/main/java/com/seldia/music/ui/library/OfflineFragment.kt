package com.seldia.music.ui.library

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.seldia.music.R
import com.seldia.music.data.model.Track
import com.seldia.music.ui.TableListFragment
import com.seldia.music.util.showConfirmDialog
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

    private fun confirmDelete(track: Track) {
        showConfirmDialog(
            requireContext(),
            getString(R.string.offline_delete_title),
            getString(R.string.offline_delete_message, track.title)
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
