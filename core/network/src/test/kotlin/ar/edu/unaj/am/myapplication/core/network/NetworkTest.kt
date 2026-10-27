package ar.edu.unaj.am.myapplication.core.network

import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.Assert.*
import org.junit.Test

class NetworkTest {
    @Test fun `transport maps IO and invalid JSON but preserves cancellation`() = runTest {
        assertTrue(safeApiCall { throw IOException("Sin red") } is NetworkResult.NetworkError)
        assertTrue(safeApiCall { throw SerializationException("JSON inválido") } is NetworkResult.ParseError)
        assertEquals(NetworkResult.Success("ok"), safeApiCall { "ok" })
        try {
            safeApiCall { throw CancellationException("Salir de la pantalla") }
            fail("La cancelación no debe convertirse en un error de red")
        } catch (_: CancellationException) { }
    }

    @Test fun `real Retrofit request preserves coordinates parses route and handles HTTP failure`() = runTest {
        val servidor = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var codigo = 200
        var cuerpo = """{"code":"Ok","routes":[{"distance":100.0,"duration":60.0,"geometry":{"coordinates":[[-58.0,-34.0],[-58.1,-34.1]]}}],"extra":true}"""
        var ruta = ""
        servidor.createContext("/") { intercambio ->
            ruta = intercambio.requestURI.path
            val bytes = cuerpo.toByteArray()
            intercambio.responseHeaders.add("Content-Type", "application/json")
            intercambio.sendResponseHeaders(codigo, bytes.size.toLong())
            intercambio.responseBody.use { it.write(bytes) }
            intercambio.close()
        }
        servidor.start()
        try {
            val api = NetworkFactory.createRutasApi("http://127.0.0.1:${servidor.address.port}/")
            val respuesta = safeApiCall { api.calcular("-58.0,-34.0;-58.1,-34.1") } as NetworkResult.Success
            assertEquals(100.0, respuesta.data.routes.single().distance, 0.0)
            assertTrue(ruta.contains("-58.0,-34.0;-58.1,-34.1"))
            codigo = 503
            assertEquals(503, (safeApiCall { api.calcular("0,0;1,1") } as NetworkResult.HttpError).code)
            codigo = 200
            cuerpo = "{json-invalido"
            assertTrue(safeApiCall { api.calcular("0,0;1,1") } is NetworkResult.ParseError)
        } finally { servidor.stop(0) }
    }
}
