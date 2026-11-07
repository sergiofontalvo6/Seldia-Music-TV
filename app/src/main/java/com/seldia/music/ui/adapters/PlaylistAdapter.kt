package com.seldia.music.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import coil.load
import com.seldia.music.R
import com.seldia.music.data.db.AppDatabase
import com.seldia.music.data.db.sanitizeArtworkUrl
import com.seldia.music.data.model.Playlist
import com.seldia.music.databinding.ItemPlaylistBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

class PlaylistAdapter(
    private val onClick: (Playlist) -> Unit,
    private val onRename: (Playlist) -> Unit,
    private val onDelete: (Playlist) -> Unit,
    private val db: AppDatabase? = null
) : ListAdapter<Playlist, PlaylistAdapter.VH>(PlaylistDiffCallback()) {

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val coverCache = mutableMapOf<Long, String?>()
    private val coverCounts = mutableMapOf<Long, Int>()
    private val pending = mutableSetOf<Long>()

    fun submit(list: List<Playlist>) {
        submitList(list)
        list.forEach { playlist ->
            val prevCount = coverCounts[playlist.id]
            val countChanged = prevCount != playlist.trackCount
            coverCounts[playlist.id] = playlist.trackCount
            // Reintenta si aún no hay carátula válida o cambió la composición.
            if (coverCache[playlist.id].isNullOrBlank() || countChanged) {
                ensureCover(playlist.id)
            }
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: androidx.recyclerview.widget.RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        if (!scope.isActive) scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    override fun onDetachedFromRecyclerView(recyclerView: androidx.recyclerview.widget.RecyclerView) {
        scope.cancel()
        pending.clear()
        super.onDetachedFromRecyclerView(recyclerView)
    }

    private fun ensureCover(playlistId: Long) {
        if (!pending.add(playlistId)) return
        val database = db ?: run {
            // Sin DB no se puede resolver carátula; se abandona el intento.
            pending.remove(playlistId)
            return
        }
        scope.launch(Dispatchers.IO) {
            val rows = database.playlistDao().getPlaylistTracksOnce(playlistId)
            val track = when {
                rows.isEmpty() -> null
                else -> {
                    val withArt = rows.filter { !it.artworkUrl.isNullOrBlank() }
                    if (withArt.isEmpty()) {
                        // Regla por defecto: carátula de la primera canción agregada.
                        rows.firstOrNull()
                    } else {
                        // Dinámica: carátula aleatoria entre las que tienen imagen.
                        withArt.randomOrNull(Random)
                    }
                }
            }
            val url = sanitizeArtworkUrl(track?.artworkUrl)
            withContext(Dispatchers.Main) {
                pending.remove(playlistId)
                if (coverCache[playlistId] != url) {
                    coverCache[playlistId] = url
                    val pos = currentList.indexOfFirst { it.id == playlistId }
                    if (pos >= 0) notifyItemChanged(pos)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemPlaylistBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val binding: ItemPlaylistBinding) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {

        fun bind(playlist: Playlist) {
            val url = coverCache[playlist.id]
            if (url.isNullOrBlank()) {
                // Placeholder: icono centrado en gris (nunca bloque rojo sólido).
                binding.ivArtwork.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE)
                binding.ivArtwork.setPadding(10, 10, 10, 10)
                binding.ivArtwork.setColorFilter(
                    androidx.core.content.ContextCompat.getColor(binding.root.context, R.color.text_secondary)
                )
                binding.ivArtwork.setImageResource(R.drawable.ic_playlist)
            } else {
                // Carátula dinámica: sin tinte ni fondo sólido (evita el bloque rojo).
                binding.ivArtwork.setColorFilter(null)
                binding.ivArtwork.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP)
                binding.ivArtwork.setPadding(0, 0, 0, 0)
                binding.ivArtwork.load(url) {
                    size(128, 128)
                    placeholder(R.drawable.ic_playlist)
                    error(R.drawable.ic_playlist)
                    crossfade(false)
                }
            }
            val safeName = playlist.name.trim().takeIf {
                it.isNotEmpty() && !it.equals("<unknown>", ignoreCase = true) &&
                    !it.equals("unknown", ignoreCase = true)
            } ?: "Lista sin nombre"
            binding.tvName.text = safeName
            binding.tvCount.text = binding.root.context.resources
                .getQuantityString(R.plurals.track_count, playlist.trackCount, playlist.trackCount)

            binding.root.setOnClickListener { onClick(playlist) }
            binding.btnMore.setOnClickListener {
                val popup = PopupMenu(binding.root.context, it)
                popup.menu.add(0, MENU_RENAME, 0, R.string.rename)
                popup.menu.add(0, MENU_DELETE, 1, R.string.delete)
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        MENU_RENAME -> onRename(playlist)
                        MENU_DELETE -> onDelete(playlist)
                    }
                    true
                }
                popup.show()
            }
        }
    }

    private class PlaylistDiffCallback : DiffUtil.ItemCallback<Playlist>() {
        override fun areItemsTheSame(oldItem: Playlist, newItem: Playlist): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Playlist, newItem: Playlist): Boolean =
            oldItem == newItem
    }

    companion object {
        private const val MENU_RENAME = 1
        private const val MENU_DELETE = 2
    }
}
