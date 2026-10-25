package ar.edu.unaj.am.myapplication.reminders

import ar.edu.unaj.am.myapplication.R

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
import ar.edu.unaj.am.myapplication.core.domain.usecase.instantesRecordatorios
import ar.edu.unaj.am.myapplication.core.domain.usecase.anticipacionesRecordatorio
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject

internal data class TurnoReminder(val id: String, val owner: String, val inicio: Long,
    val pending: List<Long> = anticipacionesRecordatorio, val disabled: Boolean = false, val version: Int = 2)

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
        val localized = androidx.core.content.ContextCompat.getContextForLanguage(context)
        val channel = NotificationChannel(CHANNEL, localized.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_HIGH)
        channel.description = localized.getString(R.string.reminder_channel_description)
        channel.enableVibration(true)
        channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
        notifications.createNotificationChannel(channel)
    }

    fun reminders(): List<TurnoReminder> = prefs.all.mapNotNull { (_, value) ->
        runCatching {
            val json = JSONObject(value as String)
            val pending = json.optJSONArray("pending")
            TurnoReminder(json.getString("id"), json.getString("owner"), json.getLong("inicio"),
                pending?.let { array -> (0 until array.length()).map { array.getLong(it) } } ?: listOf(3_600_000L),
                json.optBoolean("disabled", false), json.optInt("version", 1))
        }.getOrNull()
    }

    fun activeIds(): Set<String> = if (!canScheduleExact() || !canNotify()) emptySet() else
        reminders().filter { it.owner == owner && !it.disabled &&
            it.pending.any { offset -> it.inicio - offset > System.currentTimeMillis() } }.map { it.id }.toSet()

    fun hasAskedPermissions(): Boolean = prefs.getBoolean("permissions_asked_$owner", false)
    fun markPermissionsAsked() { prefs.edit().putBoolean("permissions_asked_$owner", true).apply() }

    private fun save(reminder: TurnoReminder): Boolean = prefs.edit().putString(reminder.id,
        JSONObject().put("id", reminder.id).put("owner", reminder.owner).put("inicio", reminder.inicio)
            .put("pending", org.json.JSONArray(reminder.pending)).put("disabled", reminder.disabled)
            .put("version", 2).toString()).commit()

    private fun arm(reminder: TurnoReminder): Boolean {
        if (!canScheduleExact() || !canNotify()) return false
        return try {
            reminder.pending.filter { reminder.inicio - it > System.currentTimeMillis() }.forEach { offset ->
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.inicio - offset,
                    operation(reminder.id, offset))
            }
            true
        } catch (_: SecurityException) { false }
    }

    fun schedule(turno: Turno): Boolean {
        val uid = owner ?: return false
        val now = System.currentTimeMillis()
        val inicio = turno.inicioMillis ?: return false
        if (uid != turno.clienteId || instantesRecordatorios(turno, now).isEmpty()) return false
        remove(turno.id)
        val reminder = TurnoReminder(turno.id, uid, inicio,
            anticipacionesRecordatorio.filter { inicio - it > now })
        return save(reminder) && arm(reminder)
    }

    fun cancel(id: String) {
        val reminder = reminders().find { it.id == id }
        remove(id)
        // Guarda la elección para que una sincronización no lo reactive automáticamente.
        if (reminder != null) save(reminder.copy(disabled = true, pending = emptyList()))
    }

    private fun remove(id: String) {
        alarms.cancel(operation(id)) // Migración de la alarma de la versión anterior.
        anticipacionesRecordatorio.forEach { alarms.cancel(operation(id, it)) }
        prefs.edit().remove(id).commit()
        notifications.cancel(id, 0)
        anticipacionesRecordatorio.forEach { notifications.cancel("$id-$it", 0) }
    }

    fun cancelAll() { reminders().forEach { remove(it.id) } }

    fun consume(id: String, offset: Long) {
        val reminder = reminders().find { it.id == id } ?: return
        save(reminder.copy(pending = reminder.pending - offset))
    }

    /** Conserva alarmas tras reiniciar y elimina recordatorios vencidos o de otra sesión. */
    fun restore() {
        reminders().forEach {
            if (it.owner != owner || it.inicio <= System.currentTimeMillis()) remove(it.id)
            else if (!it.disabled) arm(it)
        }
    }

    /** Recibe el catálogo completo del cliente, antes del filtro por semana. */
    fun reconcile(turnos: List<Turno>) {
        val existing = reminders().associateBy { it.id }
        val ids = turnos.map { it.id }.toSet()
        existing.values.filter { it.owner != owner || it.id !in ids }.forEach { remove(it.id) }
        turnos.forEach { turno ->
            val saved = existing[turno.id]
            val inicio = turno.inicioMillis
            if (turno.clienteId != owner || inicio == null || inicio <= System.currentTimeMillis() ||
                turno.estado.lowercase() !in setOf("confirmado", "pendiente")) remove(turno.id)
            else if (saved?.disabled == true) {
                if (saved.inicio != inicio) save(saved.copy(inicio = inicio))
            } else if (saved == null || saved.inicio != turno.inicioMillis || saved.version < 2) schedule(turno)
            else arm(saved)
        }
    }

    private fun operation(id: String, offset: Long? = null): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, TurnoReminderReceiver::class.java).apply {
            action = FIRE
            data = Uri.Builder().scheme("barberia").authority("recordatorio").appendPath(id)
                .apply { if (offset != null) appendPath(offset.toString()) }.build()
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    companion object {
        const val PREFS = "turno_reminders"
        const val CHANNEL = "turno_reminders_v1"
        const val FIRE = "ar.edu.unaj.am.myapplication.RECORDATORIO_TURNO"
    }
}
