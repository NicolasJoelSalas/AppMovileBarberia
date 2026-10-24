package ar.edu.unaj.am.myapplication.core.data.session

import android.content.Context

/** Recuerda el rol confirmado para abrir los turnos guardados sin consultar la red. */
class SesionBarberoLocal(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("sesion_barbero_local", Context.MODE_PRIVATE)

    fun estaValidado(uid: String): Boolean = preferences.getBoolean("validado_$uid", false)

    fun guardar(uid: String) {
        preferences.edit().putBoolean("validado_$uid", true).apply()
    }

    fun limpiar(uid: String) {
        preferences.edit().remove("validado_$uid").apply()
    }
}
