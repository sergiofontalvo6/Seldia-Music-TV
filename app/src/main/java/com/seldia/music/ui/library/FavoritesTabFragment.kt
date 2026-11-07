package com.seldia.music.ui.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.seldia.music.R
import com.seldia.music.databinding.FragmentTrackListBinding
import com.seldia.music.ui.TrackListFragment

class FavoritesTabFragment : TrackListFragment() {

    private var _binding: FragmentTrackListBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrackListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.tvToolbarTitle.setText(R.string.favorites)
        binding.tvToolbarTitle.visibility = View.VISIBLE
        binding.recycler.adapter = makeAdapter()

        libraryVm.favorites.observe(viewLifecycleOwner) { list ->
            adapter.submit(list)
            binding.empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            binding.empty.text = getString(R.string.no_favorites)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
