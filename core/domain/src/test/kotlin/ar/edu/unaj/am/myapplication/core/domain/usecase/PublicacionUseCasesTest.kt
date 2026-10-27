package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.testing.FakePublicacionRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PublicacionUseCasesTest {
    @Test fun `description edit validates length and trims before saving`() = runTest {
        val repo = FakePublicacionRepository()
        val editar = ModificarDescripcionPublicacionUseCase(repo)
        assertTrue(editar("id", "x".repeat(301)) is AppResult.Error)
        assertTrue(editar("", "Texto") is AppResult.Error)
        assertTrue(repo.modificadas.isEmpty())
        assertTrue(editar("id", " Texto ") is AppResult.Success)
        assertEquals("id" to "Texto", repo.modificadas.singleOrNull())
        assertTrue(editar("id", "") is AppResult.Success)
    }
    @Test fun `trims description and preserves crop`() = runTest {
        val repo = FakePublicacionRepository()
        assertTrue(PublicarTrabajoUseCase(repo)(NuevaPublicacion("id", "content://foto", " Corte ", .5f, -.5f)) is AppResult.Success)
        assertEquals("Corte", repo.solicitudes.single().descripcion)
        assertEquals(.5f, repo.solicitudes.single().recorteX)
    }
    @Test fun `invalid image description and crop never reach repository`() = runTest {
        val repo = FakePublicacionRepository()
        val useCase = PublicarTrabajoUseCase(repo)
        val valid = NuevaPublicacion("id", "content://foto", "Corte")
        for (invalid in listOf(valid.copy(imagenLocal = ""), valid.copy(solicitudId = ""),
            valid.copy(descripcion = "a".repeat(301)), valid.copy(recorteX = Float.NaN),
            valid.copy(recorteY = 2f))) assertTrue(useCase(invalid) is AppResult.Error)
        assertTrue(repo.solicitudes.isEmpty())
    }
}
