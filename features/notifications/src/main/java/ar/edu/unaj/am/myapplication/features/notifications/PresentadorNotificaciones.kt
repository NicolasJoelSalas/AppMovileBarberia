package ar.edu.unaj.am.myapplication.features.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

fun crearCanalTurnos(context: Context) {
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel("cambios_turnos", context.getString(R.string.notifications_channel), NotificationManager.IMPORTANCE_DEFAULT))
}

/** Presentación Android: no consulta datos, guarda preferencias ni programa trabajos. */
class PresentadorNotificaciones(context: Context) {
    private val context = context.applicationContext
    fun tienePermiso() = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    fun cancelarTodas() = context.getSystemService(NotificationManager::class.java).cancelAll()
    fun mostrar(turnoId: String, tipo: String) {
        crearCanalTurnos(context)
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        intent.putExtra("abrir_turnos_fcm", true)
        val id = turnoId.hashCode()
        val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val texto = when (tipo) {
            "cancelacion_cliente" -> R.string.notifications_cancelacion_cliente
            "alta" -> R.string.notifications_alta
            "baja" -> R.string.notifications_baja
            else -> R.string.notifications_modificacion
        }
        NotificationManagerCompat.from(context).notify("turnos_locales", id,
            NotificationCompat.Builder(context, "cambios_turnos").setSmallIcon(R.drawable.ic_turno_notification)
                .setContentTitle(context.getString(if (tipo == "cancelacion_cliente") R.string.notifications_cliente_title
                    else R.string.notifications_title)).setContentText(context.getString(texto))
                .setContentIntent(pending).setAutoCancel(true).build())
    }

    fun mostrarRemota(texto: String, titulo: String?, eventoId: String) {
        if (!tienePermiso()) return
        crearCanalTurnos(context)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        launch.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        launch.putExtra("abrir_turnos_fcm", true)
        val id = eventoId.hashCode()
        val pending = PendingIntent.getActivity(context,id,launch,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        NotificationManagerCompat.from(context).notify(id,NotificationCompat.Builder(context,"cambios_turnos")
            .setSmallIcon(R.drawable.ic_turno_notification)
            .setContentTitle(titulo ?: context.getString(R.string.notifications_title))
            .setContentText(texto).setContentIntent(pending).setAutoCancel(true).build())
    }
}
