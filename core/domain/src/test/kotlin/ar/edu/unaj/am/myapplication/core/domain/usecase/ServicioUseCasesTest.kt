package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.testing.FakeServicioRepository
import ar.edu.unaj.am.myapplication.core.testing.servicioBarbero
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ServicioUseCasesTest {
    @Test fun `invalid values never reach persistence`() = runTest {
        val repo = FakeServicioRepository()
        val guardar = GuardarServicioUseCase(repo)
        val valido = servicioBarbero()
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
        val repo = FakeServicioRepository()
        val estado = CambiarEstadoServicioUseCase(repo)
        assertTrue(estado("", false) is AppResult.Error)
        assertNull(repo.estado)
        assertTrue(estado("corte", false) is AppResult.Success)
        assertEquals("corte" to false, repo.estado)
        assertTrue(estado("corte", true) is AppResult.Success)
        assertEquals("corte" to true, repo.estado)
    }
}
