package ar.edu.unaj.am.myapplication.core.data.images

import ar.edu.unaj.am.myapplication.core.network.DescargadorImagenPublicacion
import kotlinx.coroutines.test.runTest
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class DescargadorImagenPublicacionTest {
    @Test fun `downloads images and rejects foreign hosts oversized bodies and non images`() = runTest {
        var mime = "image/jpeg"
        var data = byteArrayOf(1, 2, 3)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(data.toResponseBody(mime.toMediaType())).build()
        }.build()
        try {
            val downloader = DescargadorImagenPublicacion(client)
            val url = "https://res.cloudinary.com/demo/image/upload/foto.jpg"
            assertArrayEquals(data, downloader.descargar(url))
            try {
                downloader.descargar("https://otro-servidor.test/foto.jpg")
                fail("El host ajeno debe rechazarse antes de descargar")
            } catch (_: IOException) { }
            mime = "text/html"
            try { downloader.descargar(url); fail("No es una imagen") } catch (_: IOException) { }
            mime = "image/jpeg"
            data = ByteArray(DescargadorImagenPublicacion.MAX_BYTES + 1)
            try { downloader.descargar(url); fail("Supera el límite") } catch (_: IOException) { }
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }
}
