package com.seldia.music.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.seldia.music.SeldiaApp
import com.seldia.music.data.model.Track
import com.seldia.music.playback.PlayerState
import kotlinx.coroutines.flow.StateFlow

/** ViewModel del reproductor, compartido por toda la UI (scoped a la Activity). */
class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val player = (app as SeldiaApp).container.musicPlayer

    val state: StateFlow<PlayerState> = player.state
    val positionFlow: StateFlow<Long> = player.positionFlow

    fun toggle() = player.toggle()
    fun next() = player.next()
    fun previous() = player.previous()
    fun seekTo(ms: Long) = player.seekTo(ms)
    fun setRepeatMode(mode: Int) = player.setRepeatMode(mode)
    fun toggleShuffle() = player.toggleShuffle()
    fun setShuffleEnabled(enabled: Boolean) = player.setShuffleEnabled(enabled)
    fun playQueue(tracks: List<Track>, index: Int) = player.playQueue(tracks, index)
    fun addToQueue(track: Track) = player.addToQueue(track)
    fun removeFromQueue(index: Int) = player.removeFromQueue(index)
    fun playAt(index: Int) = player.playAt(index)
    fun setVideoMode(enabled: Boolean) = player.setVideoMode(enabled)
    val exoPlayer: ExoPlayer get() = player.player

    companion object {
        const val REPEAT_OFF = Player.REPEAT_MODE_OFF
        const val REPEAT_ALL = Player.REPEAT_MODE_ALL
        const val REPEAT_ONE = Player.REPEAT_MODE_ONE
    }
}
