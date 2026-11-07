package com.seldia.music.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.seldia.music.R
import com.seldia.music.databinding.ItemHomeCardBinding

/** Tarjetas superiores de la pantalla de inicio (Sin conexión, Favoritos, Playlists). */
class HomeCardAdapter(
    private val onClick: (HomeCard) -> Unit
) : ListAdapter<HomeCardAdapter.HomeCard, HomeCardAdapter.VH>(HomeCardDiffCallback()) {

    enum class Type { OFFLINE, FAVORITES, PLAYLISTS }

    data class HomeCard(val type: Type, val subtitle: String)

    fun submit(list: List<HomeCard>) {
        submitList(list)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemHomeCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val binding: ItemHomeCardBinding) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {

        fun bind(card: HomeCard) {
            val (titleRes, iconRes, backgroundRes) = when (card.type) {
                Type.OFFLINE -> Triple(R.string.action_offline, R.drawable.ic_download, R.drawable.bg_card_offline)
                Type.FAVORITES -> Triple(R.string.action_favorites, R.drawable.ic_favorite_filled, R.drawable.bg_card_favorites)
                Type.PLAYLISTS -> Triple(R.string.action_playlists, R.drawable.ic_playlist, R.drawable.bg_card_playlists)
            }
            binding.cardSurface.setBackgroundResource(backgroundRes)
            binding.ivIcon.setImageResource(iconRes)
            binding.tvTitle.setText(titleRes)
            binding.tvSubtitle.text = card.subtitle
            binding.root.setOnClickListener { onClick(card) }
        }
    }

    private class HomeCardDiffCallback : DiffUtil.ItemCallback<HomeCard>() {
        override fun areItemsTheSame(oldItem: HomeCard, newItem: HomeCard): Boolean =
            oldItem.type == newItem.type

        override fun areContentsTheSame(oldItem: HomeCard, newItem: HomeCard): Boolean =
            oldItem == newItem
    }
}
