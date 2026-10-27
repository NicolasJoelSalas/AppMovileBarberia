package ar.edu.unaj.am.myapplication.core.data.notifications

import android.content.Context
import android.content.SharedPreferences
import ar.edu.unaj.am.myapplication.core.domain.model.RecordatorioTurnoLocal
import ar.edu.unaj.am.myapplication.core.domain.repository.RecordatoriosRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class PreferenciasRecordatorios @Inject constructor(@ApplicationContext context: Context) : RecordatoriosRepository {
    private val prefs = context.getSharedPreferences("turno_reminders", Context.MODE_PRIVATE)
    override fun listar(): List<RecordatorioTurnoLocal> = prefs.all.mapNotNull { (_, value) ->
        runCatching {
            val json = JSONObject(value as String)
            val pending = json.optJSONArray("pending")
            RecordatorioTurnoLocal(json.getString("id"), json.getString("owner"), json.getLong("inicio"),
                pending?.let { array -> (0 until array.length()).map { array.getLong(it) } } ?: listOf(3_600_000L),
                json.optBoolean("disabled", false), json.optInt("version", 1))
        }.getOrNull()
    }
    override fun guardar(recordatorio: RecordatorioTurnoLocal): Boolean = with(recordatorio) {
        prefs.edit().putString(id, JSONObject().put("id",id).put("owner",owner).put("inicio",inicio)
            .put("pending",JSONArray(pending)).put("disabled",disabled).put("version",2).toString()).commit()
    }
    override fun eliminar(id: String) { prefs.edit().remove(id).commit() }
    override fun permisoSolicitado(uid: String?) = prefs.getBoolean("permissions_asked_$uid",false)
    override fun registrarSolicitudPermiso(uid: String?) { prefs.edit().putBoolean("permissions_asked_$uid",true).apply() }
    override fun observarCambios() = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(Unit)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}
