package ar.edu.unaj.am.myapplication.core.data.session

import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

/** Perfil de cliente previamente confirmado. Nunca concede permisos de escritura remotos. */
@Singleton
class SesionClienteLocal @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("sesion_cliente_local", Context.MODE_PRIVATE)
    private val cambios = MutableStateFlow(0L)

    fun observarValidacion(uid: String) = cambios.map { estaValidado(uid) }.distinctUntilChanged()

    fun estaValidado(uid: String): Boolean = preferences.getBoolean("validado_$uid", false)

    fun guardar(uid: String, nombre: String) {
        preferences.edit().putBoolean("validado_$uid", true)
            .putString("nombre_$uid", nombre).apply()
        cambios.value++
    }

    fun nombre(uid: String): String = preferences.getString("nombre_$uid", "").orEmpty()

    fun limpiar(uid: String) {
        preferences.edit().remove("validado_$uid").remove("nombre_$uid").apply()
        cambios.value++
    }
}
