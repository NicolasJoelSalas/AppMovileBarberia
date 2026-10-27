package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.CapturasTemporalesRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

class CapturasTemporalesUseCase @Inject constructor(private val repository: CapturasTemporalesRepository) {
    suspend fun crear(): AppResult<String> = try { AppResult.Success(repository.crear()) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { AppResult.Error(DomainError.Known(ErrorReason.CAPTURE_FAILED)) }
    suspend fun descartar(uri: String) {
        try { repository.descartar(uri) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* La limpieza periódica de temporales reintentará la eliminación. */ }
    }
}
