package ar.edu.unaj.am.myapplication.core.data.notifications

import android.content.Context
import ar.edu.unaj.am.myapplication.core.domain.repository.EstadoAvisosRepository
import ar.edu.unaj.am.myapplication.core.domain.repository.PermisoNotificacionesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class PreferenciasPermisoNotificaciones @Inject constructor(@ApplicationContext context: Context) : PermisoNotificacionesRepository {
    private val preferences = context.getSharedPreferences("fcm_permission", Context.MODE_PRIVATE)
    override val solicitado get() = preferences.getBoolean("solicitado", false)
    override fun registrarSolicitud() { preferences.edit().putBoolean("solicitado", true).apply() }
}

@Singleton
class PreferenciasAvisosTurnos @Inject constructor(@ApplicationContext context: Context) : EstadoAvisosRepository {
    private val preferences = context.getSharedPreferences("avisos_turnos", Context.MODE_PRIVATE)
    override val usuarioId get() = preferences.getString("uid", null)
    override val comoCliente get() = preferences.getBoolean("comoCliente", false)
    override fun iniciar(uid: String, comoCliente: Boolean) {
        preferences.edit().putString("uid", uid).putBoolean("comoCliente", comoCliente).apply()
    }
    override fun cerrar() { preferences.edit().remove("uid").apply() }
    override fun obtenerFirmas(uid: String): Map<String, List<String?>>? = preferences.getString("snapshot_$uid", null)?.let {
        val json = JSONObject(it)
        json.keys().asSequence().associateWith { id ->
            val valores = json.getJSONArray(id)
            (0 until valores.length()).map { index -> if (valores.isNull(index)) null else valores.getString(index) }
        }
    }
    override fun guardarFirmas(uid: String, firmas: Map<String, List<String?>>) {
        val json = JSONObject()
        firmas.forEach { (id, valores) -> json.put(id, JSONArray(valores)) }
        check(preferences.edit().putString("snapshot_$uid", json.toString()).commit())
    }
}
