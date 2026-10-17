package ar.edu.unaj.am.myapplication.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import ar.edu.unaj.am.myapplication.core.domain.model.Turno
import ar.edu.unaj.am.myapplication.core.domain.usecase.instanteRecordatorio
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject

internal data class TurnoReminder(val id: String, val owner: String, val inicio: Long) {
    val trigger: Long get() = inicio - 3_600_000L
}

internal class TurnoReminderScheduler(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val owner: String? get() = FirebaseAuth.getInstance().currentUser?.uid

    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()

    fun canNotify(): Boolean {
        createChannel()
        return notifications.areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            notifications.getNotificationChannel(CHANNEL).importance != NotificationManager.IMPORTANCE_NONE
    }

    fun createChannel() {
        val channel = NotificationChannel(CHANNEL, "Recordatorios de turnos", NotificationManager.IMPORTANCE_HIGH)
        channel.description = "Aviso una hora antes de tus turnos"
        channel.enableVibration(true)
        channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
        notifications.createNotificationChannel(channel)
    }

    fun reminders(): List<TurnoReminder> = prefs.all.mapNotNull { (_, value) ->
        runCatching {
            val json = JSONObject(value as String)
            TurnoReminder(json.getString("id"), json.getString("owner"), json.getLong("inicio"))
        }.getOrNull()
    }

    fun activeIds(): Set<String> = if (!canScheduleExact() || !canNotify()) emptySet() else
        reminders().filter { it.owner == owner && it.trigger > System.currentTimeMillis() }.map { it.id }.toSet()

    fun schedule(turno: Turno): Boolean {
        val uid = owner ?: return false
        if (uid != turno.clienteId || instanteRecordatorio(turno, System.currentTimeMillis()) == null ||
            !canScheduleExact() || !canNotify()) return false
        val reminder = TurnoReminder(turno.id, uid, turno.inicioMillis!!)
        // La identidad estable del PendingIntent reemplaza una alarma anterior del mismo turno.
        return try {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.trigger, operation(reminder.id))
            val json = JSONObject().put("id", reminder.id).put("owner", uid).put("inicio", reminder.inicio)
            if (!prefs.edit().putString(reminder.id, json.toString()).commit()) {
                alarms.cancel(operation(reminder.id))
                false
            } else true
        } catch (_: SecurityException) { false }
    }

    fun cancel(id: String) {
        alarms.cancel(operation(id))
        prefs.edit().remove(id).commit()
        notifications.cancel(id, 0)
    }

    fun cancelAll() { reminders().forEach { cancel(it.id) } }

    /** Conserva alarmas tras reiniciar y elimina recordatorios vencidos o de otra sesión. */
    fun restore() {
        reminders().forEach {
            if (it.owner != owner || it.trigger <= System.currentTimeMillis()) cancel(it.id)
            else if (canScheduleExact() && canNotify()) {
                try {
                    alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, it.trigger, operation(it.id))
                } catch (_: SecurityException) { /* Se reintentará al recuperar el permiso. */ }
            }
        }
    }

    /** Recibe el catálogo completo del cliente, antes del filtro por semana. */
    fun reconcile(turnos: List<Turno>) {
        val existing = reminders().associateBy { it.id }
        val ids = turnos.map { it.id }.toSet()
        existing.values.filter { it.owner != owner || it.id !in ids }.forEach { cancel(it.id) }
        turnos.forEach { turno ->
            val saved = existing[turno.id] ?: return@forEach
            if (saved.owner != owner || instanteRecordatorio(turno, System.currentTimeMillis()) == null) cancel(turno.id)
            else if (saved.inicio != turno.inicioMillis) {
                cancel(turno.id)
                schedule(turno)
            }
        }
    }

    private fun operation(id: String): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, TurnoReminderReceiver::class.java).apply {
            action = FIRE
            data = Uri.Builder().scheme("barberia").authority("recordatorio").appendPath(id).build()
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    companion object {
        const val PREFS = "turno_reminders"
        const val CHANNEL = "turno_reminders_v1"
        const val FIRE = "ar.edu.unaj.am.myapplication.RECORDATORIO_TURNO"
    }
}
