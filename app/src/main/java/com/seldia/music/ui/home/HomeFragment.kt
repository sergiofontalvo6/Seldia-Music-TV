package com.seldia.music.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.seldia.music.R
import com.seldia.music.databinding.FragmentHomeBinding
import com.seldia.music.ui.HomeViewModel
import com.seldia.music.ui.MainActivity
import com.seldia.music.ui.TrackListFragment
import com.seldia.music.ui.home.HomeCardAdapter.HomeCard
import com.seldia.music.ui.home.HomeCardAdapter.Type

class HomeFragment : TrackListFragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val homeVm: HomeViewModel by activityViewModels()

    private lateinit var cardsAdapter: HomeCardAdapter
    private var favoritesCount = 0
    private var playlistsCount = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        cardsAdapter = HomeCardAdapter(onClick = ::onCardClick)
        binding.cards.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.cards.setHasFixedSize(true)
        binding.cards.adapter = cardsAdapter
        submitCards()

        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = makeAdapter()

        homeVm.favoritesCount.observe(viewLifecycleOwner) { count ->
            favoritesCount = count
            submitCards()
        }
        homeVm.playlistsCount.observe(viewLifecycleOwner) { count ->
            playlistsCount = count
            submitCards()
        }

        homeVm.favorites.observe(viewLifecycleOwner) { favorites ->
            adapter.submit(favorites)
            binding.empty.visibility = if (favorites.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun submitCards() {
        cardsAdapter.submit(
            listOf(
                HomeCard(Type.OFFLINE, getString(R.string.card_offline_subtitle)),
                HomeCard(Type.FAVORITES, resources.getQuantityString(R.plurals.favorite_count, favoritesCount, favoritesCount)),
                HomeCard(Type.PLAYLISTS, resources.getQuantityString(R.plurals.playlist_count, playlistsCount, playlistsCount))
            )
        )
    }

    private fun onCardClick(card: HomeCard) {
        val main = activity as? MainActivity ?: return
        when (card.type) {
            Type.OFFLINE -> main.openOfflineMusic()
            Type.FAVORITES -> main.openLibrary(1)
            Type.PLAYLISTS -> main.openLibrary(0)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
