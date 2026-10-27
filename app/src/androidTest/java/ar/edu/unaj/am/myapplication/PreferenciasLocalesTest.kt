package ar.edu.unaj.am.myapplication

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import ar.edu.unaj.am.myapplication.core.data.notifications.*
import ar.edu.unaj.am.myapplication.core.data.repository.PreferenciasApariencia
import ar.edu.unaj.am.myapplication.core.domain.model.RecordatorioTurnoLocal
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

/** Usa archivos aislados y comprueba el formato persistido antes de la separación de capas. */
class PreferenciasLocalesTest {
    private fun aislado(prueba: (Context) -> Unit) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val prefijo = "prueba_capas_${UUID.randomUUID()}_"
        val nombres = mutableSetOf<String>()
        val context = object : ContextWrapper(app) {
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
                nombres += name
                return app.getSharedPreferences(prefijo+name,mode)
            }
        }
        try { prueba(context) } finally { nombres.forEach { app.deleteSharedPreferences(prefijo+it) } }
    }
    @Test fun recordatoriosConservanPendientesYDesactivacionAlReabrir() = aislado { context ->
        val repo = PreferenciasRecordatorios(context)
        val activo = RecordatorioTurnoLocal("t1","cliente",2_000_000,listOf(3_600_000L))
        assertTrue(repo.guardar(activo))
        assertEquals(listOf(activo),PreferenciasRecordatorios(context).listar())
        val desactivado = activo.copy(pending=emptyList(),disabled=true)
        assertTrue(repo.guardar(desactivado))
        assertEquals(listOf(desactivado),PreferenciasRecordatorios(context).listar())
        repo.eliminar("t1")
        assertTrue(PreferenciasRecordatorios(context).listar().isEmpty())
    }
    @Test fun recordatoriosAntiguosYPermisosPorCuentaSiguenSiendoCompatibles() = aislado { context ->
        context.getSharedPreferences("turno_reminders",Context.MODE_PRIVATE).edit()
            .putString("t1", """{"id":"t1","owner":"cliente","inicio":2000000}""").commit()
        val repo = PreferenciasRecordatorios(context)
        repo.registrarSolicitudPermiso("cliente")
        assertTrue(PreferenciasRecordatorios(context).permisoSolicitado("cliente"))
        assertFalse(repo.permisoSolicitado("otro"))
        assertEquals(listOf(RecordatorioTurnoLocal("t1","cliente",2_000_000,listOf(3_600_000L),version=1)),repo.listar())
    }
    @Test fun avisosConservanFirmasNulasYNoMezclanCuentas() = aislado { context ->
        val repo = PreferenciasAvisosTurnos(context)
        repo.iniciar("cliente",true)
        val firmas = mapOf("t1" to listOf("cliente","barbero",null,"100",null,"pendiente",null))
        repo.guardarFirmas("cliente",firmas)
        val reabierto = PreferenciasAvisosTurnos(context)
        assertEquals("cliente",reabierto.usuarioId)
        assertTrue(reabierto.comoCliente)
        assertEquals(firmas,reabierto.obtenerFirmas("cliente"))
        assertNull(reabierto.obtenerFirmas("otro"))
        reabierto.cerrar()
        assertNull(PreferenciasAvisosTurnos(context).usuarioId)
    }
    @Test fun aparienciaSigueElSistemaHastaGuardarUnaPreferencia() = aislado { context ->
        val repo = PreferenciasApariencia(context)
        assertNull(repo.temaOscuro)
        repo.temaOscuro=true
        assertEquals(true,PreferenciasApariencia(context).temaOscuro)
        repo.temaOscuro=false
        assertEquals(false,PreferenciasApariencia(context).temaOscuro)
        repo.temaOscuro=null
        assertNull(PreferenciasApariencia(context).temaOscuro)
    }
}
