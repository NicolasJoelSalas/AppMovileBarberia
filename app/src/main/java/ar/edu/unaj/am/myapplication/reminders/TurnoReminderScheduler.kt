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
import ar.edu.unaj.am.myapplication.core.domain.model.RecordatorioTurnoLocal as TurnoReminder
import ar.edu.unaj.am.myapplication.core.domain.model.EstadoTurno
import ar.edu.unaj.am.myapplication.core.domain.model.estadoNormalizado
import dagger.hilt.android.EntryPointAccessors

internal class TurnoReminderScheduler(private val context: Context) {
    private val dependencies = EntryPointAccessors.fromApplication(context.applicationContext, RecordatoriosDependencies::class.java)
    private val preferencias = dependencies.recordatorios()
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    val owner: String? get() = dependencies.sesion().usuarioId()

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

    fun reminders(): List<TurnoReminder> = preferencias.listar()
    fun observarCambios() = preferencias.observarCambios()

    fun activeIds(): Set<String> = if (!canScheduleExact() || !canNotify()) emptySet() else
        reminders().filter { it.owner == owner && !it.disabled &&
            it.pending.any { offset -> it.inicio - offset > System.currentTimeMillis() } }.map { it.id }.toSet()

    fun hasAskedPermissions(): Boolean = preferencias.permisoSolicitado(owner)
    fun markPermissionsAsked() { preferencias.registrarSolicitudPermiso(owner) }
    private fun save(reminder: TurnoReminder): Boolean = preferencias.guardar(reminder)

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
        preferencias.eliminar(id)
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
                turno.estadoNormalizado != EstadoTurno.PENDIENTE) remove(turno.id)
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
        const val CHANNEL = "turno_reminders_v1"
        const val FIRE = "ar.edu.unaj.am.myapplication.RECORDATORIO_TURNO"
    }
}
