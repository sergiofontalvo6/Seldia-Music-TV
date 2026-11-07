package com.seldia.music.ui.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.seldia.music.R
import com.seldia.music.SeldiaApp
import com.seldia.music.databinding.FragmentGroupListBinding
import com.seldia.music.databinding.ItemGroupBinding
import com.seldia.music.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Biblioteca offline agrupada por álbum o por artista. */
class MediaGroupsFragment : Fragment() {

    private var _binding: FragmentGroupListBinding? = null
    private val binding get() = _binding!!

    private val mode: String
        get() = arguments?.getString(ARG_MODE) ?: GroupTracksFragment.MODE_ALBUM

    private fun isValidGroupName(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return false
        val lower = trimmed.lowercase()
        return lower != "<unknown>" && lower != "unknown" && lower != "null"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGroupListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val isAlbum = mode == GroupTracksFragment.MODE_ALBUM
        binding.tvTitleBig.setText(if (isAlbum) R.string.albums else R.string.artists)
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())

        viewLifecycleOwner.lifecycleScope.launch {
            val all = withContext(Dispatchers.IO) {
                (requireActivity().application as SeldiaApp)
                    .container.offlineMusicRepository.loadTracks()
            }

            val groups = all
                .mapNotNull { track ->
                    val value = (if (isAlbum) track.album else track.artist).trim()
                    value.takeIf { isValidGroupName(it) }
                }
                .groupBy { it.lowercase() }
                .map { (_, variants) ->
                    val display = variants.groupingBy { it }.eachCount().maxBy { it.value }.key
                    MediaGroup(display, variants.size)
                }
                .sortedBy { it.name.lowercase() }

            binding.progress.visibility = View.GONE
            binding.empty.visibility = if (groups.isEmpty()) View.VISIBLE else View.GONE
            binding.recycler.adapter = GroupAdapter(groups, isAlbum) { name ->
                (activity as? MainActivity)?.openGroupTracks(mode, name)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private data class MediaGroup(val name: String, val count: Int)

    private inner class GroupAdapter(
        private val items: List<MediaGroup>,
        private val isAlbum: Boolean,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<GroupAdapter.VH>() {

        inner class VH(val binding: ItemGroupBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val group = items[position]
            holder.binding.tvName.text = group.name
            holder.binding.tvCount.text =
                resources.getQuantityString(R.plurals.track_count, group.count, group.count)
            holder.binding.ivIcon.setImageResource(
                if (isAlbum) R.drawable.ic_music_note else R.drawable.ic_mic
            )
            holder.binding.root.setOnClickListener { onClick(group.name) }
        }

        override fun getItemCount(): Int = items.size
    }

    companion object {
        private const val ARG_MODE = "mode"

        fun newInstance(mode: String): MediaGroupsFragment = MediaGroupsFragment().apply {
            arguments = Bundle().apply { putString(ARG_MODE, mode) }
        }
    }
}
