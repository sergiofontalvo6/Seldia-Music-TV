package com.seldia.music.data.network

import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Manejo centralizado de errores HTTP y de red.
 * Convierte códigos crudos (403/404/500) y excepciones de red en mensajes
 * amigables únicos, sin exponer JSON de error ni códigos en la interfaz.
 */
enum class ApiErrorKind {
    NO_CONNECTION,
    RATE_LIMIT,
    NOT_FOUND,
    SERVER,
    GENERIC
}

/** Excepción de API con código HTTP (si existe) y categoría amigable. */
class ApiException(
    val httpCode: Int?,
    val kind: ApiErrorKind,
    cause: Throwable? = null
) : IOException(friendlyMessage(httpCode, cause), cause) {

    /** Mensaje listo para la interfaz (sin códigos ni JSON crudos). */
    val friendly: String get() = friendlyMessage(httpCode, cause)

    companion object {
        fun friendlyMessage(httpCode: Int?, cause: Throwable?): String {
            return when {
                httpCode == 403 -> "Has superado el límite de consultas temporales. Intenta de nuevo en unos minutos."
                httpCode == 404 -> "No se encontró la información solicitada en el servidor."
                httpCode != null && httpCode >= 500 -> "El servidor no responde en este momento. Intenta más tarde."
                httpCode != null -> "Ocurrió un problema de comunicación. Por favor reintenta."
                cause is UnknownHostException || cause is SocketTimeoutException -> "Sin conexión a internet. Verifica tu red y vuelve a intentarlo."
                else -> "Ocurrió un problema de comunicación. Por favor reintenta."
            }
        }
    }
}

object NetworkError {

    /** Mensaje amigable para cualquier [Throwable] de red o HTTP. */
    fun friendlyMessage(throwable: Throwable): String {
        val (code, kind) = classify(throwable)
        return messageFor(code, kind)
    }

    fun messageFor(httpCode: Int?, kind: ApiErrorKind): String = when (kind) {
        ApiErrorKind.NO_CONNECTION -> "Sin conexión a internet. Verifica tu red y vuelve a intentarlo."
        ApiErrorKind.RATE_LIMIT -> "Has superado el límite de consultas temporales. Intenta de nuevo en unos minutos."
        ApiErrorKind.NOT_FOUND -> "No se encontró la información solicitada en el servidor."
        ApiErrorKind.SERVER -> "El servidor no responde en este momento. Intenta más tarde."
        ApiErrorKind.GENERIC -> "Ocurrió un problema de comunicación. Por favor reintenta."
    }

    fun classify(throwable: Throwable): Pair<Int?, ApiErrorKind> {
        var current: Throwable? = throwable
        while (current != null) {
            when (current) {
                is ApiException -> return current.httpCode to current.kind
                is HttpException -> return electiveKind(current.code())
                is UnknownHostException, is SocketTimeoutException -> return null to ApiErrorKind.NO_CONNECTION
                is IOException -> {
                    val msg = current.message ?: ""
                    if (msg.contains("timeout", ignoreCase = true) ||
                        msg.contains("unable to resolve host", ignoreCase = true) ||
                        msg.contains("network is unreachable", ignoreCase = true) ||
                        msg.contains("connection reset", ignoreCase = true) ||
                        msg.contains("no address associated", ignoreCase = true)
                    ) {
                        return null to ApiErrorKind.NO_CONNECTION
                    }
                    val code = Regex("""\b(403|404|429|5\d{2})\b""").find(msg)?.value?.toInt()
                    if (code != null) return electiveKind(code)
                }
            }
            current = current.cause
        }
        return null to ApiErrorKind.GENERIC
    }

    private fun electiveKind(code: Int): Pair<Int?, ApiErrorKind> {
        return when (code) {
            403 -> code to ApiErrorKind.RATE_LIMIT
            404 -> code to ApiErrorKind.NOT_FOUND
            in 500..599 -> code to ApiErrorKind.SERVER
            else -> code to ApiErrorKind.GENERIC
        }
    }

    /** Convierte cualquier [Throwable] de red a [ApiException] con mensaje amigable. */
    fun toApiException(throwable: Throwable): ApiException {
        if (throwable is ApiException) return throwable
        val (code, kind) = classify(throwable)
        return ApiException(code, kind, throwable)
    }
}

/**
 * Interceptor OkHttp: si la respuesta es error (HTTP ≥ 400) lanza [ApiException]
 * sin propagar el cuerpo JSON crudo; los IOException de red se convierten igual.
 */
class NetworkErrorInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = try {
            chain.proceed(chain.request())
        } catch (e: IOException) {
            throw NetworkError.toApiException(e)
        }
        if (response.code >= 400) {
            val code = response.code
            val (mappedCode, kind) = NetworkError.classify(
                IllegalStateException("HTTP $code")
            ).let { electiveCode(code) }
            response.close()
            throw ApiException(code, kind)
        }
        return response
    }

    private fun electiveCode(code: Int): Pair<Int?, ApiErrorKind> = when (code) {
        403 -> code to ApiErrorKind.RATE_LIMIT
        404 -> code to ApiErrorKind.NOT_FOUND
        in 500..599 -> code to ApiErrorKind.SERVER
        else -> code to ApiErrorKind.GENERIC
    }
}
