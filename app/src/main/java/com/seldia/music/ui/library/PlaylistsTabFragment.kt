package com.seldia.music.ui.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.seldia.music.R
import com.seldia.music.databinding.FragmentPlaylistsBinding
import com.seldia.music.ui.LibraryViewModel
import com.seldia.music.ui.MainActivity
import com.seldia.music.ui.adapters.PlaylistAdapter
import com.seldia.music.util.showModernConfirmDialog
import com.seldia.music.util.showCreatePlaylistDialog
import com.seldia.music.util.showRenamePlaylistDialog

class PlaylistsTabFragment : Fragment() {

    private var _binding: FragmentPlaylistsBinding? = null
    private val binding get() = _binding!!

    private val libraryVm: LibraryViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaylistsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val db = (requireActivity().application as com.seldia.music.SeldiaApp).container.database
        val adapter = PlaylistAdapter(
            onClick = { playlist -> (activity as? MainActivity)?.openPlaylistDetail(playlist.id) },
            onRename = { playlist ->
                showRenamePlaylistDialog(requireContext(), playlist.name) { newName ->
                    libraryVm.renamePlaylist(playlist.id, newName)
                }
            },
            onDelete = { playlist ->
                showModernConfirmDialog(
                    requireContext(),
                    getString(R.string.delete),
                    getString(R.string.confirm_delete_playlist, playlist.name),
                    getString(R.string.delete)
                ) { libraryVm.deletePlaylist(playlist.id) }
            },
            db = db
        )
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        binding.btnCreate.setOnClickListener {
            showCreatePlaylistDialog(requireContext()) { name -> libraryVm.createPlaylist(name) }
        }

        libraryVm.playlists.observe(viewLifecycleOwner) { list ->
            adapter.submit(list)
            binding.empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
