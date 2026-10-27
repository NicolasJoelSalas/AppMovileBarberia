package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.CapturasTemporalesRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CapturasTemporalesUseCaseTest {
    private class Capturas : CapturasTemporalesRepository {
        var fallo: Exception? = null
        var descartada: String? = null
        override suspend fun crear(): String { fallo?.let { throw it }; return "content://captura/1" }
        override suspend fun descartar(uri: String) { fallo?.let { throw it }; descartada = uri }
    }
    @Test fun `entrega la URI y descarta la captura seleccionada`() = runTest {
        val repo = Capturas(); val caso = CapturasTemporalesUseCase(repo)
        assertEquals(AppResult.Success("content://captura/1"), caso.crear())
        caso.descartar("content://captura/1")
        assertEquals("content://captura/1", repo.descartada)
    }
    @Test fun `un fallo tecnico genera un motivo para la presentacion`() = runTest {
        val repo = Capturas().apply { fallo = IllegalStateException("detalle interno") }
        assertEquals(AppResult.Error(DomainError.Known(ErrorReason.CAPTURE_FAILED)), CapturasTemporalesUseCase(repo).crear())
        CapturasTemporalesUseCase(repo).descartar("content://captura/1")
    }
    @Test fun `cancelar la operacion no se convierte en error de pantalla`() = runTest {
        val caso = CapturasTemporalesUseCase(Capturas().apply { fallo = CancellationException() })
        try { caso.crear(); fail("Debe propagar la cancelacion") } catch (_: CancellationException) { }
        try { caso.descartar("content://captura/1"); fail("Debe propagar la cancelacion") } catch (_: CancellationException) { }
    }
}
