package com.seldia.music.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.seldia.music.R
import com.seldia.music.data.model.Track
import com.seldia.music.databinding.FragmentTableListBinding
import com.seldia.music.util.formatTotalDuration

/**
 * Base para pantallas de lista tabulada estilo desktop (playlist, favoritos,
 * álbum/artista): encabezado con acciones rápidas + tabla #/Título/Álbum/Fecha/Duración.
 */
abstract class TableListFragment : TrackListFragment() {

    protected var _binding: FragmentTableListBinding? = null
    protected val binding get() = _binding!!

    protected var currentTracks: List<Track> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTableListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.recycler.adapter = makeAdapter(onRemove = removeAction(), onDeleteLocal = deleteAction())
        observeFavorites()
        binding.progress.visibility = View.VISIBLE

        binding.btnPlayAll.setOnClickListener {
            if (currentTracks.isNotEmpty()) playerVm.playQueue(currentTracks, 0)
        }
        binding.btnShuffleAll.setOnClickListener {
            if (currentTracks.isNotEmpty()) {
                playerVm.setShuffleEnabled(true)
                playerVm.playQueue(currentTracks, 0)
            }
        }
        binding.btnAddAll.setOnClickListener {
            if (currentTracks.isEmpty()) return@setOnClickListener
            currentTracks.forEach { playerVm.addToQueue(it) }
            toast(getString(R.string.added_to_queue_count, currentTracks.size))
        }
    }

    /** Acción de "quitar" opcional para el menú contextual (playlists). */
    protected open fun removeAction(): ((Track, Int) -> Unit)? = null

    /** Acción de "eliminar archivo offline" opcional (sección Sin conexión). */
    protected open fun deleteAction(): ((Track) -> Unit)? = null

    /** Texto mostrado en el estado vacío. */
    protected abstract fun emptyText(): String

    protected fun kicker(text: String) {
        binding.tvKicker.text = text
    }

    protected fun headerTitle(text: String) {
        binding.tvTitleBig.text = text
    }

    protected fun submitTracks(tracks: List<Track>) {
        currentTracks = tracks
        adapter.submit(tracks)
        binding.progress.visibility = View.GONE
        binding.empty.visibility = if (tracks.isEmpty()) View.VISIBLE else View.GONE
        binding.empty.text = emptyText()
        binding.tvMeta.text = getString(
            R.string.tracks_meta,
            tracks.size,
            formatTotalDuration(tracks.sumOf { it.durationMs })
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
