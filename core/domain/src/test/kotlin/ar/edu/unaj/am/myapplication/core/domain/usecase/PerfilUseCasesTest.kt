package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.PerfilRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PerfilUseCasesTest {
    private val repository = FakePerfilRepository()
    private val actualizar = ActualizarMiNombreUseCase(repository)

    @Test fun `rechaza nombres vacios y demasiado largos sin escribir`() = runTest {
        assertTrue(actualizar("   ") is AppResult.Error)
        assertTrue(actualizar("a".repeat(121)) is AppResult.Error)
        assertEquals(0, repository.escrituras)
    }

    @Test fun `normaliza espacios y acepta el limite de longitud`() = runTest {
        assertTrue(actualizar("  Ana Pérez  ") is AppResult.Success)
        assertEquals("Ana Pérez", repository.nombre)
        assertTrue(actualizar("a".repeat(120)) is AppResult.Success)
    }

    @Test fun `devuelve el error de persistencia`() = runTest {
        repository.resultado = AppResult.Error(DomainError.Network)
        assertEquals(repository.resultado, actualizar("Ana"))
    }

    private class FakePerfilRepository : PerfilRepository {
        var nombre = ""
        var escrituras = 0
        var resultado: AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun obtenerMiPerfil() = AppResult.Success(PerfilUsuario("Ana", "ana@example.com", false))
        override suspend fun actualizarMiNombre(nombre: String): AppResult<Unit> {
            this.nombre = nombre
            escrituras++
            return resultado
        }
    }
}
