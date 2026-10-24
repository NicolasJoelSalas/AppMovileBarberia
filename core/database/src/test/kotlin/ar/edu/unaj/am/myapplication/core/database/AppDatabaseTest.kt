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
    @Test fun `barber appointments persist across reopening and replacement preserves other barbers`() = runTest {
        val folder = Files.createTempDirectory("turnos-barbero-room").toFile()
        val file = folder.resolve("test.db")
        fun open() = Room.databaseBuilder<AppDatabase>(file.absolutePath).setDriver(BundledSQLiteDriver()).build()
        val propio = TurnosEntity("propio", "cliente", "barbero", "corte", 1000L, 2401000L,
            "confirmado", "Ana", "Corte", 120000L)
        val ajeno = propio.copy(id = "ajeno", barberoId = "otro-barbero")
        try {
            val first = open()
            try { first.turnoDao().guardarTodos(listOf(propio, ajeno)) } finally { first.close() }
            val reopened = open()
            try {
                val dao = reopened.turnoDao()
                assertEquals(listOf(propio), dao.observarPorBarbero("barbero").first())
                assertEquals("Ana", dao.observarPorBarbero("barbero").first().single().clienteNombre)
                dao.reemplazarPorBarbero("barbero", listOf(propio.copy(estado = "realizado")))
                dao.actualizarNombres(listOf(propio.copy(clienteNombre = "Nombre actualizado", estado = "confirmado")))
                assertEquals("realizado", dao.observarPorBarbero("barbero").first().single().estado)
                assertEquals("Nombre actualizado", dao.observarPorBarbero("barbero").first().single().clienteNombre)
                try {
                    dao.reemplazarPorBarbero("barbero", listOf(ajeno))
                    fail("No debe aceptar turnos ajenos")
                } catch (_: IllegalArgumentException) { }
                dao.reemplazarPorBarbero("barbero", emptyList())
                assertTrue(dao.observarPorBarbero("barbero").first().isEmpty())
                assertEquals(listOf(ajeno), dao.observarPorBarbero("otro-barbero").first())
            } finally { reopened.close() }
        } finally {
            folder.listFiles()?.forEach { it.delete() }
            folder.delete()
        }
    }
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
