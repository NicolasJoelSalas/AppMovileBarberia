package ar.edu.unaj.am.myapplication.notifications

import ar.edu.unaj.am.myapplication.core.domain.model.Turno
import ar.edu.unaj.am.myapplication.core.domain.usecase.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Coordina la escucha durante el proceso; la persistencia, programación y presentación son dependencias separadas. */
class AvisosTurnos(
    private val estado: ar.edu.unaj.am.myapplication.core.domain.repository.EstadoAvisosRepository,
    private val programador: ProgramadorAvisosTurnos,
    private val presentador: ar.edu.unaj.am.myapplication.features.notifications.PresentadorNotificaciones,
    private val consultarTurnos: ConsultarNovedadesTurnosUseCase,
    private val observarTurnos: ObservarNovedadesTurnosUseCase,
    private val usuarioActual: () -> String?,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutex = Mutex()
    private var observacion: Job? = null
    private var observandoUid: String? = null
    @Volatile private var saliendo = false

    fun sesionValida(uid: String) = !saliendo && usuarioActual() == uid && estado.usuarioId == uid

    /** El rol confirmado se conserva para las consultas en segundo plano. */
    fun iniciar(uid: String, comoCliente: Boolean = false) {
        saliendo = false
        estado.iniciar(uid, comoCliente)
        programador.iniciar(uid)
        if (observandoUid == uid && observacion?.isActive == true) return
        observacion?.cancel()
        observandoUid = uid
        observacion = scope.launch {
            while (sesionValida(uid)) {
                try {
                    observarTurnos(uid, comoCliente).collect { turnos -> mutex.withLock { procesar(uid, turnos) } }
                    return@launch
                } catch (e: CancellationException) { throw e
                } catch (e: Exception) {
                    android.util.Log.w("BarberiaAvisos", "La escucha se reintentará", e)
                    delay(30000)
                }
            }
        }
    }

    suspend fun consultar(uid: String) = mutex.withLock {
        if (sesionValida(uid)) procesar(uid, consultarTurnos(uid, estado.comoCliente))
    }

    fun detener() {
        saliendo = true
        observacion?.cancel()
        observacion = null
        observandoUid = null
        estado.cerrar()
        programador.detener()
        presentador.cancelarTodas()
    }

    private fun procesar(uid: String, turnos: List<Turno>) {
        if (!sesionValida(uid)) return
        val firmas = estado.obtenerFirmas(uid)
        if (firmas != null) {
            val cambios = if (estado.comoCliente) detectarCancelacionesCliente(firmas, turnos)
                else detectarCambiosTurnos(firmas, turnos)
            // Sin permiso se conserva la última versión: no acumular avisos antiguos al habilitarlo.
            if (presentador.tienePermiso()) {
                cambios.forEach { cambio ->
                    if (sesionValida(uid)) presentador.mostrar(cambio.turnoId, cambio.tipo)
                }
            }
        }
        // La primera consulta establece la base, sin notificar todas las reservas históricas.
        if (sesionValida(uid)) estado.guardarFirmas(uid, turnos.associate { it.id to firmaTurno(it) })
    }

}
