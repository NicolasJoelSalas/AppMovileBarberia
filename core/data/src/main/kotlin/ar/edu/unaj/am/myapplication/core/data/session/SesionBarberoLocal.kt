package ar.edu.unaj.am.myapplication.core.data.session

import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

import android.content.Context

/** Recuerda el rol confirmado para abrir los turnos guardados sin consultar la red. */
@Singleton
class SesionBarberoLocal @Inject constructor(@ApplicationContext context: Context) {
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
