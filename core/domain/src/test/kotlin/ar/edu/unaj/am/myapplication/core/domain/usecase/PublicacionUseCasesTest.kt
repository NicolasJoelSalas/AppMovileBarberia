package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.PublicacionRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PublicacionUseCasesTest {
    private class Fake : PublicacionRepository {
        var enviada: NuevaPublicacion? = null
        override fun observar() = flowOf(AppResult.Success(emptyList<Publicacion>()))
        override suspend fun publicar(publicacion: NuevaPublicacion): AppResult<Unit> {
            enviada = publicacion; return AppResult.Success(Unit)
        }
        override suspend fun eliminar(id: String) = AppResult.Success(Unit)
    }
    @Test fun `trims description and preserves crop`() = runTest {
        val repo = Fake()
        assertTrue(PublicarTrabajoUseCase(repo)(NuevaPublicacion("id", "content://foto", " Corte ", .5f, -.5f)) is AppResult.Success)
        assertEquals("Corte", repo.enviada!!.descripcion)
        assertEquals(.5f, repo.enviada!!.recorteX)
    }
    @Test fun `invalid image description and crop never reach repository`() = runTest {
        val repo = Fake()
        val useCase = PublicarTrabajoUseCase(repo)
        val valid = NuevaPublicacion("id", "content://foto", "Corte")
        for (invalid in listOf(valid.copy(imagenLocal = ""), valid.copy(solicitudId = ""),
            valid.copy(descripcion = "a".repeat(301)), valid.copy(recorteX = Float.NaN),
            valid.copy(recorteY = 2f))) assertTrue(useCase(invalid) is AppResult.Error)
        assertNull(repo.enviada)
    }
}
