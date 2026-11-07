package com.seldia.music.ui.playlist

import android.os.Bundle
import android.view.View
import com.seldia.music.R
import com.seldia.music.ui.TableListFragment

class PlaylistDetailFragment : TableListFragment() {

    private var playlistId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playlistId = arguments?.getLong(ARG_PLAYLIST_ID, -1L) ?: -1L
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        kicker(getString(R.string.kicker_playlist))
        headerTitle("")

        libraryVm.playlists.observe(viewLifecycleOwner) { list ->
            list.firstOrNull { it.id == playlistId }?.let {
                headerTitle(it.name)
            }
        }

        container.libraryRepository.observePlaylistTracks(playlistId).observe(viewLifecycleOwner) { tracks ->
            submitTracks(tracks)
        }
    }

    override fun removeAction(): ((com.seldia.music.data.model.Track, Int) -> Unit)? =
        { track, _ -> libraryVm.removeTrackFromPlaylist(playlistId, track) }

    override fun playlistIdForActions(): Long = playlistId

    override fun playlistNameForActions(): String? {
        // Nombre actualizado por el observer de headerTitle;fallback al título visible.
        return binding.tvTitleBig.text?.toString()?.takeIf { it.isNotBlank() }
    }

    override fun emptyText(): String = getString(R.string.empty_playlist)

    companion object {
        private const val ARG_PLAYLIST_ID = "playlist_id"

        fun newInstance(playlistId: Long): PlaylistDetailFragment = PlaylistDetailFragment().apply {
            arguments = Bundle().apply { putLong(ARG_PLAYLIST_ID, playlistId) }
        }
    }
}
