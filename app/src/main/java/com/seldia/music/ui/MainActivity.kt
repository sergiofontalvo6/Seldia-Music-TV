package com.seldia.music.ui

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.app.UiModeManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import coil.load
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.seldia.music.R
import com.seldia.music.SeldiaApp
import com.seldia.music.data.model.Source
import com.seldia.music.data.model.Track
import com.seldia.music.databinding.ActivityMainBinding
import com.seldia.music.databinding.ActivityMainDesktopBinding
import com.seldia.music.databinding.ViewBottomBarBinding
import com.seldia.music.playback.PlayerState
import com.seldia.music.ui.home.HomeFragment
import com.seldia.music.ui.library.GroupTracksFragment
import com.seldia.music.ui.library.LikedSongsFragment
import com.seldia.music.ui.library.LibraryFragment
import com.seldia.music.ui.library.MediaGroupsFragment
import com.seldia.music.ui.player.LyricsBottomSheetFragment
import com.seldia.music.ui.player.PlayerFragment
import com.seldia.music.ui.playlist.PlaylistDetailFragment
import com.seldia.music.ui.queue.QueueFragment
import com.seldia.music.ui.search.SearchFragment
import com.seldia.music.ui.adapters.SidebarPlaylistAdapter
import com.seldia.music.util.OfflinePrefs
import com.seldia.music.util.accentPrimaryColor
import com.seldia.music.util.formatDuration
import com.seldia.music.util.showCreatePlaylistDialog
import kotlinx.coroutines.launch

/**
 * Shell principal adaptativo — Seldia Music TV:
 * - Android TV (Leanback): tres paneles fijos (sidebar · contenido · Sonando ahora)
 *   totalmente operable con D-pad (↑ ↓ ← → + centro/Enter). Foco visible con
 *   borde de acento, escala 1.04x y navegación superior/lateral optimizada 10-foot.
 * - Pantallas anchas no-TV (>= 840dp): mismo layout desktop de tres paneles.
 * - Pantallas compactas: contenido + barra flotante + navegación inferior.
 */
class MainActivity : AppCompatActivity() {

    private val fullscreenBackCallback = object : androidx.activity.OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (fullscreen) setFullscreen(false)
        }
    }

    private var phoneBinding: ActivityMainBinding? = null
    private var desktopBinding: ActivityMainDesktopBinding? = null

    private val bottomBarBinding: ViewBottomBarBinding?
        get() = phoneBinding?.bottomBar ?: desktopBinding?.bottomBar

    private var fullscreen = false
    private var lastArtworkUrl: String? = null
    private var isCurrentFavorite = false
    private var favoriteTrackKey: String? = null
    private var userScrubbing = false
    private var sidebarPlaylistAdapter: SidebarPlaylistAdapter? = null
    private var activePlaylistId: Long? = null
    private var likedActive = false
    private var activeFilter = SidebarFilter.LISTAS

    private enum class SidebarFilter { LISTAS, ARTISTAS, BUSCAR }

    // ---- Referencias al reproductor integrado en el panel derecho (desktop) ----
    private var npVideoView: PlayerView? = null
    private var npArtworkCard: View? = null
    private var npVideoContainer: View? = null
    private var npModeToggle: MaterialButtonToggleGroup? = null
    private var npBtnFullscreenEnter: ImageButton? = null
    private var npBtnFullscreenExit: ImageButton? = null
    private var npBtnLyrics: ImageButton? = null
    private var npFsBar: View? = null
    private var npFsIvArtwork: ImageView? = null
    private var npFsTvTitle: TextView? = null
    private var npFsTvArtist: TextView? = null
    private var npFsBtnFavorite: ImageButton? = null
    private var npFsBtnPlayPause: ImageButton? = null
    private var npFsBtnPrev: ImageButton? = null
    private var npFsBtnNext: ImageButton? = null
    private var npFsVolumeBar: SeekBar? = null
    private var npFsTvPosition: TextView? = null
    private var npFsTvDuration: TextView? = null
    private var npFsSeekBar: SeekBar? = null
    private var npFsBtnExit: ImageButton? = null
    private var npBtnFavorite: ImageButton? = null
    private var npBtnQueue: ImageButton? = null
    private var npTvTitle: TextView? = null
    private var npTvArtist: TextView? = null
    private var npSeekBar: SeekBar? = null
    private var npTvPosition: TextView? = null
    private var npTvDuration: TextView? = null
    private var npBtnModeAudio: MaterialButton? = null
    private var npBtnModeVideo: MaterialButton? = null
    private var npIvArtwork: ImageView? = null

    // Estado guardado del video_container para pantalla completa exclusiva.
    private var savedVideoParent: ViewGroup? = null
    private var savedVideoIndex: Int = -1
    // Barra flotante fullscreen vive en el panel derecho: al entrar hay que
    // re-parentarla al video (decor) para que se vea/foco sea alcanzable por D-pad.
    private var savedFsParent: ViewGroup? = null
    private var savedFsIndex: Int = -1

    // Autohide de controles en pantalla completa (5 s de inactividad).
    private val hideControlsHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var overlayHideRunnable: Runnable? = null
    private var overlaySeeking = false

    private val playerVm: PlayerViewModel by viewModels()
    private val libraryVm: LibraryViewModel by viewModels()

    /** true en Android TV: UiModeManager o feature leanback. */
    private val isTV: Boolean
        get() = runCatching {
            val uiMode = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
            val pm = packageManager
            when {
                uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION -> true
                pm.hasSystemFeature("android.software.leanback") &&
                    resources.configuration.screenWidthDp >= 960 -> true
                pm.hasSystemFeature("android.software.leanback") &&
                    resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE -> true
                else -> false
            }
        }.getOrDefault(false)

    private val isDesktop: Boolean
        // En TV siempre se usa el layout de escritorio para tener sidebar fija
        // + "Sonando ahora" y foco D-pad claro, sin depender solo del ancho.
        get() = isTV || resources.configuration.screenWidthDp >= DESKTOP_MIN_WIDTH_DP ||
            (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                resources.configuration.screenWidthDp >= 720)

    private val contentContainer: FrameLayout
        get() = phoneBinding?.contentContainer ?: desktopBinding!!.contentContainer

    private val bottomNav: BottomNavigationView?
        get() = phoneBinding?.bottomNav

    private val app: SeldiaApp
        get() = application as SeldiaApp

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(com.seldia.music.util.ThemePrefs.themeResForContext(this))
        super.onCreate(savedInstanceState)

        if (isDesktop) {
            desktopBinding = ActivityMainDesktopBinding.inflate(layoutInflater)
            setContentView(desktopBinding!!.root)
            setupDesktopSidebar()
            setupDesktopPlayerPanel()
        } else {
            phoneBinding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(phoneBinding!!.root)
            setupBottomNav()
        }

        setupBottomBar()
        observePlayerState()
        observeConnectivity()
        observeLikedCount()
        if (isTV) TvFocusHelper.attach(window.decorView)
        // Back en pantalla completa: minimiza video (no congela ni hace pop de fragment)
        onBackPressedDispatcher.addCallback(this, fullscreenBackCallback)

        if (savedInstanceState == null) {
            phoneBinding?.bottomNav?.selectedItemId = R.id.nav_home
            if (isDesktop) {
                // Filtro Listas activo por defecto: primera lista (Favoritas) en el centro.
                likedActive = true
                activeFilter = SidebarFilter.LISTAS
                renderSidebarFilterStates()
                selectSection(LikedSongsFragment())
            }
        }

        // TV: pide foco inicial para que el D-pad tenga punto de partida.
        if (isTV) {
            window.decorView.isFocusableInTouchMode = false
            window.decorView.post { requestInitialTvFocus() }
        }
        android.util.Log.d("SeldiaTV", "onCreate isTV=$isTV isDesktop=$isDesktop widthDp=${resources.configuration.screenWidthDp} orientation=${resources.configuration.orientation}")
    }

    override fun onResume() {
        super.onResume()
        if (isTV && currentFocus == null) {
            window.decorView.post { requestInitialTvFocus() }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && isTV) {
            android.util.Log.d("SeldiaTV", "onWindowFocusChanged hasFocus=true currentFocus=${currentFocus?.id}")
            // Pequeño retraso para que el sistema asiente el foco de la ventana
            window.decorView.post {
                if (isTV && currentFocus == null) requestInitialTvFocus()
                else if (isTV) {
                    val b = desktopBinding
                    // Si el foco cayó en el contenedor genérico, moverlo a un control real
                    val cf = currentFocus
                    if (cf?.id == R.id.content_container && b != null) {
                        val ok = b.btnFilterLists.requestFocus()
                        android.util.Log.d("SeldiaTV", "windowFocus move to filter req=$ok")
                    }
                }
            }
        }
    }

    /**
     * Foco inicial TV: sidebar "Favoritas" (o primer filtro) para arrancar en
     * navegación lateral fija y luego entrar al contenido con →.
     */
    private fun requestInitialTvFocus() {
        val b = desktopBinding ?: return
        TvFocusHelper.requestInitialFocus(b.btnNavLiked, window.decorView)
    }

    private fun isDescendant(ancestor: View, view: View): Boolean {
        var v: View? = view.parent as? View
        while (v != null) {
            if (v == ancestor) return true
            v = v.parent as? View
        }
        return false
    }

    /**
     * Mando a distancia:
     * - Media Play/Pause, Next/Prev, Stop delegan en el reproductor.
     * - DPAD_CENTER / ENTER: el sistema ya dispara click en el foco actual;
     *   aquí solo garantizamos que si el foco se pierde, se recupera.
     * - BACK (KEYCODE_BACK / KEYCODE_TV_BACK): retrocede en la pila de fragmentos.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        // En fullscreen: cualquier tecla del mando despierta la barra flotante
        // y asegura el foco dentro de ella (la surface nunca debe robarlo).
        if (fullscreen && isDesktop) {
            showOverlayAndReschedule()
            val cf = currentFocus
            val insideFs = npFsBar?.let { bar -> cf != null && (cf == bar || isDescendant(bar, cf)) } ?: false
            if (!insideFs) {
                TvFocusHelper.requestInitialFocus(npFsBtnPlayPause ?: npFsBtnExit, window.decorView)
            }
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                playerVm.toggle()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                playerVm.next()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                playerVm.previous()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_STOP -> {
                playerVm.toggle()
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                // Si no hay foco (p. ej. tras abrir diálogo), recupéralo.
                if (currentFocus == null && isTV) {
                    requestInitialTvFocus()
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                // Steering: desde el menú lateral, → entra directo al contenido central
                // (evita el salto errático al panel derecho / contenedor invisible).
                if (isTV && desktopBinding != null && currentFocus != null) {
                    val sidebar = desktopBinding!!.sidebarRoot
                    var v: View? = currentFocus
                    var inSidebar = false
                    while (v != null) {
                        if (v == sidebar) { inSidebar = true; break }
                        v = v.parent as? View
                    }
                    if (inSidebar) {
                        val fragView = supportFragmentManager.findFragmentById(R.id.content_container)?.view
                        val target = fragView?.let { findFirstFocusable(it) }
                            ?: findFirstFocusable(contentContainer)
                        if (target != null && target.requestFocus()) {
                            return true
                        }
                    }
                }
            }
            KeyEvent.KEYCODE_BACK -> {
                // 1) En fullscreen de video, Atrás minimiza al panel derecho (3 columnas)
                if (fullscreen) {
                    setFullscreen(false)
                    return true
                }
                // Dejar que el sistema gestione BACK; si no hay backstack,
                // en TV salir al launcher solo si no hay reproducción activa
                // es lo esperado, pero no forzamos finish para no cortar música.
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // ======================= Desktop: sidebar =======================

    private fun setupDesktopSidebar() {
        val b = desktopBinding ?: return

        sidebarPlaylistAdapter = SidebarPlaylistAdapter(
            onClick = { playlist -> openPlaylistDetail(playlist.id) },
            db = app.container.database
        )
        b.recyclerPlaylists.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        b.recyclerPlaylists.adapter = sidebarPlaylistAdapter

        // Drag & drop manual: arrastrar fila (pulsación larga) para reordenar.
        val itemTouchHelper = androidx.recyclerview.widget.ItemTouchHelper(
            object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
                androidx.recyclerview.widget.ItemTouchHelper.UP or androidx.recyclerview.widget.ItemTouchHelper.DOWN,
                0
            ) {
                override fun onMove(
                    recyclerView: androidx.recyclerview.widget.RecyclerView,
                    viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
                    target: androidx.recyclerview.widget.RecyclerView.ViewHolder
                ): Boolean {
                    sidebarPlaylistAdapter?.moveItem(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                    return true
                }

                override fun onSwiped(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, direction: Int) = Unit

                override fun isItemViewSwipeEnabled(): Boolean = false

                override fun onSelectedChanged(
                    viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder?,
                    actionState: Int
                ) {
                    super.onSelectedChanged(viewHolder, actionState)
                    if (viewHolder != null) {
                        viewHolder.itemView.elevation = if (actionState == androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_DRAG) {
                            12 * resources.displayMetrics.density
                        } else {
                            0f
                        }
                    }
                }

                override fun clearView(recyclerView: androidx.recyclerview.widget.RecyclerView, viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder) {
                    super.clearView(recyclerView, viewHolder)
                    // Persiste el nuevo orden en Room (sortOrder).
                    sidebarPlaylistAdapter?.orderedIds()?.let { ids ->
                        libraryVm.reorderPlaylists(ids)
                    }
                }
            }
        )
        itemTouchHelper.attachToRecyclerView(b.recyclerPlaylists)

        // Filtros en fila horizontal: Listas | Artistas | Buscar.
        b.btnFilterLists.setOnClickListener {
            activeFilter = SidebarFilter.LISTAS
            renderSidebarFilterStates()
            if (activePlaylistId != null) {
                navigate(PlaylistDetailFragment.newInstance(activePlaylistId!!))
            } else {
                likedActive = true
                sidebarPlaylistAdapter?.setActive(null)
                renderSidebarFilterStates()
                navigate(LikedSongsFragment())
            }
        }
        b.btnFilterArtists.setOnClickListener {
            likedActive = false
            activePlaylistId = null
            sidebarPlaylistAdapter?.setActive(null)
            activeFilter = SidebarFilter.ARTISTAS
            renderSidebarFilterStates()
            navigate(MediaGroupsFragment.newInstance(GroupTracksFragment.MODE_ARTIST))
        }
        b.btnFilterSearch.setOnClickListener {
            likedActive = false
            activePlaylistId = null
            sidebarPlaylistAdapter?.setActive(null)
            activeFilter = SidebarFilter.BUSCAR
            renderSidebarFilterStates()
            navigate(SearchFragment())
        }

        b.btnNavLiked.setOnClickListener { openLikedSongs() }

        b.btnNewPlaylist.setOnClickListener {
            showCreatePlaylistDialog(this) { name -> libraryVm.createPlaylist(name) }
        }

        b.btnNavSettings.setOnClickListener { openSettings() }

        libraryVm.playlists.observe(this) { list ->
            sidebarPlaylistAdapter?.submit(list)
            sidebarPlaylistAdapter?.setActive(activePlaylistId)
        }
        renderSidebarFilterStates()
    }

    private fun renderSidebarFilterStates() {
        val b = desktopBinding ?: return
        val accent = accentPrimaryColor()
        val primary = androidx.core.content.ContextCompat.getColor(this, R.color.text_primary)
        val listsActive = activeFilter == SidebarFilter.LISTAS
        val artistsActive = activeFilter == SidebarFilter.ARTISTAS
        val searchActive = activeFilter == SidebarFilter.BUSCAR

        b.btnFilterLists.setBackgroundResource(
            if (listsActive) R.drawable.bg_nav_selected else R.drawable.bg_glass_pill
        )
        b.btnFilterLists.setTextColor(if (listsActive) accent else primary)

        b.btnFilterArtists.setBackgroundResource(
            if (artistsActive) R.drawable.bg_nav_selected else R.drawable.bg_glass_pill
        )
        b.btnFilterArtists.setTextColor(if (artistsActive) accent else primary)

        b.btnFilterSearch.setBackgroundResource(
            if (searchActive) R.drawable.bg_nav_selected else R.drawable.bg_glass_pill
        )
        b.btnFilterSearch.setTextColor(if (searchActive) accent else primary)

        b.btnNavLiked.setBackgroundResource(
            if (likedActive) R.drawable.bg_nav_selected else R.drawable.bg_glass_card
        )
    }

    // ======================= Desktop: reproductor integrado en panel derecho =======================

    private fun setupDesktopPlayerPanel() {
        val np = desktopBinding?.nowPlaying ?: return

        // Capturar referencias a las vistas del panel derecho
        npVideoView = np.videoView
        npArtworkCard = np.artworkCard
        npVideoContainer = np.videoContainer
        npModeToggle = np.modeToggle
        npBtnFullscreenEnter = np.btnFullscreenEnter
        npBtnFullscreenExit = np.btnFullscreenExit
        npBtnLyrics = np.btnNpLyrics
        npBtnFavorite = np.btnNpFavorite
        npBtnQueue = np.btnNpQueue
        npFsBar = np.fsBar
        npFsIvArtwork = np.fsIvArtwork
        npFsTvTitle = np.fsTvTitle
        npFsTvArtist = np.fsTvArtist
        npFsBtnFavorite = np.fsBtnFavorite
        npFsBtnPlayPause = np.fsBtnPlayPause
        npFsBtnPrev = np.fsBtnPrev
        npFsBtnNext = np.fsBtnNext
        npFsVolumeBar = np.fsVolumeBar
        npFsTvPosition = np.fsTvPosition
        npFsTvDuration = np.fsTvDuration
        npFsSeekBar = np.fsSeekBar
        npFsBtnExit = np.fsBtnExit
        npTvTitle = np.tvNpTitle
        npTvArtist = np.tvNpArtist
        npSeekBar = np.seekBar
        npTvPosition = np.tvPosition
        npTvDuration = np.tvDuration
        npBtnModeAudio = np.btnModeAudio
        npBtnModeVideo = np.btnModeVideo
        npIvArtwork = np.ivBigArtwork

        // Vincular PlayerView a ExoPlayer (corrige pantalla negra en video).
        // Se asigna SIEMPRE (no solo en video mode) para que la surface esté lista.
        npVideoView?.player = playerVm.exoPlayer
        npVideoView?.useController = false
        npVideoView?.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
        // La surface NO debe robar el foco D-pad: los controles fullscreen sí.
        npVideoView?.isFocusable = false
        npVideoView?.isFocusableInTouchMode = false
        // Cadena explícita en la barra flotante: definida en XML (nextFocusLeft/Right),
        // la API de View solo permite setNextFocusForwardId, no direccionales.

        // Acciones secundarias (los controles principales viven en la barra inferior)
        npBtnLyrics?.setOnClickListener { openLyrics() }
        npBtnFavorite?.setOnClickListener { toggleFavoriteCurrentTrack() }
        npBtnQueue?.setOnClickListener { openQueue() }

        npBtnFullscreenEnter?.setOnClickListener { setFullscreen(true) }
        npBtnFullscreenExit?.setOnClickListener { setFullscreen(false) }

        // Toggle Audio / Video
        npModeToggle?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val wantsVideo = checkedId == R.id.btn_mode_video
            val currentTrack = playerVm.state.value.currentTrack
            if (wantsVideo && currentTrack?.source != Source.YOUTUBE) {
                toast(getString(R.string.video_only_youtube))
                npModeToggle?.check(R.id.btn_mode_audio)
                return@addOnButtonCheckedListener
            }
            playerVm.setVideoMode(wantsVideo)
        }

        // Seek bar del panel derecho
        npSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) npTvPosition?.text = formatDuration(progress.toLong())
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                playerVm.seekTo(seekBar?.progress?.toLong() ?: 0L)
            }
        })

        // Barra flotante de pantalla completa (idéntica a la principal).
        npFsBtnPlayPause?.setOnClickListener { playerVm.toggle() }
        npFsBtnPrev?.setOnClickListener { playerVm.previous() }
        npFsBtnNext?.setOnClickListener { playerVm.next() }
        npFsBtnFavorite?.setOnClickListener { toggleFavoriteCurrentTrack() }
        npFsBtnExit?.setOnClickListener { setFullscreen(false) }
        npFsVolumeBar?.progress = (playerVm.exoPlayer.volume * 100).toInt()
        npFsVolumeBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) playerVm.exoPlayer.volume = progress / 100f
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        npFsSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) npFsTvPosition?.text = formatDuration(progress.toLong())
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { overlaySeeking = true }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                playerVm.seekTo(seekBar?.progress?.toLong() ?: 0L)
                overlaySeeking = false
                showOverlayAndReschedule()
            }
        })
    }

    // ======================= Desktop: conectividad y likes =======================

    private fun observeConnectivity() {
        if (!isDesktop) return
        val b = desktopBinding ?: return
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.container.networkConnectivityManager.isOnline.collect { online ->
                    b.connectivityDot.setBackgroundResource(
                        if (online) R.drawable.bg_dot_online else R.drawable.bg_dot_offline
                    )
                    b.tvConnectivity.setText(
                        if (online) R.string.online_status else R.string.offline_status
                    )
                }
            }
        }
    }

    private fun observeLikedCount() {
        if (!isDesktop) return
        libraryVm.favorites.observe(this) { favs ->
            desktopBinding?.tvLikedCount?.text =
                resources.getQuantityString(R.plurals.favorite_count, favs.size, favs.size)
            // Sincronización reactiva: si la pista en reproducción cambia de favorito
            // en cualquier lista, el corazón del reproductor se repinta solo.
            val currentKey = playerVm.state.value.currentTrack?.key
            if (currentKey != null) {
                val nowFav = favs.any { it.key == currentKey }
                if (nowFav != isCurrentFavorite) {
                    isCurrentFavorite = nowFav
                    renderFavoriteButtons()
                }
            }
        }
    }

    // ======================= Bottom bar =======================

    private fun setupBottomBar() {
        val bar = bottomBarBinding ?: return

        bar.btnPlayPause.setOnClickListener { playerVm.toggle() }
        bar.btnNext.setOnClickListener { playerVm.next() }
        bar.btnPrev.setOnClickListener { playerVm.previous() }
        bar.btnShuffle.setOnClickListener { playerVm.toggleShuffle() }
        bar.btnRepeat.setOnClickListener { cycleRepeatMode() }
        bar.btnLyrics.setOnClickListener { openLyrics() }
        bar.btnQueue.setOnClickListener { openQueue() }
        bar.btnFavorite.setOnClickListener { toggleFavoriteCurrentTrack() }

        if (resources.configuration.screenWidthDp < 560) {
            bar.volumeBar.visibility = View.GONE
            bar.ivVolumeIcon.visibility = View.GONE
        }

        bar.volumeBar.progress = (playerVm.exoPlayer.volume * 100).toInt()
        bar.volumeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) playerVm.exoPlayer.volume = progress / 100f
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        bar.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val duration = playerVm.state.value.durationMs
                if (duration <= 0L) return
                playerVm.seekTo(duration * progress / 1000L)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                userScrubbing = true
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                userScrubbing = false
            }
        })

        desktopBinding?.nowPlaying?.let { np ->
            np.btnNpLyrics.setOnClickListener { openLyrics() }
            np.btnNpFavorite.setOnClickListener { toggleFavoriteCurrentTrack() }
        }

        bottomBarBinding?.bottomBarRoot?.setOnClickListener {
            if (!isDesktop) openPlayer()
        }
    }

    private fun cycleRepeatMode() {
        val next = when (playerVm.state.value.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        playerVm.setRepeatMode(next)
    }

    private fun toggleFavoriteCurrentTrack() {
        val track = playerVm.state.value.currentTrack ?: return
        isCurrentFavorite = !isCurrentFavorite
        renderFavoriteButtons()
        libraryVm.toggleFavorite(track)
        lifecycleScope.launch {
            isCurrentFavorite = app.container.libraryRepository.isFavorite(track)
            renderFavoriteButtons()
        }
    }


    private fun openLyrics() {
        LyricsBottomSheetFragment().show(supportFragmentManager, LyricsBottomSheetFragment.TAG)
    }

    private fun renderFavoriteButtons() {
        val bar = bottomBarBinding
        val icon = if (isCurrentFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite
        val accent = accentPrimaryColor()
        val color = if (isCurrentFavorite) accent else androidx.core.content.ContextCompat.getColor(
            this, R.color.text_secondary
        )
        bar?.btnFavorite?.setImageResource(icon)
        bar?.btnFavorite?.setColorFilter(color)

        npBtnFavorite?.setImageResource(icon)
        npBtnFavorite?.setColorFilter(
            if (isCurrentFavorite) accent else androidx.core.content.ContextCompat.getColor(
                this, R.color.text_primary
            )
        )
    }

    // ======================= Observador de estado de reproducción =======================

    private fun observePlayerState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                playerVm.state.collect { updatePlaybackUi(it) }
            }
        }
        // Flujo independiente de posición: solo seekbars y tiempos cada 500 ms.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                playerVm.positionFlow.collect { updatePlaybackPosition(it) }
            }
        }
    }

    private fun updatePlaybackPosition(positionMs: Long) {
        val bar = bottomBarBinding
        val duration = playerVm.state.value.durationMs.coerceAtLeast(0L)
        bar?.tvPosition?.text = formatDuration(positionMs)
        if (!userScrubbing) {
            bar?.seekBar?.progress =
                if (duration > 0L) ((positionMs * 1000L) / duration).toInt().coerceIn(0, 1000) else 0
        }
        npTvPosition?.text = formatDuration(positionMs)
        if (!userScrubbing) {
            npSeekBar?.progress =
                if (duration > 0L) ((positionMs * 1000L) / duration).toInt().coerceIn(0, 1000) else 0
        }
        npFsTvPosition?.text = formatDuration(positionMs)
        if (!overlaySeeking) {
            npFsSeekBar?.progress =
                if (duration > 0L) ((positionMs * 1000L) / duration).toInt().coerceIn(0, 1000) else 0
        }
    }


    private fun updatePlaybackUi(s: PlayerState) {
        // TV: pantalla siempre encendida mientras suena (anti apagado inactivo)
        if (s.isPlaying) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val bar = bottomBarBinding
        val track = s.currentTrack

        val barVisibility = if (track != null && !fullscreen) View.VISIBLE else View.GONE
        bar?.bottomBarRoot?.visibility = barVisibility
        if (track == null) {
            npTvTitle?.text = getString(R.string.nothing_playing)
            npTvArtist?.text = ""
            showLogoFallback(npIvArtwork)
            showLogoFallback(bar?.ivArtwork)
            return
        }

        bar?.tvTitle?.text = track.title
        bar?.tvArtist?.text = track.artist
        bar?.btnPlayPause?.setImageResource(
            if (s.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )

        if (track.key != favoriteTrackKey) {
            favoriteTrackKey = track.key
            lifecycleScope.launch {
                isCurrentFavorite = app.container.libraryRepository.isFavorite(track)
                renderFavoriteButtons()
            }
        }

        bar?.btnShuffle?.setColorFilter(
            if (s.shuffleEnabled) accentPrimaryColor() else androidx.core.content.ContextCompat.getColor(
                this, R.color.text_secondary
            )
        )
        when (s.repeatMode) {
            Player.REPEAT_MODE_ONE -> {
                bar?.btnRepeat?.setImageResource(R.drawable.ic_repeat_one)
                bar?.btnRepeat?.setColorFilter(accentPrimaryColor())
            }
            Player.REPEAT_MODE_ALL -> {
                bar?.btnRepeat?.setImageResource(R.drawable.ic_repeat)
                bar?.btnRepeat?.setColorFilter(accentPrimaryColor())
            }
            else -> {
                bar?.btnRepeat?.setImageResource(R.drawable.ic_repeat)
                bar?.btnRepeat?.setColorFilter(
                    androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary)
                )
            }
        }

        val duration = s.durationMs.coerceAtLeast(0L)
        bar?.tvDuration?.text = formatDuration(duration)

        // ===== Panel derecho =====
        npTvTitle?.text = track.title
        npTvArtist?.text = track.artist

        // Carátula: cargar en panel derecho y barra inferior (ambas).
        val artworkUrl = track.artworkUrl
        if (lastArtworkUrl != artworkUrl) {
            lastArtworkUrl = artworkUrl
            listOfNotNull(bar?.ivArtwork, npIvArtwork).forEach { image ->
                if (artworkUrl.isNullOrBlank()) {
                    showLogoFallback(image)
                } else {
                    image.clearAnimation()
                    image.scaleType = ImageView.ScaleType.CENTER_CROP
                    image.load(hqArtwork(artworkUrl)) {
                        placeholder(R.drawable.ic_seldia_logo)
                        error(R.drawable.ic_seldia_logo)
                        crossfade(false)
                    }
                }
            }
            // Letras fijas del panel: cargar automáticamente al cambiar de pista.
            // (panel de letras eliminado: los accesos quedan en la barra inferior)
        }

        val isYouTube = track.source == Source.YOUTUBE
        npBtnModeAudio?.isEnabled = true
        npBtnModeVideo?.isEnabled = isYouTube
        npBtnModeVideo?.alpha = if (isYouTube) 1f else 0.4f

        npArtworkCard?.visibility = if (s.videoMode) View.GONE else View.VISIBLE
        npVideoContainer?.visibility = if (s.videoMode) View.VISIBLE else View.GONE

        // Asegurar que el PlayerView tenga el player asignado y forzar
        // la creación de la surface cuando se activa el modo video.
        if (s.videoMode) {
            npVideoView?.player = playerVm.exoPlayer
            npVideoView?.requestLayout()
            npVideoView?.invalidate()
        }

        npBtnFullscreenEnter?.isEnabled = s.videoMode
        npBtnFullscreenEnter?.alpha = if (s.videoMode) 1f else 0.4f
        npBtnFullscreenEnter?.visibility = if (s.videoMode && !fullscreen) View.VISIBLE else View.GONE
        npBtnFullscreenExit?.visibility = if (s.videoMode && fullscreen) View.VISIBLE else View.GONE

        if (!s.videoMode && fullscreen) setFullscreen(false)

        npTvDuration?.text = formatDuration(duration)

        // Refrescar barra flotante de pantalla completa (idéntica a la principal)
        npFsTvTitle?.text = track.title
        npFsTvArtist?.text = track.artist
        npFsBtnPlayPause?.setImageResource(
            if (s.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
        npFsBtnFavorite?.setImageResource(
            if (isCurrentFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite
        )
        npFsTvDuration?.text = formatDuration(duration)
        if (fullscreen) {
            val fsArtwork = npFsIvArtwork
            if (fsArtwork != null) {
                if (artworkUrl.isNullOrBlank()) {
                    showLogoFallback(fsArtwork)
                } else {
                    fsArtwork.clearAnimation()
                    fsArtwork.scaleType = ImageView.ScaleType.CENTER_CROP
                    fsArtwork.load(hqArtwork(artworkUrl)) {
                        placeholder(R.drawable.ic_seldia_logo)
                        error(R.drawable.ic_seldia_logo)
                        crossfade(false)
                    }
                }
            }
        }

    }


    private fun scheduleOverlayHide() {
        overlayHideRunnable?.let { hideControlsHandler.removeCallbacks(it) }
        overlayHideRunnable = Runnable {
            // En TV no ocultar si el foco está dentro de la barra (usuario navegando
            // con D-pad); reprograma para no dejar controles invisibles sin foco.
            val bar = npFsBar
            if (bar != null && bar.findFocus() != null) {
                scheduleOverlayHide()
            } else {
                bar?.visibility = View.GONE
            }
        }
        hideControlsHandler.postDelayed(overlayHideRunnable!!, OVERLAY_HIDE_DELAY_MS)
    }

    private fun showOverlayAndReschedule() {
        npFsBar?.visibility = View.VISIBLE
        scheduleOverlayHide()
    }

    private fun showLogoFallback(image: ImageView?) {
        if (image == null) return
        image.scaleType = ImageView.ScaleType.FIT_CENTER
        image.setImageResource(R.drawable.ic_seldia_logo)
        runCatching {
            val anim = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.pulse_logo)
            image.startAnimation(anim)
        }
    }

    private fun updateArtwork(artworkUrl: String?) {
        // La carátula se carga en updatePlaybackUi para ambas vistas (panel y barra inferior).
    }

    private fun hqArtwork(url: String): String {
        val match = Regex("https?://i\\.ytimg\\.com/vi/([^/]+)/([^/]+)").find(url) ?: return url
        val id = match.groupValues[1]
        val file = match.groupValues[2].substringBefore('.')
        return if (file.startsWith("maxresdefault")) url else "https://i.ytimg.com/vi/$id/maxresdefault.jpg"
    }

    fun setFullscreen(enabled: Boolean) {
        if (fullscreen == enabled) return
        fullscreen = enabled
        fullscreenBackCallback.isEnabled = enabled
        val insets = WindowInsetsControllerCompat(window, window.decorView)
        if (enabled) insets.hide(WindowInsetsCompat.Type.systemBars())
        else insets.show(WindowInsetsCompat.Type.systemBars())

        val chrome = if (enabled) View.GONE else View.VISIBLE
        phoneBinding?.bottomNav?.visibility = chrome
        bottomBarBinding?.bottomBarRoot?.visibility = chrome

        if (!isDesktop) return

        if (enabled) {
            // Solo el video: ocultar sidebar, contenido y divisores.
            desktopBinding?.let { b ->
                b.sidebarRoot.visibility = View.GONE
                b.dividerSidebar.visibility = View.GONE
                b.dividerNowPlaying.visibility = View.GONE
                b.contentContainer.visibility = View.GONE
            }
            // Mover el video_container al decor para que llene toda la pantalla.
            val video = npVideoContainer ?: return
            val parent = video.parent as? ViewGroup
            if (parent != null) {
                savedVideoParent = parent
                savedVideoIndex = parent.indexOfChild(video)
                parent.removeView(video)
            }
            val decor = window.decorView as ViewGroup
            decor.addView(
                video,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            video.setPadding(0, 0, 0, 0)
            video.bringToFront()
            npVideoView?.visibility = View.VISIBLE
            npVideoView?.requestLayout()
            npVideoView?.invalidate()
            // Re-parentar la barra flotante al contenedor de video (decor):
            // estaba dentro del panel derecho y quedaba oculta tras el video.
            npFsBar?.let { bar ->
                val barParent = bar.parent as? ViewGroup
                if (barParent != null && barParent != video) {
                    savedFsParent = barParent
                    savedFsIndex = barParent.indexOfChild(bar)
                    barParent.removeView(bar)
                    // Layout al pie del video (FrameLayout en decor)
                    (video as? ViewGroup)?.addView(
                        bar,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT
                        ).apply { gravity = android.view.Gravity.BOTTOM }
                    )
                }
            }
            // La barra flotante fullscreen lleva el botón de salida integrado.
            npFsBtnExit?.visibility = View.VISIBLE
            npBtnFullscreenExit?.visibility = View.GONE
            // Autohide: controles visibles al entrar y se ocultan a los 5 s.
            video.setOnTouchListener { _, motion ->
                if (motion.action == android.view.MotionEvent.ACTION_DOWN) showOverlayAndReschedule()
                false
            }
            showOverlayAndReschedule()
            // D-pad: el foco cae en la barra flotante (minimizar/play) para que
            // CENTER lo active y BACK minimice sin congelar la pantalla.
            // TvFocusHelper con reintento (requestFocus directo falla en modo táctil de algunas ROMs TV).
            window.decorView.post {
                TvFocusHelper.requestInitialFocus(npFsBtnExit ?: npFsBtnPlayPause, window.decorView)
            }
        } else {
            desktopBinding?.let { b ->
                b.sidebarRoot.visibility = View.VISIBLE
                b.dividerSidebar.visibility = View.VISIBLE
                b.dividerNowPlaying.visibility = View.VISIBLE
                b.contentContainer.visibility = View.VISIBLE
            }
            // Restaurar el video en el panel derecho.
            val video = npVideoContainer
            val decor = window.decorView as ViewGroup
            if (video != null && video.parent == decor) {
                decor.removeView(video)
                val parent = savedVideoParent
                val index = savedVideoIndex
                if (parent != null) {
                    val insertAt = if (index in 0 until parent.childCount) index else parent.childCount
                    parent.addView(
                        video,
                        insertAt,
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            (190 * resources.displayMetrics.density).toInt()
                        )
                    )
                }
                savedVideoParent = null
                savedVideoIndex = -1
                // Restaurar barra flotante en el panel derecho
                npFsBar?.let { bar ->
                    (bar.parent as? ViewGroup)?.removeView(bar)
                    val fsParent = savedFsParent
                    val fsIndex = savedFsIndex
                    if (fsParent != null) {
                        val at = if (fsIndex in 0 until fsParent.childCount) fsIndex else fsParent.childCount
                        fsParent.addView(bar, at)
                    }
                    savedFsParent = null
                    savedFsIndex = -1
                }
            }
            video?.setOnTouchListener(null)
            overlayHideRunnable?.let { hideControlsHandler.removeCallbacks(it) }
            overlayHideRunnable = null
            npFsBar?.visibility = View.GONE
            npFsBtnExit?.visibility = View.GONE
            npVideoView?.visibility = if (playerVm.state.value.videoMode) View.VISIBLE else View.GONE
            npBtnFullscreenExit?.visibility = View.GONE
            npBtnFullscreenEnter?.visibility =
                if (playerVm.state.value.videoMode) View.VISIBLE else View.GONE
        }
    }

    // ======================= Navegación =======================

    private fun selectSection(fragment: Fragment) {
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_container, fragment)
            .commit()
        supportFragmentManager.executePendingTransactions()
        // TV: tras cambiar de sección, mueve el foco al contenido para
        // que el D-pad no se quede en el sidebar.
        if (isTV) {
            window.decorView.post {
                val fragView = supportFragmentManager.findFragmentById(R.id.content_container)?.view
                val firstFocusable = fragView?.let { findFirstFocusable(it) }
                    ?: contentContainer.findFocus()
                    ?: findFirstFocusable(contentContainer)
                if (firstFocusable != null) {
                    firstFocusable.requestFocus()
                } else {
                    contentContainer.requestFocus()
                }
            }
        }
    }

    private fun navigate(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_container, fragment)
            .addToBackStack(null)
            .commit()
        supportFragmentManager.executePendingTransactions()
        if (isTV) {
            window.decorView.post {
                val fragView = supportFragmentManager.findFragmentById(R.id.content_container)?.view
                val firstFocusable = fragView?.let { findFirstFocusable(it) }
                if (firstFocusable != null) {
                    firstFocusable.requestFocus()
                }
            }
        }
    }

    /** Recorre el árbol y devuelve el primer foco D-pad disponible (preferir clicables). */
    private fun findFirstFocusable(root: View): View? {
        // Preferir hojas clicables (botones) sobre contenedores no clicables.
        val isLeafFocusable = root.isFocusable && root.isClickable && root.isVisibleForTV()
        if (isLeafFocusable) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findFirstFocusable(root.getChildAt(i))
                if (found != null) return found
            }
        }
        // Fallback: cualquier foco visible (p. ej. RecyclerView de pistas)
        if (root.isFocusable && root.isVisibleForTV()) return root
        return null
    }

    private fun View.isVisibleForTV(): Boolean = isShown

    fun openPlayer() {
        if (isDesktop) return
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_container, PlayerFragment.newInstance(showClose = true))
            .addToBackStack("player")
            .commit()
    }

    fun openQueue() = navigate(QueueFragment())

    fun openPlaylistDetail(playlistId: Long) {
        activeFilter = SidebarFilter.LISTAS
        likedActive = false
        activePlaylistId = playlistId
        sidebarPlaylistAdapter?.setActive(playlistId)
        renderSidebarFilterStates()
        navigate(PlaylistDetailFragment.newInstance(playlistId))
    }

    fun openSettings() = navigate(com.seldia.music.ui.settings.SettingsFragment())

    fun openLikedSongs() {
        activeFilter = SidebarFilter.LISTAS
        likedActive = true
        activePlaylistId = null
        sidebarPlaylistAdapter?.setActive(null)
        renderSidebarFilterStates()
        navigate(LikedSongsFragment())
    }

    fun openGroups(mode: String) {
        activeFilter = SidebarFilter.ARTISTAS
        likedActive = false
        activePlaylistId = null
        sidebarPlaylistAdapter?.setActive(null)
        renderSidebarFilterStates()
        navigate(MediaGroupsFragment.newInstance(mode))
    }

    fun openGroupTracks(mode: String, name: String) = navigate(GroupTracksFragment.newInstance(mode, name))

    fun openLibrary(initialTab: Int) = navigate(LibraryFragment.newInstance(initialTab))

    private fun setupBottomNav() {
        bottomNav?.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> selectSection(HomeFragment())
                R.id.nav_search -> selectSection(SearchFragment())
                R.id.nav_library -> selectSection(LibraryFragment())
                else -> return@setOnItemSelectedListener false
            }
            true
        }
    }

    private fun toast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val DESKTOP_MIN_WIDTH_DP = 840
        const val OVERLAY_HIDE_DELAY_MS = 5000L
    }
}
