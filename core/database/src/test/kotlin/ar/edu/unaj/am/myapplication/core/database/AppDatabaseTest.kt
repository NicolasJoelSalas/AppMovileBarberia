package ar.edu.unaj.am.myapplication.core.database

import ar.edu.unaj.am.myapplication.core.database.mapper.toDomain
import ar.edu.unaj.am.myapplication.core.database.mapper.toEntity
import ar.edu.unaj.am.myapplication.core.database.model.TurnosEntity
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class AppDatabaseTest {
    @Test fun `persists appointments on reopen and isolates clients`() = runTest {
        val folder = Files.createTempDirectory("turnos-room").toFile()
        val file = folder.resolve("test.db")
        fun open() = Room.databaseBuilder<AppDatabase>(file.absolutePath)
            .setDriver(BundledSQLiteDriver()).build()
        val turno = TurnosEntity("t1", "cliente", "barbero", "corte", 1000L, 2401000L,
            "confirmado", "Ana", "Corte", 120000L)
        try {
            val initial = open()
            try {
                val db = initial
                db.turnoDao().guardar(turno)
                db.turnoDao().guardar(turno.copy(id = "ajeno", clienteId = "otro"))
            } finally { initial.close() }
            val reopened = open()
            try {
                val db = reopened
                assertEquals(listOf(turno), db.turnoDao().observarPorCliente("cliente").first())
                assertNull(db.turnoDao().obtenerPorId("t1", "otro"))
                assertEquals(turno, turno.toDomain().toEntity())
                db.turnoDao().reemplazarPorCliente("cliente", listOf(turno.copy(estado = "cancelado")))
                assertEquals("cancelado", db.turnoDao().obtenerPorId("t1", "cliente")!!.estado)
                db.turnoDao().reemplazarPorCliente("cliente", emptyList())
                assertTrue(db.turnoDao().observarPorCliente("cliente").first().isEmpty())
                assertEquals(1, db.turnoDao().observarPorCliente("otro").first().size)
            } finally { reopened.close() }
        } finally {
            folder.listFiles()?.forEach { it.delete() }
            folder.delete()
        }
    }
}
