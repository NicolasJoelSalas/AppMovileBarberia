package ar.edu.unaj.am.myapplication.core.database

import androidx.room3.Database
import androidx.room3.RoomDatabase
import ar.edu.unaj.am.myapplication.core.database.dao.TurnoDao
import ar.edu.unaj.am.myapplication.core.database.model.TurnosEntity
import ar.edu.unaj.am.myapplication.core.database.model.PublicacionEntity
import ar.edu.unaj.am.myapplication.core.database.dao.PublicacionDao

@Database(
    entities = [TurnosEntity::class, PublicacionEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun turnoDao(): TurnoDao
    abstract fun publicacionDao(): PublicacionDao

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
