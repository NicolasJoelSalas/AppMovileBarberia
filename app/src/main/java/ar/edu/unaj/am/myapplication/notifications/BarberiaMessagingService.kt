package ar.edu.unaj.am.myapplication.notifications

import ar.edu.unaj.am.myapplication.features.notifications.PresentadorNotificaciones
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import java.util.UUID

interface NotificacionesDependencies {
    fun sincronizarTokenNotificaciones(token: String)
    fun puedeRecibirNotificaciones(uid: String): Boolean
}

@AndroidEntryPoint
class BarberiaMessagingService : FirebaseMessagingService() {
    @Inject lateinit var presentador: PresentadorNotificaciones
    override fun onNewToken(token: String) { (application as? NotificacionesDependencies)?.sincronizarTokenNotificaciones(token) }
    override fun onMessageReceived(message: RemoteMessage) {
        val destinatario = message.data["barberoId"] ?: return
        if ((application as? NotificacionesDependencies)?.puedeRecibirNotificaciones(destinatario) != true) return
        presentador.mostrarRemota(message.notification?.body ?: message.data["texto"].orEmpty(),
            message.notification?.title, message.data["eventoId"] ?: message.messageId ?: UUID.randomUUID().toString())
    }
}
