package com.seldia.music.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.seldia.music.SeldiaApp

/** Recibe los botones de la notificación multimedia y actúa sobre el reproductor. */
class PlaybackActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val player = (context.applicationContext as SeldiaApp).container.musicPlayer
        when (intent.action) {
            ACTION_PLAY_PAUSE -> player.toggle()
            ACTION_NEXT -> player.next()
            ACTION_PREVIOUS -> player.previous()
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.seldia.music.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.seldia.music.action.NEXT"
        const val ACTION_PREVIOUS = "com.seldia.music.action.PREVIOUS"
    }
}
