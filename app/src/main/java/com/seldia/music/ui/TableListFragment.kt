package com.seldia.music.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import com.seldia.music.R
import com.seldia.music.data.model.Track
import com.seldia.music.databinding.FragmentTableListBinding
import com.seldia.music.util.formatTotalDuration
import com.seldia.music.util.showModernConfirmDialog
import com.seldia.music.util.showRenamePlaylistDialog

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
        attachDragAndDrop(binding.recycler)
        binding.progress.visibility = View.VISIBLE

        binding.btnPlayAll.setOnClickListener {
            if (currentTracks.isNotEmpty()) playerVm.playQueue(currentTracks, 0)
        }
        binding.btnMoreOptions.setOnClickListener { showHeaderOverflow(it) }
    }

    private fun showHeaderOverflow(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, MENU_RENAME, 0, R.string.rename_playlist)
            .isVisible = playlistIdForActions() != null
        popup.menu.add(0, MENU_SELECT, 1, R.string.select_songs)
        popup.menu.add(0, MENU_DELETE, 2, R.string.delete_playlist)
            .isVisible = playlistIdForActions() != null
        popup.menu.add(0, MENU_SHUFFLE, 3, R.string.shuffle_all)
        popup.menu.add(0, MENU_QUEUE_ALL, 4, R.string.add_all_to_queue)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_RENAME -> promptRenamePlaylist()
                MENU_SELECT -> startSelectionMode()
                MENU_DELETE -> promptDeletePlaylist()
                MENU_SHUFFLE -> {
                    if (currentTracks.isNotEmpty()) {
                        playerVm.setShuffleEnabled(true)
                        playerVm.playQueue(currentTracks, 0)
                    }
                }
                MENU_QUEUE_ALL -> {
                    if (currentTracks.isEmpty()) return@setOnMenuItemClickListener true
                    currentTracks.forEach { playerVm.addToQueue(it) }
                    toast(getString(R.string.added_to_queue_count, currentTracks.size))
                }
            }
            true
        }
        popup.show()
    }

    /** ID de playlist si es editable (renombrar/borrar); null si no. */
    protected open fun playlistIdForActions(): Long? = null

    private fun promptRenamePlaylist() {
        val id = playlistIdForActions() ?: return
        val current = playlistNameForActions() ?: ""
        showRenamePlaylistDialog(requireContext(), current) { newName ->
            libraryVm.renamePlaylist(id, newName)
        }
    }

    private fun promptDeletePlaylist() {
        val id = playlistIdForActions() ?: return
        val name = playlistNameForActions() ?: ""
        showModernConfirmDialog(
            requireContext(),
            getString(R.string.delete_playlist),
            getString(R.string.confirm_delete_playlist, name),
            getString(R.string.delete)
        ) {
            libraryVm.deletePlaylist(id)
            parentFragmentManager.popBackStack()
        }
    }

    protected open fun playlistNameForActions(): String? = binding.tvTitleBig.text?.toString()

    private companion object {
        const val MENU_RENAME = 1
        const val MENU_SELECT = 2
        const val MENU_DELETE = 3
        const val MENU_SHUFFLE = 4
        const val MENU_QUEUE_ALL = 5
    }

    /** Acción de "quitar" opcional para el menú contextual (playlists). */
    protected open fun removeAction(): ((Track, Int) -> Unit)? = null

    /** Acción de "eliminar archivo offline" opcional (sección Sin conexión). */
    protected open fun deleteAction(): ((Track) -> Unit)? = null

    override fun currentRemoveAction(): ((Track, Int) -> Unit)? = removeAction()

    override fun currentDeleteAction(): ((Track) -> Unit)? = deleteAction()

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
        runCatching { finishSelectionMode() }
        super.onDestroyView()
        _binding = null
    }
}
