package com.seldia.music.ui.library

import android.os.Bundle
import android.view.View
import com.seldia.music.R
import com.seldia.music.ui.TableListFragment

/** "Canciones que te gustan": acceso rápido desde la barra lateral. */
class LikedSongsFragment : TableListFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        kicker(getString(R.string.kicker_favorites))
        headerTitle(getString(R.string.liked_songs_title))

        libraryVm.favorites.observe(viewLifecycleOwner) { list ->
            submitTracks(list)
        }
    }

    override fun emptyText(): String = getString(R.string.no_favorites)
}
