package ar.edu.unaj.am.myapplication.features.notifications

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import ar.edu.unaj.am.myapplication.core.domain.model.Turno
import ar.edu.unaj.am.myapplication.core.domain.usecase.ConsultarNovedadesTurnosUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.ObservarNovedadesTurnosUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.detectarCambiosTurnos
import ar.edu.unaj.am.myapplication.core.domain.usecase.firmaTurno
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AvisosTurnos(
    context: Context,
    private val consultarTurnos: ConsultarNovedadesTurnosUseCase,
    private val observarTurnos: ObservarNovedadesTurnosUseCase,
    private val usuarioActual: () -> String?
) {
    private val context = context.applicationContext
    private val preferences = this.context.getSharedPreferences("avisos_turnos", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var observacion: Job? = null
    private var observandoUid: String? = null
    @Volatile private var saliendo = false

    fun sesionValida(uid: String) = !saliendo && usuarioActual() == uid && preferences.getString("uid", null) == uid

    /** Se llama cuando la app confirmó el rol de barbero. El trabajo persiste tras cerrar el proceso. */
    fun iniciar(uid: String) {
        saliendo = false
        preferences.edit().putString("uid", uid).apply()
        val request = PeriodicWorkRequestBuilder<TurnosWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf("uid" to uid)).addTag("avisos_turnos").build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("avisos_turnos_$uid", ExistingPeriodicWorkPolicy.KEEP, request)
        if (observandoUid == uid && observacion?.isActive == true) return
        observacion?.cancel()
        observandoUid = uid
        observacion = scope.launch {
            while (sesionValida(uid)) {
                try {
                    observarTurnos(uid).collect { turnos -> mutex.withLock { procesar(uid, turnos) } }
                    return@launch
                } catch (e: CancellationException) { throw e
                } catch (e: Exception) {
                    android.util.Log.w("BarberiaAvisos", "La escucha se reintentará", e)
                    delay(30000)
                }
            }
        }
    }

    suspend fun consultar(uid: String) = mutex.withLock {
        if (sesionValida(uid)) procesar(uid, consultarTurnos(uid))
    }

    fun detener() {
        saliendo = true
        observacion?.cancel()
        observacion = null
        observandoUid = null
        preferences.edit().remove("uid").apply()
        WorkManager.getInstance(context).cancelAllWorkByTag("avisos_turnos")
        context.getSystemService(NotificationManager::class.java).cancelAll()
    }

    private fun procesar(uid: String, turnos: List<Turno>) {
        if (!sesionValida(uid)) return
        val anterior = preferences.getString("snapshot_$uid", null)
        val actuales = JSONObject()
        turnos.forEach { actuales.put(it.id, JSONArray(firmaTurno(it))) }
        if (anterior != null) {
            val json = JSONObject(anterior)
            val firmas = json.keys().asSequence().associateWith { id ->
                val valores = json.getJSONArray(id)
                (0 until valores.length()).map { index -> if (valores.isNull(index)) null else valores.getString(index) }
            }
            val cambios = detectarCambiosTurnos(firmas, turnos)
            // Sin permiso se conserva la última versión: no acumular avisos antiguos al habilitarlo.
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                cambios.forEach { cambio ->
                    if (sesionValida(uid)) mostrar(cambio.turnoId, cambio.tipo)
                }
            }
        }
        // La primera consulta establece la base, sin notificar todas las reservas históricas.
        if (sesionValida(uid)) check(preferences.edit().putString("snapshot_$uid", actuales.toString()).commit())
    }

    private fun mostrar(turnoId: String, tipo: String) {
        crearCanalTurnos(context)
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        intent.putExtra("abrir_turnos_fcm", true)
        val id = turnoId.hashCode()
        val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val texto = when (tipo) {
            "alta" -> R.string.notifications_alta
            "baja" -> R.string.notifications_baja
            else -> R.string.notifications_modificacion
        }
        NotificationManagerCompat.from(context).notify("turnos_locales", id,
            NotificationCompat.Builder(context, "cambios_turnos").setSmallIcon(R.drawable.ic_turno_notification)
                .setContentTitle(context.getString(R.string.notifications_title)).setContentText(context.getString(texto))
                .setContentIntent(pending).setAutoCancel(true).build())
    }
}
