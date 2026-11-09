package com.seldia.music.ui.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import com.seldia.music.R
import com.seldia.music.SeldiaApp
import com.seldia.music.data.model.Source
import com.seldia.music.data.model.Track
import com.seldia.music.databinding.FragmentPlayerBinding
import com.seldia.music.di.AppContainer
import com.seldia.music.playback.PlayerState
import com.seldia.music.ui.MainActivity
import com.seldia.music.ui.PlayerViewModel
import com.seldia.music.util.accentPrimaryColor
import com.seldia.music.util.formatDuration
import kotlinx.coroutines.launch

class PlayerFragment : Fragment() {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private val playerVm: PlayerViewModel by activityViewModels()
    private var isUserSeeking = false
    private var isFullscreen = false
    private var updatingModeToggle = false

    private val container: AppContainer
        get() = (requireActivity().application as SeldiaApp).container

    private var lastArtworkUrl: String? = null

    private val fullscreenBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = setFullscreen(false)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        lastArtworkUrl = null
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val showClose = arguments?.getBoolean(ARG_SHOW_CLOSE, true) ?: true
        binding.btnClose.visibility = if (showClose) View.VISIBLE else View.GONE

        binding.videoView.player = playerVm.exoPlayer

        binding.btnPlayPause.setOnClickListener { playerVm.toggle() }
        binding.btnNext.setOnClickListener { playerVm.next() }
        binding.btnPrev.setOnClickListener { playerVm.previous() }
        binding.btnShuffle.setOnClickListener { playerVm.toggleShuffle() }
        binding.btnRepeat.setOnClickListener { cycleRepeat() }
        binding.btnQueue.setOnClickListener { (activity as? MainActivity)?.openQueue() }
        binding.btnClose.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        binding.btnFullscreen.setOnClickListener { setFullscreen(!isFullscreen) }
        binding.btnFullscreenExit.setOnClickListener { setFullscreen(false) }
        binding.btnLyrics.setOnClickListener { openLyricsSheet() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, fullscreenBackCallback)

        binding.modeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || updatingModeToggle) return@addOnButtonCheckedListener
            val wantsVideo = checkedId == R.id.btn_mode_video
            if (wantsVideo && playerVm.state.value.currentTrack?.source != Source.YOUTUBE) {
                Toast.makeText(requireContext(), R.string.video_only_youtube, Toast.LENGTH_SHORT).show()
                updateModeToggle(playerVm.state.value.videoMode)
                return@addOnButtonCheckedListener
            }
            playerVm.setVideoMode(wantsVideo)
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) binding.tvPosition.text = formatDuration(progress.toLong())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                playerVm.seekTo(seekBar?.progress?.toLong() ?: 0L)
                isUserSeeking = false
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                playerVm.state.collect { render(it) }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                playerVm.positionFlow.collect { updatePosition(it) }
            }
        }

    }

    private fun openLyricsSheet() {
        val existing = childFragmentManager.findFragmentByTag(LyricsBottomSheetFragment.TAG)
        if (existing == null) {
            LyricsBottomSheetFragment().show(childFragmentManager, LyricsBottomSheetFragment.TAG)
        }
    }



    private fun cycleRepeat() {
        val next = when (playerVm.state.value.repeatMode) {
            PlayerViewModel.REPEAT_OFF -> PlayerViewModel.REPEAT_ALL
            PlayerViewModel.REPEAT_ALL -> PlayerViewModel.REPEAT_ONE
            else -> PlayerViewModel.REPEAT_OFF
        }
        playerVm.setRepeatMode(next)
    }

    private fun render(s: PlayerState) {
        val track = s.currentTrack
        binding.tvTitle.text = track?.title ?: getString(R.string.nothing_playing)
        binding.tvArtist.text = track?.artist ?: ""

        // Evita recargar la carátula en cada actualización de posición (cada 500 ms).
        val artworkUrl = track?.artworkUrl
        if (lastArtworkUrl != artworkUrl) {
            lastArtworkUrl = artworkUrl
            if (artworkUrl.isNullOrBlank()) {
                binding.ivArtwork.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                binding.ivArtwork.setImageResource(R.drawable.ic_seldia_logo)
                runCatching {
                    val anim = android.view.animation.AnimationUtils.loadAnimation(requireContext(), R.anim.pulse_logo)
                    binding.ivArtwork.startAnimation(anim)
                }
            } else {
                binding.ivArtwork.clearAnimation()
                binding.ivArtwork.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                binding.ivArtwork.load(artworkUrl) {
                    placeholder(R.drawable.ic_seldia_logo)
                    error(R.drawable.ic_seldia_logo)
                    crossfade(false)
                }
            }
        }

        binding.btnPlayPause.setImageResource(
            if (s.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
        binding.tvDuration.text = formatDuration(s.durationMs)

        binding.btnShuffle.alpha = if (s.shuffleEnabled) 1f else 0.4f
        binding.btnShuffle.isSelected = s.shuffleEnabled
        binding.btnRepeat.setImageResource(
            if (s.repeatMode == PlayerViewModel.REPEAT_ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat
        )
        binding.btnRepeat.alpha = if (s.repeatMode == PlayerViewModel.REPEAT_OFF) 0.4f else 1f

        val hasTrack = track != null
        val isYouTube = track?.source == Source.YOUTUBE
        binding.btnLyrics.isEnabled = hasTrack
        binding.btnLyrics.alpha = if (hasTrack) 1f else 0.4f


        binding.btnModeVideo.isEnabled = isYouTube
        updateModeToggle(s.videoMode)
        binding.artworkCard.visibility = if (s.videoMode) View.GONE else View.VISIBLE
        binding.videoContainer.visibility = if (s.videoMode) View.VISIBLE else View.GONE
        binding.btnFullscreen.isEnabled = s.videoMode
        binding.btnFullscreen.alpha = if (s.videoMode) 1f else 0.4f
        if (!s.videoMode && isFullscreen) setFullscreen(false)
    }

    private fun updatePosition(positionMs: Long) {
        if (_binding == null) return
        binding.tvPosition.text = formatDuration(positionMs)
        if (!isUserSeeking) {
            val durationMs = playerVm.state.value.durationMs
            val max = durationMs.coerceAtLeast(1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            binding.seekBar.max = max
            binding.seekBar.progress = positionMs.toInt().coerceAtMost(max)
        }
    }


    private fun updateModeToggle(videoMode: Boolean) {
        updatingModeToggle = true
        binding.modeToggle.check(if (videoMode) R.id.btn_mode_video else R.id.btn_mode_audio)
        updatingModeToggle = false
    }

    private fun setFullscreen(enabled: Boolean) {
        if (_binding == null) return
        if (enabled && !playerVm.state.value.videoMode) return
        if (enabled == isFullscreen) return
        isFullscreen = enabled
        fullscreenBackCallback.isEnabled = enabled

        val chromeVisibility = if (enabled) View.GONE else View.VISIBLE
        binding.headerRow.visibility = chromeVisibility
        binding.modeToggle.visibility = chromeVisibility
        binding.infoBlock.visibility = chromeVisibility
        binding.seekRow.visibility = chromeVisibility
        binding.controlsRow.visibility = chromeVisibility
        binding.btnFullscreenExit.visibility = if (enabled) View.VISIBLE else View.GONE

        val padding = if (enabled) 0 else (18 * resources.displayMetrics.density).toInt()
        binding.playerContent.setPadding(padding, padding, padding, padding)

        (activity as? MainActivity)?.setFullscreen(enabled)
    }

    override fun onDestroyView() {
        setFullscreen(false)
        binding.videoView.player = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_SHOW_CLOSE = "show_close"

        fun newInstance(showClose: Boolean): PlayerFragment = PlayerFragment().apply {
            arguments = Bundle().apply { putBoolean(ARG_SHOW_CLOSE, showClose) }
        }
    }
}
