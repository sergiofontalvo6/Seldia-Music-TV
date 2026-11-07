package com.seldia.music.ui.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.seldia.music.R
import com.seldia.music.SeldiaApp
import com.seldia.music.data.repository.LyricsResult
import com.seldia.music.databinding.FragmentLyricsSheetBinding
import com.seldia.music.ui.PlayerViewModel
import kotlinx.coroutines.launch

/**
 * Modal de letra (BottomSheet) de altura fija (~92% de la pantalla) anclado a los
 * bordes del sistema: nunca queda cortado ni detrás de la barra de navegación.
 * Si LRCLIB entrega marcas de tiempo se activa el resaltado karaoke sincronizado.
 */
class LyricsBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentLyricsSheetBinding? = null
    private val binding get() = _binding!!

    private val playerVm: PlayerViewModel by activityViewModels()
    private var lyricsAdapter: LyricsAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLyricsSheetBinding.inflate(inflater, container, false)
        applySheetHeight()
        return binding.root
    }

    /** Altura de la hoja anclada a la altura real de la pantalla (no fija en dp). */
    private fun applySheetHeight() {
        val metrics = resources.displayMetrics
        binding.root.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (metrics.heightPixels * 0.92f).toInt()
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnCloseLyrics.setOnClickListener { dismiss() }
        // Padding inferior = altura de la barra de navegación (no queda tras las teclas del sistema).
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, nav + (14 * resources.displayMetrics.density).toInt())
            insets
        }

        setupSheetBehavior()

        val track = playerVm.state.value.currentTrack
        if (track == null) {
            binding.tvSheetTitle.text = getString(R.string.lyrics_title)
            binding.tvSheetSubtitle.text = ""
            binding.scrollLyricsPlain.visibility = View.VISIBLE
            binding.tvLyricsBody.text = getString(R.string.lyrics_no_track)
            return
        }

        binding.tvSheetTitle.text = track.title
        binding.tvSheetSubtitle.text = track.artist
        binding.progressLyrics.visibility = View.VISIBLE
        binding.tvLyricsBody.text = getString(R.string.lyrics_loading)
        binding.scrollLyricsPlain.visibility = View.VISIBLE

        val lyricsRepo = (requireActivity().application as SeldiaApp).container.lyricsRepository
        viewLifecycleOwner.lifecycleScope.launch {
            val result = lyricsRepo.getLyrics(track)
            if (_binding == null) return@launch
            binding.progressLyrics.visibility = View.GONE
            result.fold(
                onSuccess = { lyrics -> renderLyrics(lyrics) },
                onFailure = { binding.tvLyricsBody.text = getString(R.string.lyrics_not_found) }
            )
        }

        observePlaybackPosition()
    }

    private fun setupSheetBehavior() {
        (dialog as? BottomSheetDialog)?.let { sheetDialog ->
            sheetDialog.behavior.apply {
                skipCollapsed = true
                state = BottomSheetBehavior.STATE_EXPANDED
                peekHeight = (resources.displayMetrics.heightPixels * 0.92f).toInt()
            }
        }
    }

    private fun renderLyrics(lyrics: LyricsResult) {
        when (lyrics) {
            is LyricsResult.Synced -> {
                binding.tvSyncBadge.visibility = View.VISIBLE
                binding.scrollLyricsPlain.visibility = View.GONE
                binding.recyclerLyrics.visibility = View.VISIBLE

                lyricsAdapter = LyricsAdapter(lyrics.lines)
                binding.recyclerLyrics.layoutManager =
                    LinearLayoutManager(requireContext()).apply { initialPrefetchItemCount = 8 }
                binding.recyclerLyrics.adapter = lyricsAdapter
                applyActiveLine(playerVm.positionFlow.value)
            }
            is LyricsResult.Plain -> {
                binding.tvSyncBadge.visibility = View.GONE
                binding.recyclerLyrics.visibility = View.GONE
                binding.scrollLyricsPlain.visibility = View.VISIBLE
                binding.tvLyricsBody.text = lyrics.text
            }
        }
    }

    private fun observePlaybackPosition() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                playerVm.positionFlow.collect { positionMs ->
                    applyActiveLine(positionMs)
                }
            }
        }
    }

    private fun applyActiveLine(positionMs: Long) {
        val adapter = lyricsAdapter ?: return
        val recycler = binding.recyclerLyrics
        if (adapter.itemCount == 0) return
        val pos = findActiveLineIndex(adapter, positionMs)
        if (pos == adapter.activeIndex) return
        adapter.setActiveIndex(pos)
        if (pos >= 0) centerOnLine(recycler, pos)
    }

    private fun findActiveLineIndex(adapter: LyricsAdapter, positionMs: Long): Int {
        // Búsqueda binaria sobre marcas de tiempo (lista ordenada).
        var lo = 0
        var hi = adapter.itemCount - 1
        var result = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val t = adapter.lineTimeAt(mid)
            if (t <= positionMs) {
                result = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return result
    }

    private fun centerOnLine(recycler: androidx.recyclerview.widget.RecyclerView, index: Int) {
        val lm = recycler.layoutManager as? LinearLayoutManager ?: return
        val itemHeight = (56 * resources.displayMetrics.density).toInt()
        val offset = ((recycler.height - itemHeight) / 2).coerceAtLeast(0)
        lm.scrollToPositionWithOffset(index, offset)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        lyricsAdapter = null
        _binding = null
    }

    companion object {
        const val TAG = "LyricsBottomSheet"
    }
}
