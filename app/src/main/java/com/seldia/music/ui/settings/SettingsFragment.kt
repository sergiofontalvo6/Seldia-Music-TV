package com.seldia.music.ui.settings

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.seldia.music.R
import com.seldia.music.SeldiaApp
import com.seldia.music.databinding.FragmentSettingsBinding
import com.seldia.music.util.OfflinePrefs
import com.seldia.music.util.ThemePrefs
import com.seldia.music.data.update.UpdateCheck
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                (requireActivity().application as SeldiaApp).container.backupManager.exportBackup(uri)
                    .getOrThrow()
            }
            Toast.makeText(
                requireContext(),
                getString(if (result.isSuccess) R.string.backup_done else R.string.backup_failed),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                (requireActivity().application as SeldiaApp).container.backupManager.importBackup(uri)
                    .getOrThrow()
            }
            Toast.makeText(
                requireContext(),
                getString(if (result.isSuccess) R.string.restore_done else R.string.restore_failed),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val spotifyTxtLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        loadSpotifyUrlsFromFile(uri)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.switchAutoDownload.isChecked = OfflinePrefs.autoDownloadEnabled
        binding.switchAutoDownload.setOnCheckedChangeListener { _, checked ->
            OfflinePrefs.autoDownloadEnabled = checked
        }

        binding.btnBackup.setOnClickListener {
            exportLauncher.launch("seldia-music-backup-${System.currentTimeMillis()}.json")
        }
        binding.btnRestore.setOnClickListener {
            importLauncher.launch(arrayOf("application/json"))
        }

        binding.btnSpotifyImport.setOnClickListener {
            val url = binding.etSpotifyUrl.text?.toString()?.trim().orEmpty()
            if (url.isEmpty()) {
                Toast.makeText(requireContext(), getString(R.string.spotify_import_failed), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            startSpotifyImport(url)
        }

        binding.btnSpotifyTxt.setOnClickListener {
            spotifyTxtLauncher.launch(arrayOf("text/plain"))
        }

        binding.btnUpdateCheck.setOnClickListener { checkForUpdate() }

        setupPersonalization()
    }

    private fun setupPersonalization() {
        val mode = ThemePrefs.nightMode
        binding.rbDark.isChecked = mode == ThemePrefs.NIGHT_DARK
        binding.rbLight.isChecked = mode == ThemePrefs.NIGHT_LIGHT
        binding.rbGlass.isChecked = mode == ThemePrefs.NIGHT_GLASS

        binding.rbDark.setOnCheckedChangeListener { _, checked ->
            if (!checked) return@setOnCheckedChangeListener
            if (ThemePrefs.nightMode != ThemePrefs.NIGHT_DARK) {
                ThemePrefs.nightMode = ThemePrefs.NIGHT_DARK
                // AppCompatDelegate recrea la actividad; si no lo hace, recrea manualmente.
                requireActivity().recreate()
            }
        }
        binding.rbLight.setOnCheckedChangeListener { _, checked ->
            if (!checked) return@setOnCheckedChangeListener
            if (ThemePrefs.nightMode != ThemePrefs.NIGHT_LIGHT) {
                ThemePrefs.nightMode = ThemePrefs.NIGHT_LIGHT
                requireActivity().recreate()
            }
        }
        binding.rbGlass.setOnCheckedChangeListener { _, checked ->
            if (!checked) return@setOnCheckedChangeListener
            if (ThemePrefs.nightMode != ThemePrefs.NIGHT_GLASS) {
                ThemePrefs.nightMode = ThemePrefs.NIGHT_GLASS
                requireActivity().recreate()
            }
        }

        buildAccentSwatches()
    }

    private fun buildAccentSwatches() {
        val ctx = requireContext()
        val row = binding.accentRow
        row.removeAllViews()

        val accents = listOf(
            ThemePrefs.ACCENT_RED to R.color.accent_red,
            ThemePrefs.ACCENT_ORANGE to R.color.accent_orange,
            ThemePrefs.ACCENT_AMBER to R.color.accent_amber,
            ThemePrefs.ACCENT_GREEN to R.color.accent_green,
            ThemePrefs.ACCENT_TEAL to R.color.accent_teal,
            ThemePrefs.ACCENT_BLUE to R.color.accent_blue,
            ThemePrefs.ACCENT_PURPLE to R.color.accent_purple,
            ThemePrefs.ACCENT_PINK to R.color.accent_pink
        )

        val selected = ThemePrefs.accentKey
        val sizePx = (44 * resources.displayMetrics.density).toInt()
        val checkPx = (22 * resources.displayMetrics.density).toInt()
        val marginPx = (6 * resources.displayMetrics.density).toInt()

        accents.forEach { (key, colorRes) ->
            val isSelected = key == selected
            val container = android.widget.FrameLayout(ctx).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(sizePx, sizePx).apply {
                    setMargins(marginPx, 0, marginPx, 0)
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (ThemePrefs.accentKey != key) {
                        ThemePrefs.accentKey = key
                        requireActivity().recreate()
                    }
                }
            }

            val bg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = sizePx / 2f
                setColor(androidx.core.content.ContextCompat.getColor(ctx, colorRes))
                if (isSelected) {
                    setStroke(
                        (3 * resources.displayMetrics.density).toInt(),
                        androidx.core.content.ContextCompat.getColor(ctx, R.color.text_primary)
                    )
                }
            }
            val circle = android.view.View(ctx).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                background = bg
            }
            container.addView(circle)

            if (isSelected) {
                val check = android.widget.ImageView(ctx).apply {
                    layoutParams = android.widget.FrameLayout.LayoutParams(checkPx, checkPx).apply {
                        gravity = android.view.Gravity.CENTER
                    }
                    setImageResource(R.drawable.ic_check)
                    setColorFilter(0xFFFFFFFF.toInt())
                }
                container.addView(check)
            }

            row.addView(container)
        }
    }

    /** Extrae URLs de Spotify línea por línea (ignora blancos) desde el .txt elegido. */
    private fun loadSpotifyUrlsFromFile(uri: Uri) {
        val resolver = requireContext().contentResolver
        val lines = try {
            resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                reader.lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toList()
            } ?: emptyList()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.spotify_import_failed), Toast.LENGTH_LONG).show()
            return
        }
        if (lines.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.spotify_txt_empty), Toast.LENGTH_LONG).show()
            return
        }
        startSpotifyFileImport(lines)
    }

    private fun setSpotifyBusy(busy: Boolean) {
        binding.btnSpotifyImport.isEnabled = !busy
        binding.btnSpotifyTxt.isEnabled = !busy
        binding.progressSpotify.visibility = if (busy) View.VISIBLE else View.GONE
        binding.tvSpotifyProgress.visibility = if (busy) View.VISIBLE else View.GONE
    }

    private fun updateSpotifyProgress(text: String) {
        binding.root.post {
            if (_binding == null) return@post
            binding.tvSpotifyProgress.text = text
        }
    }

    private fun startSpotifyFileImport(urls: List<String>) {
        setSpotifyBusy(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val importer = (requireActivity().application as SeldiaApp).container.spotifyPlaylistImporter
            var imported = 0
            var failed = 0
            for ((index, url) in urls.withIndex()) {
                updateSpotifyProgress(getString(R.string.spotify_txt_processing, index + 1, urls.size))
                val result = importer.importFromUrl(url) { progress ->
                    if (!isAdded) return@importFromUrl
                    binding.root.post {
                        if (_binding == null) return@post
                        val inner = getString(
                            R.string.spotify_importing, progress.processed, progress.total
                        )
                        binding.tvSpotifyProgress.text =
                            getString(R.string.spotify_txt_processing, index + 1, urls.size) + " " + inner
                    }
                }
                if (result.isSuccess) imported++ else failed++
            }
            setSpotifyBusy(false)
            if (!isAdded) return@launch
            Toast.makeText(
                requireContext(),
                getString(R.string.spotify_txt_done, imported, failed),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun startSpotifyImport(url: String) {
        binding.btnSpotifyImport.isEnabled = false
        binding.progressSpotify.visibility = View.VISIBLE
        binding.tvSpotifyProgress.visibility = View.VISIBLE
        binding.tvSpotifyProgress.text = ""

        viewLifecycleOwner.lifecycleScope.launch {
            val importer = (requireActivity().application as SeldiaApp).container.spotifyPlaylistImporter
            val result = importer.importFromUrl(url) { progress ->
                if (!isAdded) return@importFromUrl
                // Called from IO dispatcher; update UI via main thread.
                binding.root.post {
                    if (_binding == null) return@post
                    binding.tvSpotifyProgress.text = getString(
                        R.string.spotify_importing, progress.processed, progress.total
                    )
                }
            }
            if (_binding != null) {
                binding.progressSpotify.visibility = View.GONE
                binding.tvSpotifyProgress.visibility = View.GONE
                binding.btnSpotifyImport.isEnabled = true
            }
            if (!isAdded) return@launch
            if (result.isSuccess) {
                val data = result.getOrThrow()
                binding.etSpotifyUrl.text?.clear()
                Toast.makeText(
                    requireContext(),
                    getString(
                        R.string.spotify_import_done,
                        data.playlistName,
                        data.importedCount,
                        data.totalCount
                    ),
                    Toast.LENGTH_LONG
                ).show()
            } else {
                val message = result.exceptionOrNull()?.message
                val text = if (message.isNullOrBlank()) {
                    getString(R.string.spotify_import_failed)
                } else {
                    message
                }
                Toast.makeText(requireContext(), text, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkForUpdate() {
        setUpdateBusy(true, getString(R.string.update_checking))
        viewLifecycleOwner.lifecycleScope.launch {
            val result = UpdateCheck.fetchLatestRelease()
            setUpdateBusy(false, "")
            if (!isAdded) return@launch
            val release = result.getOrNull()
            val current = com.seldia.music.BuildConfig.VERSION_NAME
            if (release == null) {
                val msg = result.exceptionOrNull()?.message?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.update_failed)
                binding.tvUpdateStatus.text = msg
                return@launch
            }
            if (!UpdateCheck.isVersionNewer(release.version, current)) {
                binding.tvUpdateStatus.text = getString(R.string.update_up_to_date, current)
                return@launch
            }
            showUpdateDialog(release)
        }
    }

    private fun showUpdateDialog(release: UpdateCheck.ReleaseInfo) {
        val ctx = requireContext()
        val current = com.seldia.music.BuildConfig.VERSION_NAME
        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.update_available_title))
            .setMessage(getString(R.string.update_available_message, release.version, current))
            .setPositiveButton(getString(R.string.update_download)) { _, _ ->
                downloadAndInstall(release)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun downloadAndInstall(release: UpdateCheck.ReleaseInfo) {
        val ctx = requireActivity()
        val progress = android.widget.ProgressBar(ctx).apply {
            isIndeterminate = false
            max = 100
        }
        val status = android.widget.TextView(ctx).apply {
            text = getString(R.string.update_downloading, "0", "0")
            textSize = 12f
        }
        val container = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(progress)
            addView(status)
        }
        val dialog = androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.update_download))
            .setView(container)
            .setCancelable(false)
            .setNegativeButton(android.R.string.cancel) { d, _ -> d.dismiss() }
            .show()

        viewLifecycleOwner.lifecycleScope.launch {
            val result = UpdateCheck.downloadApk(release) { downloaded, total ->
                binding.root.post {
                    if (_binding == null) return@post
                    val mbDown = "%.1f".format(downloaded / 1_000_000.0)
                    val mbTotal = if (total > 0) "%.1f".format(total / 1_000_000.0) else "?"
                    status.text = getString(R.string.update_downloading, mbDown, mbTotal)
                    if (total > 0) {
                        progress.progress = ((downloaded * 100L) / total).toInt()
                    }
                }
            }
            dialog.dismiss()
            if (!isAdded) return@launch
            val apk = result.getOrNull()
            if (apk == null) {
                binding.tvUpdateStatus.text = getString(R.string.update_download_failed)
                return@launch
            }
            binding.tvUpdateStatus.text = getString(R.string.update_installed)
            runCatching {
                startActivity(UpdateCheck.installIntent(requireContext(), apk))
            }.onFailure {
                binding.tvUpdateStatus.text = getString(R.string.update_download_failed)
            }
        }
    }

    private fun setUpdateBusy(busy: Boolean, statusText: String) {
        binding.btnUpdateCheck.isEnabled = !busy
        if (busy) binding.progressUpdate.visibility = View.VISIBLE else binding.progressUpdate.visibility = View.GONE
        if (statusText.isNotEmpty()) binding.tvUpdateStatus.text = statusText
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
