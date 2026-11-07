package com.seldia.music.data.extractor

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.net.HttpURLConnection
import java.net.URL

/** Implementación HTTP que NewPipe Extractor usa para descargar las páginas de YouTube. */
class NewPipeDownloader private constructor() : Downloader() {

    override fun execute(request: Request): Response {
        val connection = URL(request.url()).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = request.httpMethod()
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", DEFAULT_USER_AGENT)
            request.headers().forEach { (name, values) ->
                connection.setRequestProperty(name, values.joinToString(", "))
            }

            request.dataToSend()?.let { data ->
                connection.doOutput = true
                connection.outputStream.use { it.write(data) }
            }

            val code = connection.responseCode
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            val body = stream?.bufferedReader()?.use { it.readText() }

            val headers = connection.headerFields
                .filterKeys { it != null }
                .mapKeys { it.key }

            return Response(code, connection.responseMessage ?: "", headers, body, connection.url.toString())
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

        val instance: NewPipeDownloader by lazy { NewPipeDownloader() }
    }
}
