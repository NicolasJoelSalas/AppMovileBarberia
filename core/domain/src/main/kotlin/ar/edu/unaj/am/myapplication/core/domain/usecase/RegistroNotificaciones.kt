package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.repository.PermisoNotificacionesRepository
import ar.edu.unaj.am.myapplication.core.domain.repository.TokenNotificacionesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Coordina la sesión del dispositivo sin conocer Firebase ni preferencias Android. */
@Singleton
class RegistroNotificaciones @Inject constructor(
    private val tokens: TokenNotificacionesRepository,
    private val registrar: RegistrarDispositivoUseCase,
    private val desvincular: DesvincularDispositivoUseCase
) {
    private val mutex = Mutex()
    @Volatile var sesionCerrada = false
        private set
    suspend fun sincronizar() {
        sesionCerrada = false
        registrar(tokens.obtenerToken())
    }
    suspend fun registrar(token: String) = mutex.withLock {
        if (!sesionCerrada) registrar(tokens.instalacionId, token)
    }
    suspend fun cerrarSesion() {
        sesionCerrada = true
        mutex.withLock {
            try { tokens.eliminarToken() }
            finally { desvincular(tokens.instalacionId) }
        }
    }
}

class SolicitudPermisoNotificaciones @Inject constructor(private val preferencias: PermisoNotificacionesRepository) {
    val solicitado get() = preferencias.solicitado
    fun registrar() = preferencias.registrarSolicitud()
}
