package ar.edu.unaj.am.myapplication.session

import ar.edu.unaj.am.myapplication.core.data.repository.FirestoreTurnoRepository
import ar.edu.unaj.am.myapplication.core.data.repository.PublicacionesClienteLocal
import ar.edu.unaj.am.myapplication.core.data.session.*
import ar.edu.unaj.am.myapplication.core.domain.repository.SesionActual
import ar.edu.unaj.am.myapplication.notifications.AvisosTurnos
import ar.edu.unaj.am.myapplication.core.domain.usecase.RegistroNotificaciones
import javax.inject.Inject
import javax.inject.Singleton

/** Coordina el cierre de sesión; las dependencias las crea Hilt. */
@Singleton
class GestorSesion @Inject constructor(
    private val auth: SesionActual,
    private val avisosTurnos: AvisosTurnos,
    private val notificaciones: RegistroNotificaciones,
    private val turnoRepository: FirestoreTurnoRepository,
    private val publicacionesLocales: PublicacionesClienteLocal,
    private val sesionClienteLocal: SesionClienteLocal,
    private val sesionBarberoLocal: SesionBarberoLocal
) {
    fun clienteActualId(): String? = auth.usuarioId()

    suspend fun limpiarSesion(uid: String) {
        avisosTurnos.detener()
        try {
            kotlinx.coroutines.withTimeout(8000) { notificaciones.cerrarSesion() }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            android.util.Log.w("BarberiaFCM", "Desvinculación pendiente por falta de conexión")
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (e: Exception) {
            android.util.Log.w("BarberiaFCM", "No se pudo desvincular el dispositivo", e)
        }
        try {
            turnoRepository.limpiarSesion(uid)
        } finally {
            try {
                publicacionesLocales.limpiar(uid)
            } finally {
                sesionClienteLocal.limpiar(uid)
                sesionBarberoLocal.limpiar(uid)
            }
        }
    }

}
