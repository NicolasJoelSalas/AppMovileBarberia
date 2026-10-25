package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.ServicioRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ServicioUseCasesTest {
    private class Fake : ServicioRepository {
        var guardado: ServicioBarbero? = null
        var estado: Pair<String, Boolean>? = null
        override suspend fun obtenerMisServicios() = AppResult.Success(emptyList<ServicioBarbero>())
        override suspend fun guardar(servicio: ServicioBarbero): AppResult<Unit> {
            guardado = servicio
            return AppResult.Success(Unit)
        }
        override suspend fun cambiarEstado(id: String, activo: Boolean): AppResult<Unit> {
            estado = id to activo
            return AppResult.Success(Unit)
        }
    }
    @Test fun `invalid values never reach persistence`() = runTest {
        val repo = Fake()
        val guardar = GuardarServicioUseCase(repo)
        val valido = ServicioBarbero(nombre = "Corte", precioCentavos = 1250050, duracionMinutos = 40)
        for (invalid in listOf(valido.copy(nombre = "   "), valido.copy(nombre = "x".repeat(121)),
            valido.copy(precioCentavos = -1), valido.copy(precioCentavos = 100000000001L),
            valido.copy(duracionMinutos = 4), valido.copy(duracionMinutos = 241))) {
            assertTrue(guardar(invalid) is AppResult.Error)
        }
        assertNull(repo.guardado)
        assertTrue(guardar(valido.copy(nombre = "  Corte  ", duracionMinutos = 60)) is AppResult.Success)
        assertEquals("Corte", repo.guardado?.nombre)
        assertEquals(60, repo.guardado?.duracionMinutos)
        assertEquals(1250050L, repo.guardado?.precioCentavos)
    }
    @Test fun `deactivation and reactivation preserve the service identity`() = runTest {
        val repo = Fake()
        val estado = CambiarEstadoServicioUseCase(repo)
        assertTrue(estado("", false) is AppResult.Error)
        assertNull(repo.estado)
        assertTrue(estado("corte", false) is AppResult.Success)
        assertEquals("corte" to false, repo.estado)
        assertTrue(estado("corte", true) is AppResult.Success)
        assertEquals("corte" to true, repo.estado)
    }
}
