package ar.edu.unaj.am.myapplication.core.data.repository

import android.content.Context
import ar.edu.unaj.am.myapplication.core.domain.repository.AparienciaRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferenciasApariencia @Inject constructor(@ApplicationContext context: Context) : AparienciaRepository {
    private val prefs = context.getSharedPreferences("appearance",Context.MODE_PRIVATE)
    override var temaOscuro: Boolean?
        get() = if (prefs.contains("dark_theme")) prefs.getBoolean("dark_theme",false) else null
        set(value) {
            val editor = prefs.edit()
            if (value == null) editor.remove("dark_theme") else editor.putBoolean("dark_theme",value)
            editor.apply()
        }
}
