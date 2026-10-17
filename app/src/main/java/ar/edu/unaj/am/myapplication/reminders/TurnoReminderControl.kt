package ar.edu.unaj.am.myapplication.reminders

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import ar.edu.unaj.am.myapplication.core.domain.model.Turno
import ar.edu.unaj.am.myapplication.core.domain.usecase.instantesRecordatorios
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal data class TurnoReminderControl(val activeIds: Set<String>, val onClick: (Turno) -> Unit)

@Composable
internal fun rememberTurnoReminderControl(turnos: List<Turno>?): TurnoReminderControl {
    val context = LocalContext.current
    val scheduler = remember(context) { TurnoReminderScheduler(context.applicationContext) }
    val latestTurnos by rememberUpdatedState(turnos.orEmpty())
    var activeIds by remember { mutableStateOf(scheduler.activeIds()) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingId by rememberSaveable { mutableStateOf<String?>(null) }
    var explainExact by rememberSaveable { mutableStateOf(false) }
    fun message(text: String) { Toast.makeText(context, text, Toast.LENGTH_LONG).show() }
    fun finish() {
        val turno = latestTurnos.find { it.id == pendingId }
        pendingId = null
        when {
            !scheduler.canNotify() -> message("Habilitá las notificaciones de la app y el canal Recordatorios de turnos en Ajustes.")
            !scheduler.canScheduleExact() -> message("Para activar el recordatorio, permití Alarmas y recordatorios en Ajustes.")
            turno == null || !scheduler.schedule(turno) -> message("No se pudieron activar los avisos. Revisá que falte más de una hora para el turno e intentá nuevamente.")
            else -> {
                scheduler.reconcile(latestTurnos)
                message("Avisos activados para los horarios que aún no pasaron.")
            }
        }
        activeIds = scheduler.activeIds()
    }
    val exactPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { finish() }
    fun exactOrFinish() {
        if (scheduler.canScheduleExact()) finish() else explainExact = true
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) exactOrFinish()
        else {
            pendingId = null
            message("No se activó el recordatorio. Necesitamos permiso para mostrar notificaciones.")
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scheduler.restore()
        if (turnos != null) scheduler.reconcile(turnos)
        activeIds = scheduler.activeIds()
    }
    DisposableEffect(scheduler) {
        val prefs = context.getSharedPreferences(TurnoReminderScheduler.PREFS, android.content.Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            activeIds = scheduler.activeIds()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LaunchedEffect(turnos) {
        if (turnos != null) scheduler.reconcile(turnos)
        activeIds = scheduler.activeIds()
        val eligible = turnos?.firstOrNull { instantesRecordatorios(it, System.currentTimeMillis()).isNotEmpty() }
        if (eligible != null && (!scheduler.canNotify() || !scheduler.canScheduleExact()) && !scheduler.hasAskedPermissions()) {
            scheduler.markPermissionsAsked()
            selectedId = eligible.id
        }
    }
    val selected = turnos?.find { it.id == selectedId }
    if (selected != null) {
        val fechas = instantesRecordatorios(selected, System.currentTimeMillis()).joinToString(" y ") {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm"))
        }
        AlertDialog(onDismissRequest = { selectedId = null },
            title = { Text("Avisos de tus turnos") },
            text = { Text("Tus turnos incluyen avisos un día antes y una hora antes. Para este turno, los próximos avisos son: $fechas (hora local). Necesitamos los permisos del teléfono para enviarlos. El sonido depende de los ajustes de notificaciones.") },
            confirmButton = { TextButton(onClick = {
                pendingId = selected.id
                selectedId = null
                if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else exactOrFinish()
            }) { Text("Activar recordatorio") } },
            dismissButton = { TextButton(onClick = { selectedId = null }) { Text("Volver") } })
    }
    if (explainExact) AlertDialog(onDismissRequest = { explainExact = false; pendingId = null },
        title = { Text("Permitir alarmas y recordatorios") },
        text = { Text("Para avisarte a la hora indicada aunque la app esté cerrada, activá el permiso de Alarmas y recordatorios en la siguiente pantalla.") },
        confirmButton = { TextButton(onClick = {
            explainExact = false
            try {
                exactPermission.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${context.packageName}")))
            } catch (_: ActivityNotFoundException) {
                pendingId = null
                message("Este teléfono no permite abrir el permiso. Buscá Alarmas y recordatorios en Ajustes.")
            }
        }) { Text("Abrir ajustes") } },
        dismissButton = { TextButton(onClick = { explainExact = false; pendingId = null }) { Text("Volver") } })
    return TurnoReminderControl(activeIds) { turno ->
        if (turno.id in scheduler.activeIds()) {
            scheduler.cancel(turno.id)
            activeIds = scheduler.activeIds()
            message("Recordatorio cancelado.")
        } else selectedId = turno.id
    }
}
