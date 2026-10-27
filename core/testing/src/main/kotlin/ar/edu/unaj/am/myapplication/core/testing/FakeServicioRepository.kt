package ar.edu.unaj.am.myapplication.core.testing

import ar.edu.unaj.am.myapplication.core.domain.model.AppResult
import ar.edu.unaj.am.myapplication.core.domain.model.ServicioBarbero
import ar.edu.unaj.am.myapplication.core.domain.repository.ServicioRepository
import kotlinx.coroutines.CompletableDeferred

/** Simulación en memoria: permite probar errores, reintentos y guardados pendientes. */
class FakeServicioRepository : ServicioRepository {
    var guardado: ServicioBarbero? = null
    var estado: Pair<String, Boolean>? = null
    var resultado: AppResult<Unit> = AppResult.Success(Unit)
    var pendiente: CompletableDeferred<AppResult<Unit>>? = null
    var llamadas = 0
        private set

    override suspend fun obtenerMisServicios() = AppResult.Success(listOfNotNull(guardado))

    override suspend fun guardar(servicio: ServicioBarbero): AppResult<Unit> {
        llamadas++
        val result = pendiente?.await() ?: resultado
        if (result is AppResult.Success) guardado = servicio
        return result
    }

    override suspend fun cambiarEstado(id: String, activo: Boolean): AppResult<Unit> {
        estado = id to activo
        if (resultado is AppResult.Success && guardado?.id == id) {
            guardado = guardado?.copy(activo = activo)
        }
        return resultado
    }
}
