package ar.edu.unaj.am.myapplication.core.database

import androidx.room3.Database
import androidx.room3.RoomDatabase
import ar.edu.unaj.am.myapplication.core.database.dao.TurnoDao
import ar.edu.unaj.am.myapplication.core.database.model.TurnosEntity

@Database(
    entities = [TurnosEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun turnoDao(): TurnoDao

    companion object {
        @Volatile
        private var instancia: AppDatabase? = null

        /** El módulo de entrada aporta el builder de su plataforma (Android o JVM). */
        fun obtener(crear: () -> AppDatabase): AppDatabase {
            return instancia ?: synchronized(this) {
                instancia ?: crear().also { instancia = it }
            }
        }
    }
}
