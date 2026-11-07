package com.seldia.music.data.update

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Actualización OTA conectada a GitHub Releases.
 *
 * Endpoint: `GET https://api.github.com/repos/sergiofontalvo6/SeldiaMusic/releases/latest`
 * (público, sin token). Parsea `tag_name` y el asset `.apk` (`browser_download_url`),
 * compara contra `BuildConfig.VERSION_NAME`, descarga el APK con OkHttp al
 * almacenamiento temporal de la app (`cacheDir/updates/`) y abre el instalador
 * vía **FileProvider** (`ACTION_VIEW`, `application/vnd.android.package-archive`).
 */
object UpdateCheck {

    private const val TAG = "Update"
    const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/sergiofontalvo6/SeldiaMusic/releases/latest"

    data class ReleaseInfo(
        val version: String,
        val apkName: String,
        val apkUrl: String,
        val body: String? = null,
        val htmlUrl: String? = null
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun fetchLatestRelease(): Result<ReleaseInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "SeldiaMusic-Android")
                .get()
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val err = response.body?.string().orEmpty()
                throw IOException("GitHub API ${response.code}: ${err.take(200)}")
            }
            val jsonText = response.body?.string()
                ?: throw IOException("Respuesta vacía")
            Log.d(TAG, "Release payload: ${jsonText.take(300)}")
            parseRelease(jsonText)
        }
    }

    private fun parseRelease(jsonText: String): ReleaseInfo {
        val root = JsonParser.parseString(jsonText).asJsonObject
        val tagName = root.get("tag_name")?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
        if (tagName.isBlank()) throw IllegalStateException("La release no indica versión (tag_name)")
        val assets = root.getAsJsonArray("assets") ?: throw IllegalStateException("La release no incluye assets")
        var apkName = ""
        var apkUrl = ""
        for (el in assets) {
            val obj = el.asJsonObject
            val name = obj.get("name")?.asString ?: continue
            if (name.lowercase().endsWith(".apk")) {
                apkName = name
                apkUrl = obj.get("browser_download_url")?.asString ?: ""
                if (apkUrl.isNotBlank()) break
            }
        }
        if (apkUrl.isBlank()) throw IllegalStateException("La release no incluye un APK adjunto (.apk)")
        val body = root.get("body")?.takeIf { !it.isJsonNull }?.asString
        val htmlUrl = root.get("html_url")?.takeIf { !it.isJsonNull }?.asString
        return ReleaseInfo(tagName, apkName, apkUrl, body?.takeIf { it.isNotBlank() }, htmlUrl)
    }

    /** Normaliza "v1.2.3" → "1.2.3" y compara por componentes numéricos. */
    fun isVersionNewer(remoteTag: String, currentVersion: String): Boolean {
        val remote = parseVersion(remoteTag)
        val current = parseVersion(currentVersion)
        val maxLen = maxOf(remote.size, current.size)
        for (i in 0 until maxLen) {
            val r = remote.getOrElse(i) { 0 }
            val c = current.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    private fun parseVersion(version: String): List<Int> {
        val cleaned = version.trim().trimStart('v', 'V')
        return Regex("""[0-9]+""").findAll(cleaned).map { it.value.toIntOrNull() ?: 0 }.toList()
    }

    suspend fun downloadApk(
        release: ReleaseInfo,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(release.apkUrl)
                .header("Accept", "application/vnd.android.package-archive")
                .header("User-Agent", "SeldiaMusic-Android")
                .get()
                .build()
            val response = downloadClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IOException("Descarga APK fallida (HTTP ${response.code})")
            }
            val body = response.body ?: throw IOException("Respuesta sin cuerpo")
            val dir = File(requireContext().cacheDir, "updates").apply { mkdirs() }
            val out = File(dir, release.apkName)
            var downloaded = 0L
            val total = body.contentLength().takeIf { it > 0 } ?: -1L
            body.byteStream().buffered().use { input ->
                out.outputStream().buffered().use { output ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        val read = input.read(buf)
                        if (read < 0) break
                        output.write(buf, 0, read)
                        downloaded += read
                        if (total > 0) onProgress(downloaded, total)
                    }
                }
            }
            if (!out.exists() || out.length() == 0L) {
                throw IOException("APK vacío tras la descarga")
            }
            Log.d(TAG, "APK descargado: ${out.absolutePath} ${out.length()}B")
            out
        }
    }

    /** Intent de instalación vía FileProvider (sin `file://`). */
    fun installIntent(context: Context, apkFile: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        return Intent(Intent.ACTION_VIEW).apply {
            data = uri
            type = "application/vnd.android.package-archive"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun requireContext(): Context {
        check(::appContext.isInitialized) { "UpdateCheck.init(context) requerido" }
        return appContext
    }
}
