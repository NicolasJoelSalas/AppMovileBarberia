package ar.edu.unaj.am.myapplication

import android.app.Application
import kotlinx.coroutines.launch
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import dagger.Lazy
import ar.edu.unaj.am.myapplication.features.notifications.AvisosTurnos
import ar.edu.unaj.am.myapplication.features.notifications.RegistroNotificaciones
import ar.edu.unaj.am.myapplication.core.domain.repository.SesionActual

@HiltAndroidApp
class ComposeAcademyApp : Application(), ar.edu.unaj.am.myapplication.features.notifications.NotificacionesDependencies,
    ar.edu.unaj.am.myapplication.features.notifications.TurnosWorkerDependencies {
    @Inject lateinit var avisosProvider: Lazy<AvisosTurnos>
    @Inject lateinit var registroProvider: Lazy<RegistroNotificaciones>
    @Inject lateinit var sesionActual: SesionActual
    override val avisosTurnos get() = avisosProvider.get()
    private val notificationScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    override fun puedeRecibirNotificaciones(uid: String): Boolean =
        sesionActual.usuarioId() == uid && !registroProvider.get().sesionCerrada
    override fun sincronizarTokenNotificaciones(token: String) {
        notificationScope.launch {
            try { registroProvider.get().registrar(token) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { android.util.Log.w("BarberiaFCM", "No se pudo registrar el dispositivo", e) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ar.edu.unaj.am.myapplication.features.notifications.crearCanalTurnos(this)
    }

}
