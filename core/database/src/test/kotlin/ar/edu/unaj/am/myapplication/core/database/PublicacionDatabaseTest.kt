package ar.edu.unaj.am.myapplication.core.database

import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import ar.edu.unaj.am.myapplication.core.database.dao.TurnoDao
import ar.edu.unaj.am.myapplication.core.database.migration.MIGRATION_1_2
import ar.edu.unaj.am.myapplication.core.database.model.PublicacionEntity
import ar.edu.unaj.am.myapplication.core.database.model.TurnosEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

@androidx.room3.Entity(tableName = "turnos")
data class TurnoVersion1(
    @androidx.room3.PrimaryKey val id: String, val clienteId: String, val barberoId: String,
    val servicioId: String, val inicioMillis: Long?, val finMillis: Long?, val estado: String,
    val clienteNombre: String = "", val servicioNombre: String = "", val precioCobradoCentavos: Long? = null
)

@androidx.room3.Dao
interface TurnoVersion1Dao {
    @androidx.room3.Insert suspend fun guardar(turno: TurnoVersion1)
}

@Database(entities = [TurnoVersion1::class], version = 1, exportSchema = false)
abstract class DatabaseVersion1 : RoomDatabase() {
    abstract fun turnoDao(): TurnoVersion1Dao
}

class PublicacionDatabaseTest {
    @Test fun `migration preserves appointments and publications persist separately per client`() = runTest {
        val directory = Files.createTempDirectory("publicaciones-room").toFile()
        val file = directory.resolve("migration.db")
        val turno = TurnosEntity("t1", "ana", "barbero", "corte", 1000L, 2401000L, "confirmado")
        try {
            val old = Room.databaseBuilder<DatabaseVersion1>(file.absolutePath).setDriver(BundledSQLiteDriver()).build()
            try { old.turnoDao().guardar(TurnoVersion1("t1", "ana", "barbero", "corte", 1000L, 2401000L, "confirmado")) } finally { old.close() }
            fun open() = Room.databaseBuilder<AppDatabase>(file.absolutePath).setDriver(BundledSQLiteDriver())
                .addMigrations(MIGRATION_1_2, ar.edu.unaj.am.myapplication.core.database.migration.MIGRATION_2_3).build()
            val photo = PublicacionEntity("ana", "p1", "barbero", "Luis", "https://foto", "Corte", 1000L, "/local/foto.jpg")
            val upgraded = open()
            try {
                assertEquals(turno, upgraded.turnoDao().obtenerPorId("t1", "ana"))
                upgraded.turnoDao().guardar(turno.copy(referenciaCorteUrl = "https://res.cloudinary.com/test/image/upload/corte.jpg"))
                upgraded.publicacionDao().guardarTodos(listOf(photo, photo.copy(clienteId = "otro")))
            } finally { upgraded.close() }
            val reopened = open()
            try {
                val dao = reopened.publicacionDao()
                assertEquals("https://res.cloudinary.com/test/image/upload/corte.jpg",
                    reopened.turnoDao().obtenerPorId("t1", "ana")?.referenciaCorteUrl)
                assertEquals(listOf(photo), dao.observarPorCliente("ana").first())
                dao.actualizarImagen("ana", "p1", "url-antigua", "/incorrecta")
                assertEquals(photo, dao.obtenerPorCliente("ana").single())
                dao.reemplazarPorCliente("ana", emptyList())
                assertTrue(dao.obtenerPorCliente("ana").isEmpty())
                assertEquals(1, dao.obtenerPorCliente("otro").size)
            } finally { reopened.close() }
        } finally {
            directory.listFiles()?.forEach { it.delete() }
            directory.delete()
        }
    }
}
