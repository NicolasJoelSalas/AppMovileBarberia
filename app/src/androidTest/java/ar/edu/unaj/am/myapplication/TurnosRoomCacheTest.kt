package ar.edu.unaj.am.myapplication

import androidx.test.platform.app.InstrumentationRegistry
import ar.edu.unaj.am.myapplication.core.database.TurnosDatabase
import ar.edu.unaj.am.myapplication.core.domain.model.Turno
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TurnosRoomCacheTest {
    @Test fun persistsAfterClosingDatabaseAndKeepsScopesIsolated() = runBlocking {
        // El almacenamiento del APK de pruebas está separado del de la aplicación.
        val context = InstrumentationRegistry.getInstrumentation().context
        context.deleteDatabase("turnos.db")
        var database = TurnosDatabase.create(context)
        try {
            val turno = Turno("t1", "cliente", "barbero", "servicio", 123L, 456L,
                "confirmado", "Ana", "Corte", 250000L)
            database.turnosDao().replace("usuario:cliente", listOf(turno))
            database.close()
            database = TurnosDatabase.create(context)
            val cache = database.turnosDao().observe("usuario:cliente").first()
            assertEquals(listOf(turno), cache!!.turnos.map { it.turno })
            assertTrue(cache.sync.actualizadoEn > 0)
            assertNull(database.turnosDao().observe("otro:cliente").first())
            assertNull(database.turnosDao().observe("usuario:barbero").first())
            database.turnosDao().replace("usuario:cliente", emptyList())
            database.close()
            database = TurnosDatabase.create(context)
            val empty = database.turnosDao().observe("usuario:cliente").first()
            assertNotNull(empty)
            assertTrue(empty!!.turnos.isEmpty())
        } finally {
            database.close()
            context.deleteDatabase("turnos.db")
        }
    }
}
