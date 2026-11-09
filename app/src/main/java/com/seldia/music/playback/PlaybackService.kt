package com.seldia.music.playback

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.seldia.music.SeldiaApp

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        val musicPlayer = (application as SeldiaApp).container.musicPlayer
        mediaSession = MediaSession.Builder(this, musicPlayer.player).build()
        addSession(mediaSession!!)
        // WakeLock parcial explícito: mantiene CPU/red despiertos al reproducir
        // (complementa WAKE_MODE_NETWORK de ExoPlayer; evita corte por protector TV).
        musicPlayer.player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                holdWakeLock(isPlaying || musicPlayer.player.playWhenReady)
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                holdWakeLock(musicPlayer.player.playWhenReady)
            }
        })
        holdWakeLock(musicPlayer.player.isPlaying || musicPlayer.player.playWhenReady)
    }

    private fun holdWakeLock(hold: Boolean) {
        try {
            if (hold) {
                if (wakeLock == null) {
                    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                    wakeLock = pm.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "SeldiaMusic:PlaybackWakeLock"
                    ).apply {
                        setReferenceCounted(false)
                    }
                }
                if (wakeLock?.isHeld != true) {
                    wakeLock?.acquire(1000 * 60 * 60 * 24L) // 24h como seguro
                    Log.d("PlaybackService", "WakeLock adquirido (reproducción)")
                }
            } else {
                if (wakeLock?.isHeld == true) {
                    wakeLock?.release()
                    Log.d("PlaybackService", "WakeLock liberado (pausa/fin)")
                }
            }
        } catch (e: Exception) {
            Log.w("PlaybackService", "WakeLock: ${e.message}")
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        holdWakeLock(false)
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
