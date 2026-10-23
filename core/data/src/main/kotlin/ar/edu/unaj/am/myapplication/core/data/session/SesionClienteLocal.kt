package ar.edu.unaj.am.myapplication.core.data.session

import android.content.Context

/** Perfil de cliente previamente confirmado. Nunca concede permisos de escritura remotos. */
class SesionClienteLocal(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("sesion_cliente_local", Context.MODE_PRIVATE)

    fun estaValidado(uid: String): Boolean = preferences.getBoolean("validado_$uid", false)

    fun guardar(uid: String, nombre: String) {
        preferences.edit().putBoolean("validado_$uid", true)
            .putString("nombre_$uid", nombre).apply()
    }

    fun nombre(uid: String): String = preferences.getString("nombre_$uid", "").orEmpty()

    fun limpiar(uid: String) {
        preferences.edit().remove("validado_$uid").remove("nombre_$uid").apply()
    }
}
