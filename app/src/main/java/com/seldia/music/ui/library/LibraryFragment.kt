package com.seldia.music.ui.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.seldia.music.databinding.FragmentLibraryBinding

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.viewPager.adapter = LibraryPagerAdapter(this)
        binding.viewPager.isUserInputEnabled = false

        val initial = arguments?.getInt(ARG_TAB, 0) ?: 0
        binding.viewPager.setCurrentItem(initial, false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class LibraryPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = 3

        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> PlaylistsTabFragment()
            1 -> FavoritesTabFragment()
            else -> HistoryTabFragment()
        }
    }

    companion object {
        private const val ARG_TAB = "initial_tab"

        fun newInstance(initialTab: Int): LibraryFragment = LibraryFragment().apply {
            arguments = Bundle().apply { putInt(ARG_TAB, initialTab) }
        }
    }
}
