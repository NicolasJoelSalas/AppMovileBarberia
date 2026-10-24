package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class UbicacionUseCasesTest {
    @Test fun `rechaza puntos fuera de rango e infinitos sin persistir`() = runTest {
        val repo = FakeUbicacion()
        val guardar = GuardarMiUbicacionUseCase(repo)
        for (punto in listOf(Coordenadas(91.0, 0.0), Coordenadas(0.0, -181.0),
            Coordenadas(Double.NaN, 0.0), Coordenadas(0.0, Double.POSITIVE_INFINITY))) {
            assertTrue(guardar(punto) is AppResult.Error)
        }
        assertNull(repo.guardada)
        val punto = Coordenadas(-34.8, -58.3)
        assertTrue(guardar(punto) is AppResult.Success)
        assertEquals(punto, repo.guardada)
    }

    @Test fun `consulta el barbero solicitado y rechaza identificadores invalidos`() = runTest {
        val repo = FakeUbicacion()
        val obtener = ObtenerUbicacionBarberiaUseCase(repo)
        assertTrue(obtener("") is AppResult.Error)
        assertTrue(obtener("otro/barbero") is AppResult.Error)
        obtener("barbero-del-turno")
        assertEquals("barbero-del-turno", repo.consultado)
    }

    @Test fun `no consulta rutas con coordenadas invalidas`() = runTest {
        var consultas = 0
        val calcular = CalcularRutaBarberiaUseCase(object : RutaRepository {
            override suspend fun calcular(origen: Coordenadas, destino: Coordenadas): AppResult<RutaBarberia> {
                consultas++
                return AppResult.Error(DomainError.Network)
            }
        })
        assertTrue(calcular(Coordenadas(Double.NaN, 0.0), Coordenadas(0.0, 0.0)) is AppResult.Error)
        assertEquals(0, consultas)
    }

    private class FakeUbicacion : UbicacionBarberiaRepository {
        var guardada: Coordenadas? = null
        var consultado: String? = null
        override suspend fun obtenerMiUbicacion() = AppResult.Success(guardada)
        override suspend fun obtenerUbicacion(barberoId: String): AppResult<Coordenadas?> {
            consultado = barberoId
            return AppResult.Success(guardada)
        }
        override suspend fun guardarMiUbicacion(ubicacion: Coordenadas): AppResult<Unit> {
            guardada = ubicacion
            return AppResult.Success(Unit)
        }
    }
}
