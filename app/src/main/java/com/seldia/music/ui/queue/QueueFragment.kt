package com.seldia.music.ui.queue

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.seldia.music.R
import com.seldia.music.databinding.FragmentQueueBinding
import com.seldia.music.ui.PlayerViewModel
import com.seldia.music.ui.LibraryViewModel
import com.seldia.music.ui.adapters.TrackTableAdapter
import kotlinx.coroutines.launch

class QueueFragment : Fragment() {

    private var _binding: FragmentQueueBinding? = null
    private val binding get() = _binding!!

    private val playerVm: PlayerViewModel by activityViewModels()
    private val libraryVm: LibraryViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQueueBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())

        val adapter = TrackTableAdapter(
            onClick = { _, index -> playerVm.playAt(index) },
            onFavoriteToggle = { libraryVm.toggleFavorite(it) },
            onAddToQueue = {},
            onAddToPlaylist = {},
            onRemove = { _, index -> playerVm.removeFromQueue(index) }
        )
        binding.recycler.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                playerVm.state.collect { s ->
                    adapter.submit(s.queue)
                    val text = if (s.queue.isEmpty()) getString(R.string.queue_empty)
                    else getString(R.string.queue_count, s.queue.size)
                    binding.tvQueueInfo.text = text
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
