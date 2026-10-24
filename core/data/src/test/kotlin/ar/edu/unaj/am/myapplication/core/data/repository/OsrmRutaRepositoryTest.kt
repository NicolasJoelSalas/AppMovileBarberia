package ar.edu.unaj.am.myapplication.core.data.repository

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.network.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class OsrmRutaRepositoryTest {
    private fun dto(puntos: List<List<Double>> = listOf(listOf(-58.3, -34.8), listOf(-58.31, -34.81))) =
        RespuestaRutaDto("Ok", listOf(RutaDto(1234.0, 180.0, GeometriaRutaDto(puntos))))

    @Test fun `envia longitud antes que latitud y convierte geojson a dominio`() = runTest {
        var enviado = ""
        val repo = OsrmRutaRepository(object : RutasApi {
            override suspend fun calcular(coordenadas: String): RespuestaRutaDto {
                enviado = coordenadas
                return dto()
            }
        })
        val resultado = repo.calcular(Coordenadas(-34.8, -58.3), Coordenadas(-34.81, -58.31)) as AppResult.Success
        assertEquals("-58.3,-34.8;-58.31,-34.81", enviado)
        assertEquals(Coordenadas(-34.8, -58.3), resultado.data.puntos.first())
        assertEquals(1234.0, resultado.data.distanciaMetros, 0.0)
    }

    @Test fun `maneja rutas inexistentes y geometrias invalidas`() {
        assertTrue(mapearRuta(RespuestaRutaDto("NoRoute")) is AppResult.Error)
        assertTrue(mapearRuta(dto(listOf(listOf(-58.0)))) is AppResult.Error)
        assertTrue(mapearRuta(dto(listOf(listOf(-58.0, 95.0), listOf(-58.0, -34.0)))) is AppResult.Error)
    }

    @Test fun `traduce falta de conexion y propaga cancelacion`() = runTest {
        val repo = OsrmRutaRepository(object : RutasApi {
            override suspend fun calcular(coordenadas: String): RespuestaRutaDto = throw java.io.IOException()
        })
        assertEquals(AppResult.Error(DomainError.Network), repo.calcular(Coordenadas(0.0, 0.0), Coordenadas(1.0, 1.0)))
        val cancelado = OsrmRutaRepository(object : RutasApi {
            override suspend fun calcular(coordenadas: String): RespuestaRutaDto = throw kotlinx.coroutines.CancellationException()
        })
        try {
            cancelado.calcular(Coordenadas(0.0, 0.0), Coordenadas(1.0, 1.0))
            fail("Debe propagar la cancelación")
        } catch (_: kotlinx.coroutines.CancellationException) { }
    }
}
