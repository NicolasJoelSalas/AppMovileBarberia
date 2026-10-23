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

@Database(entities = [TurnosEntity::class], version = 1, exportSchema = false)
abstract class DatabaseVersion1 : RoomDatabase() {
    abstract fun turnoDao(): TurnoDao
}

class PublicacionDatabaseTest {
    @Test fun `migration preserves appointments and publications persist separately per client`() = runTest {
        val directory = Files.createTempDirectory("publicaciones-room").toFile()
        val file = directory.resolve("migration.db")
        val turno = TurnosEntity("t1", "ana", "barbero", "corte", 1000L, 2401000L, "confirmado")
        try {
            val old = Room.databaseBuilder<DatabaseVersion1>(file.absolutePath).setDriver(BundledSQLiteDriver()).build()
            try { old.turnoDao().guardar(turno) } finally { old.close() }
            fun open() = Room.databaseBuilder<AppDatabase>(file.absolutePath).setDriver(BundledSQLiteDriver())
                .addMigrations(MIGRATION_1_2).build()
            val photo = PublicacionEntity("ana", "p1", "barbero", "Luis", "https://foto", "Corte", 1000L, "/local/foto.jpg")
            val upgraded = open()
            try {
                assertEquals(turno, upgraded.turnoDao().obtenerPorId("t1", "ana"))
                upgraded.publicacionDao().guardarTodos(listOf(photo, photo.copy(clienteId = "otro")))
            } finally { upgraded.close() }
            val reopened = open()
            try {
                val dao = reopened.publicacionDao()
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
