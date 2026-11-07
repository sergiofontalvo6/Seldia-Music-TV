package com.seldia.music.ui.library

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.seldia.music.R
import com.seldia.music.ui.TableListFragment
import kotlinx.coroutines.launch

/** Pistas de un álbum o artista concreto, tomadas de la biblioteca offline. */
class GroupTracksFragment : TableListFragment() {

    private val mode: String
        get() = arguments?.getString(ARG_MODE) ?: MODE_ALBUM
    private val groupName: String
        get() = arguments?.getString(ARG_NAME).orEmpty()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        kicker(getString(if (mode == MODE_ALBUM) R.string.kicker_album else R.string.kicker_artist))
        headerTitle(groupName)
        loadTracks()
    }

    private fun loadTracks() {
        viewLifecycleOwner.lifecycleScope.launch {
            val all = container.offlineMusicRepository.loadTracks()
            val normalizedGroupName = groupName.trim()
            val groupIsUnknown = normalizedGroupName.lowercase() == "<unknown>" ||
                normalizedGroupName.lowercase() == "unknown"
            val filtered = if (groupIsUnknown) emptyList() else all.filter { track ->
                val value = if (mode == MODE_ALBUM) track.album else track.artist
                value.trim().equals(normalizedGroupName, ignoreCase = true) &&
                    value.trim().lowercase() != "<unknown>" &&
                    value.trim().lowercase() != "unknown"
            }
            submitTracks(filtered)
        }
    }

    override fun emptyText(): String = getString(R.string.empty_group_offline)

    companion object {
        const val MODE_ALBUM = "album"
        const val MODE_ARTIST = "artist"
        private const val ARG_MODE = "mode"
        private const val ARG_NAME = "name"

        fun newInstance(mode: String, name: String): GroupTracksFragment =
            GroupTracksFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_MODE, mode)
                    putString(ARG_NAME, name)
                }
            }
    }
}
