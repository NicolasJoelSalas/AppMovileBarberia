package ar.edu.unaj.am.myapplication

import android.app.Application
import kotlinx.coroutines.launch
import ar.edu.unaj.am.myapplication.di.AppContainer

class ComposeAcademyApp : Application(), ar.edu.unaj.am.myapplication.features.notifications.NotificacionesDependencies {
    private val notificationScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    override fun puedeRecibirNotificaciones(uid: String): Boolean =
        container.clienteActualId() == uid && !container.notificaciones.sesionCerrada
    override fun sincronizarTokenNotificaciones(token: String) {
        notificationScope.launch {
            try { container.notificaciones.registrar(token) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { android.util.Log.w("BarberiaFCM", "No se pudo registrar el dispositivo", e) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ar.edu.unaj.am.myapplication.features.notifications.crearCanalTurnos(this)
    }

    /** Una única instancia para toda la app: así la lista y el detalle comparten el mismo repositorio. */
    val container: AppContainer by lazy { AppContainer(this) }
}
