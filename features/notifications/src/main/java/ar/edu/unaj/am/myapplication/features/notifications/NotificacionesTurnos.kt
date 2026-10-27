package ar.edu.unaj.am.myapplication.features.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ar.edu.unaj.am.myapplication.core.domain.usecase.RegistrarDispositivoUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.DesvincularDispositivoUseCase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.UUID

interface NotificacionesDependencies {
    fun sincronizarTokenNotificaciones(token: String)
    fun puedeRecibirNotificaciones(uid: String): Boolean
}

class RegistroNotificaciones(
    context: Context,
    private val registrar: RegistrarDispositivoUseCase,
    private val desvincular: DesvincularDispositivoUseCase
) {
    private val mutex = Mutex()
    @Volatile var sesionCerrada = false
        private set
    private val preferences = context.applicationContext.getSharedPreferences("fcm_installation", Context.MODE_PRIVATE)
    private val instalacionId = preferences.getString("id", null) ?: UUID.randomUUID().toString().also {
        preferences.edit().putString("id", it).apply()
    }
    suspend fun sincronizar() {
        sesionCerrada = false
        val token = suspendCancellableCoroutine<String> { continuation ->
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
        }
        registrar(token)
    }
    suspend fun registrar(token: String) = mutex.withLock {
        if (!sesionCerrada) registrar(instalacionId, token)
    }
    suspend fun cerrarSesion() {
        sesionCerrada = true
        mutex.withLock {
            try { FirebaseMessaging.getInstance().deleteToken().esperarTokenEliminado() }
            finally { desvincular(instalacionId) }
        }
    }
}

private suspend fun com.google.android.gms.tasks.Task<Void>.esperarTokenEliminado(): Unit =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { if (continuation.isActive) continuation.resume(Unit) }
        addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    }

fun crearCanalTurnos(context: Context) {
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel("cambios_turnos", context.getString(R.string.notifications_channel), NotificationManager.IMPORTANCE_DEFAULT)
    )
}

@Composable
fun PermisoNotificacionesBarbero() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        crearCanalTurnos(context)
        val preferences = context.getSharedPreferences("fcm_permission", Context.MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 && !preferences.getBoolean("solicitado", false) &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            preferences.edit().putBoolean("solicitado", true).apply()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

class BarberiaMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        (application as? NotificacionesDependencies)?.sincronizarTokenNotificaciones(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val destinatario = message.data["barberoId"] ?: return
        if ((application as? NotificacionesDependencies)?.puedeRecibirNotificaciones(destinatario) != true) return
        crearCanalTurnos(this)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return
        launch.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        launch.putExtra("abrir_turnos_fcm", true)
        val id = (message.data["eventoId"] ?: message.messageId ?: UUID.randomUUID().toString()).hashCode()
        val pending = PendingIntent.getActivity(this, id, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        NotificationManagerCompat.from(this).notify(id, NotificationCompat.Builder(this, "cambios_turnos")
            .setSmallIcon(R.drawable.ic_turno_notification)
            .setContentTitle(message.notification?.title ?: getString(R.string.notifications_title))
            .setContentText(message.notification?.body ?: message.data["texto"].orEmpty())
            .setContentIntent(pending).setAutoCancel(true).build())
    }
}
