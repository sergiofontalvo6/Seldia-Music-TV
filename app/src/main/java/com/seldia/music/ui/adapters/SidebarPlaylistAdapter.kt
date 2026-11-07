package com.seldia.music.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import coil.load
import com.seldia.music.R
import com.seldia.music.data.db.AppDatabase
import com.seldia.music.data.db.sanitizeArtworkUrl
import com.seldia.music.data.model.Playlist
import com.seldia.music.databinding.ItemSidebarPlaylistBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** Filas compactas de playlist para la barra lateral desktop (con carátula y estado "activa"). */
class SidebarPlaylistAdapter(
    private val onClick: (Playlist) -> Unit,
    private val db: AppDatabase? = null
) : ListAdapter<Playlist, SidebarPlaylistAdapter.VH>(PlaylistDiffCallback()) {

    private var activeId: Long? = null
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
            if (coverCache[playlist.id].isNullOrBlank() || countChanged) {
                ensureCover(playlist.id)
            }
        }
    }

    /** Mueve un ítem durante drag & drop (posiciones locales, sin tocar DB). */
    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition == toPosition) return
        val list = currentList.toMutableList()
        if (fromPosition !in list.indices || toPosition !in list.indices) return
        list.add(toPosition, list.removeAt(fromPosition))
        // submitList con copy: DiffUtil sigue reconciliando; caché de carátulas intacta.
        submitList(list)
    }

    fun orderedIds(): List<Long> = currentList.map { it.id }

    fun setActive(playlistId: Long?) {
        if (activeId == playlistId) return
        val oldId = activeId
        activeId = playlistId
        // Actualización parcial: solo las filas vieja/nueva activa.
        listOfNotNull(oldId, playlistId).forEach { id ->
            val pos = currentList.indexOfFirst { it.id == id }
            if (pos >= 0) notifyItemChanged(pos)
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
                        rows.firstOrNull()
                    } else {
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemSidebarPlaylistBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemSidebarPlaylistBinding) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {

        fun bind(playlist: Playlist) {
            val active = playlist.id == activeId
            val url = coverCache[playlist.id]
            if (url.isNullOrBlank()) {
                binding.ivArtwork.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE)
                binding.ivArtwork.setPadding(9, 9, 9, 9)
                binding.ivArtwork.setColorFilter(
                    androidx.core.content.ContextCompat.getColor(binding.root.context, R.color.text_secondary)
                )
                binding.ivArtwork.setImageResource(R.drawable.ic_playlist)
            } else {
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
            binding.root.setBackgroundResource(
                if (active) R.drawable.bg_nav_selected else android.R.color.transparent
            )
            binding.root.setOnClickListener { onClick(playlist) }
        }
    }

    private class PlaylistDiffCallback : DiffUtil.ItemCallback<Playlist>() {
        override fun areItemsTheSame(oldItem: Playlist, newItem: Playlist): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Playlist, newItem: Playlist): Boolean =
            oldItem == newItem
    }
}
