package com.seldia.music.ui.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.seldia.music.R
import com.seldia.music.databinding.FragmentTrackListBinding
import com.seldia.music.ui.TrackListFragment
import com.seldia.music.util.showModernConfirmDialog

class HistoryTabFragment : TrackListFragment() {

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
        binding.tvToolbarTitle.setText(R.string.history)
        binding.tvToolbarTitle.visibility = View.VISIBLE
        binding.btnClear.visibility = View.VISIBLE
        binding.recycler.adapter = makeAdapter()
        observeFavorites()

        binding.btnClear.setOnClickListener {
            showModernConfirmDialog(
                requireContext(),
                getString(R.string.clear_history),
                getString(R.string.confirm_clear_history),
                getString(R.string.clear_history)
            ) { libraryVm.clearHistory() }
        }

        libraryVm.history.observe(viewLifecycleOwner) { list ->
            adapter.submit(list)
            binding.empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            binding.empty.text = getString(R.string.no_history)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
