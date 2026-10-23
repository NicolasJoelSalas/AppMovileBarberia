package ar.edu.unaj.am.myapplication.core.network

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Descargas acotadas y cancelables, exclusivamente desde las fotos públicas de Cloudinary. */
class DescargadorImagenPublicacion(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS).build()
) {
    suspend fun descargar(url: String): ByteArray = suspendCancellableCoroutine { continuation ->
        val address = try { url.toHttpUrl() } catch (e: IllegalArgumentException) {
            continuation.resumeWithException(IOException("Dirección de foto inválida.", e))
            return@suspendCancellableCoroutine
        }
        if (address.scheme != "https" || address.host != "res.cloudinary.com" ||
            !address.encodedPath.contains("/image/upload/")) {
            continuation.resumeWithException(IOException("La foto debe pertenecer a Cloudinary."))
            return@suspendCancellableCoroutine
        }
        val call = client.newCall(Request.Builder().url(address).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val bytes = response.use {
                        if (!it.isSuccessful) throw IOException("No se pudo descargar la foto.")
                        val body = it.body
                        if (body.contentLength() > MAX_BYTES || body.contentType()?.type != "image") {
                            throw IOException("La foto tiene un tamaño o formato inválido.")
                        }
                        body.byteStream().use { input ->
                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                if (!continuation.isActive) throw IOException("Descarga cancelada.")
                                val read = input.read(buffer)
                                if (read < 0) break
                                if (output.size() + read > MAX_BYTES) throw IOException("La foto supera los 4 MB.")
                                output.write(buffer, 0, read)
                            }
                            output.toByteArray().also { data -> if (data.isEmpty()) throw IOException("Foto vacía.") }
                        }
                    }
                    if (continuation.isActive) continuation.resume(bytes)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        })
    }
    companion object { const val MAX_BYTES = 4 * 1024 * 1024 }
}
