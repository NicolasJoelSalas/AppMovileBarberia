package ar.edu.unaj.am.myapplication.reminders

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ar.edu.unaj.am.myapplication.MainActivity
import ar.edu.unaj.am.myapplication.R
import com.google.firebase.auth.FirebaseAuth
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TurnoReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val scheduler = TurnoReminderScheduler(context)
        if (intent.action != TurnoReminderScheduler.FIRE) {
            if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED, "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED")) scheduler.restore()
            return
        }
        val id = intent.data?.lastPathSegment ?: return
        val reminder = scheduler.reminders().find { it.id == id } ?: return
        val now = System.currentTimeMillis()
        // Nunca mostrar información de otra cuenta ni avisos de un turno que ya empezó.
        if (reminder.owner != FirebaseAuth.getInstance().currentUser?.uid || now >= reminder.inicio) {
            scheduler.cancel(id)
            return
        }
        if (now < reminder.trigger) return
        scheduler.cancel(id)
        if (!scheduler.canNotify()) return
        val hora = Instant.ofEpochMilli(reminder.inicio).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))
        val openApp = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(context, TurnoReminderScheduler.CHANNEL)
            .setSmallIcon(R.drawable.ic_turno_reminder)
            .setContentTitle("Tu turno es en una hora")
            .setContentText("Tenés un turno en la barbería a las $hora.")
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        try {
            context.getSystemService(NotificationManager::class.java).notify(id, 0, notification)
        } catch (_: SecurityException) { /* El usuario puede revocar notificaciones en cualquier momento. */ }
    }
}
