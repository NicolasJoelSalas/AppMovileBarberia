package ar.edu.unaj.am.myapplication.reminders

import ar.edu.unaj.am.myapplication.R

import androidx.compose.ui.res.stringResource

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
    val resources = androidx.compose.ui.platform.LocalResources.current
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
            !scheduler.canNotify() -> message(resources.getString(R.string.app_habilita_las_notificaciones_de_la_app_y_el_canal_recordatorios_de_turnos_en_ajustes))
            !scheduler.canScheduleExact() -> message(resources.getString(R.string.app_para_activar_el_recordatorio_permiti_alarmas_y_recordatorios_en_ajustes))
            turno == null || !scheduler.schedule(turno) -> message(resources.getString(R.string.app_no_se_pudieron_activar_los_avisos_revisa_que_falte_mas_de_una_hora_para_el_turno_e_intenta_nuevamente))
            else -> {
                scheduler.reconcile(latestTurnos)
                message(resources.getString(R.string.app_avisos_activados_para_los_horarios_que_aun_no_pasaron))
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
            message(resources.getString(R.string.app_no_se_activo_el_recordatorio_necesitamos_permiso_para_mostrar_notificaciones))
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
        val fechas = instantesRecordatorios(selected, System.currentTimeMillis()).joinToString(" · ") {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
        }
        AlertDialog(onDismissRequest = { selectedId = null },
            title = { Text(stringResource(R.string.app_avisos_de_tus_turnos)) },
            text = { Text(stringResource(R.string.reminder_explanation, fechas)) },
            confirmButton = { TextButton(onClick = {
                pendingId = selected.id
                selectedId = null
                if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else exactOrFinish()
            }) { Text(stringResource(R.string.app_activar_recordatorio)) } },
            dismissButton = { TextButton(onClick = { selectedId = null }) { Text(stringResource(R.string.app_volver)) } })
    }
    if (explainExact) AlertDialog(onDismissRequest = { explainExact = false; pendingId = null },
        title = { Text(stringResource(R.string.app_permitir_alarmas_y_recordatorios)) },
        text = { Text(stringResource(R.string.app_para_avisarte_a_la_hora_indicada_aunque_la_app_este_cerrada_activa_el_permiso_de_alarmas_y_recordatorios_en_la_siguiente_pantalla)) },
        confirmButton = { TextButton(onClick = {
            explainExact = false
            try {
                exactPermission.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${context.packageName}")))
            } catch (_: ActivityNotFoundException) {
                pendingId = null
                message(resources.getString(R.string.app_este_telefono_no_permite_abrir_el_permiso_busca_alarmas_y_recordatorios_en_ajustes))
            }
        }) { Text(stringResource(R.string.app_abrir_ajustes)) } },
        dismissButton = { TextButton(onClick = { explainExact = false; pendingId = null }) { Text(stringResource(R.string.app_volver)) } })
    return TurnoReminderControl(activeIds) { turno ->
        if (turno.id in scheduler.activeIds()) {
            scheduler.cancel(turno.id)
            activeIds = scheduler.activeIds()
            message(resources.getString(R.string.app_recordatorio_cancelado))
        } else selectedId = turno.id
    }
}
