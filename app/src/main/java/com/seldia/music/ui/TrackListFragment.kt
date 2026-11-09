package com.seldia.music.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import android.view.ActionMode
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.seldia.music.R
import com.seldia.music.SeldiaApp
import com.seldia.music.data.model.Track
import com.seldia.music.di.AppContainer
import com.seldia.music.ui.adapters.TrackTableAdapter
import com.seldia.music.util.showAddToPlaylistDialog
import com.seldia.music.util.showCreatePlaylistDialog
import com.seldia.music.util.showModernConfirmDialog
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

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) pendingPermissionAction?.invoke()
            pendingPermissionAction = null
        }

    // TV: sin descargas de música (solo streaming). Se conserva el launcher
    // de permiso de lectura local para la biblioteca offline existente.

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
            onDeleteLocal = onDeleteLocal,
            onRequestSelectionMode = { startSelectionMode(it) },
            onSelectionChanged = { keys -> updateSelectionTitle(keys.size) }
        )
        observeFavorites()
        observePlayingKey()
        return adapter
    }

    // ---- Modo de selección múltiple (ActionMode superior) ----

    protected var actionMode: ActionMode? = null
        private set

    /** Adjunta drag & drop con el asidero al RecyclerView indicado. */
    protected fun attachDragAndDrop(recycler: androidx.recyclerview.widget.RecyclerView) {
        val touchHelper = androidx.recyclerview.widget.ItemTouchHelper(
            object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
                androidx.recyclerview.widget.ItemTouchHelper.UP or androidx.recyclerview.widget.ItemTouchHelper.DOWN,
                0
            ) {
                override fun onMove(
                    rv: androidx.recyclerview.widget.RecyclerView,
                    viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
                    target: androidx.recyclerview.widget.RecyclerView.ViewHolder
                ): Boolean {
                    if (!::adapter.isInitialized) return false
                    adapter.moveItem(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                    return true
                }

                override fun onSwiped(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, direction: Int) = Unit

                override fun isItemViewSwipeEnabled(): Boolean = false

                override fun isLongPressDragEnabled(): Boolean = false

                override fun clearView(rv: androidx.recyclerview.widget.RecyclerView, viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder) {
                    super.clearView(rv, viewHolder)
                    if (!::adapter.isInitialized) return
                    adapter.publishOrderChanged()
                    onTracksReordered(adapter.snapshotList())
                }
            }
        )
        touchHelper.attachToRecyclerView(recycler)
        if (::adapter.isInitialized) {
            adapter.onDragStartRequested = { holder -> touchHelper.startDrag(holder) }
        }
    }

    /** Persiste el nuevo orden tras el arrastre (cada vista decide su fuente). */
    protected open fun onTracksReordered(tracks: List<Track>) = Unit

    /** Entrada a selección múltiple desde el menú overflow o pulsación larga. */
    fun startSelectionMode(firstTrack: Track? = null) {
        if (!isAdded || !::adapter.isInitialized) return
        adapter.setSelectionMode(true)
        if (firstTrack != null) adapter.select(firstTrack)
        val activity = requireActivity()
        if (activity is androidx.appcompat.app.AppCompatActivity) {
            actionMode = activity.startActionMode(selectionCallback)
            updateSelectionTitle(adapter.selectedKeys.size)
        }
    }

    fun finishSelectionMode() {
        actionMode?.finish()
        finishSelection()
    }

    private fun finishSelection() {
        if (!isAdded && view == null) {
            actionMode = null
            return
        }
        if (::adapter.isInitialized) adapter.setSelectionMode(false)
        actionMode = null
    }

    private fun updateSelectionTitle(count: Int) {
        if (!isAdded) return
        runCatching {
            actionMode?.title = resources.getQuantityString(R.plurals.selection_count, count, count)
        }
        actionMode?.subtitle = if (count == 0) null else ""
    }

    private val selectionCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.menu_selection, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val selected = selectedTracks()
            if (selected.isEmpty()) return false
            return when (item.itemId) {
                R.id.action_delete_selected -> {
                    batchDeleteSelected(selected)
                    mode.finish()
                    true
                }
                R.id.action_add_to_playlist -> {
                    batchAddToPlaylist(selected)
                    true
                }
                R.id.action_fav_toggle -> {
                    batchFavToggle(selected)
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            finishSelection()
        }
    }

    protected fun selectedTracks(): List<Track> {
        if (!::adapter.isInitialized || adapter.selectedKeys.isEmpty()) return emptyList()
        val keys = adapter.selectedKeys
        return adapter.snapshotList().filter { it.key in keys }
    }

    /** Elimina según la sección: desde playlist → quitar; offline → borrar archivo; resto → contexto. */
    protected open fun batchDeleteSelected(selected: List<Track>) {
        if (!isAdded || view == null) return
        showModernConfirmDialog(
            context = requireContext(),
            title = getString(R.string.delete_selected),
            message = resources.getQuantityString(R.plurals.confirm_delete_selected, selected.size, selected.size),
            confirmText = getString(R.string.delete)
        ) {
            val remove = currentRemoveAction()
            val deleteLocal = currentDeleteAction()
            when {
                remove != null -> selected.forEachIndexed { idx, track -> remove(track, idx) }
                deleteLocal != null -> selected.forEach { deleteLocal(it) }
                else -> {
                    viewLifecycleOwner.lifecycleScope.launch {
                        selected.forEach { libraryVm.toggleFavorite(it) }
                        toast(getString(R.string.delete_selected))
                    }
                }
            }
        }
    }

    protected open fun currentRemoveAction(): ((Track, Int) -> Unit)? = null

    protected open fun currentDeleteAction(): ((Track) -> Unit)? = null

    protected fun batchAddToPlaylist(selected: List<Track>) {
        if (selected.isEmpty()) return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val playlists = container.libraryRepository.getPlaylists()
            withContext(Dispatchers.Main) {
                com.seldia.music.util.showAddToPlaylistDialog(
                    requireContext(),
                    playlists,
                    onCreateNew = {
                        com.seldia.music.util.showCreatePlaylistDialog(requireContext()) { name ->
                            viewLifecycleOwner.lifecycleScope.launch {
                                val id = container.libraryRepository.createPlaylist(name)
                                selected.forEach { container.libraryRepository.addTrackToPlaylist(id, it) }
                                toast(resources.getQuantityString(com.seldia.music.R.plurals.playlist_count, selected.size, selected.size))
                            }
                        }
                    },
                    onPick = { pl ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            selected.forEach { container.libraryRepository.addTrackToPlaylist(pl.id, it) }
                            toast("Añadido a \"${pl.name}\"")
                        }
                    }
                )
            }
        }
    }

    // TV: sin descargas de música (solo streaming).

    protected fun batchFavToggle(selected: List<Track>) {
        viewLifecycleOwner.lifecycleScope.launch {
            selected.forEach { track ->
                val isFav = container.libraryRepository.isFavorite(track)
                if (!isFav) libraryVm.toggleFavorite(track)
            }
            toast(getString(R.string.fav_selected))
        }
    }

    private fun onFavoriteToggle(track: Track) {
        libraryVm.toggleFavorite(track)
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



    protected fun observeFavorites() {
        container.libraryRepository.observeFavorites().observe(viewLifecycleOwner) { favs ->
            adapter.updateFavorites(favs.map { it.key }.toSet())
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
