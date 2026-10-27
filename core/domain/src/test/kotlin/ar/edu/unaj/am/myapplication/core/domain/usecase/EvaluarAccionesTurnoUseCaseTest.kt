package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class EvaluarAccionesTurnoUseCaseTest {
    private val ahora = 200_000_000L
    private val politica = PoliticaReservas(Clock.fixed(Instant.ofEpochMilli(ahora),ZoneOffset.UTC))
    private val evaluar = EvaluarAccionesTurnoUseCase(politica)
    private fun turno(estado: String = "pendiente", inicio: Long? = ahora+7_200_000L) =
        Turno("t1","cliente","barbero","corte",inicio,null,estado)

    @Test fun `un pendiente futuro permite editar y recordar pero no completar`() {
        for (estado in listOf("pendiente","Confirmado","true")) {
            val acciones = evaluar(turno(estado),ahora)
            assertTrue(acciones.editable)
            assertFalse(acciones.completable)
            assertTrue(acciones.admiteFinalizacion)
            assertTrue(acciones.admiteRecordatorios)
            assertTrue(acciones.recordatorioHabilitado)
            assertTrue(acciones.permiteConsultarRuta)
        }
    }
    @Test fun `al comenzar cambia de editable a completable usando el mismo criterio que la validacion`() {
        val t = turno(inicio=ahora)
        val acciones = evaluar(t,ahora)
        assertFalse(acciones.editable)
        assertTrue(acciones.completable)
        assertFalse(acciones.recordatorioHabilitado)
        politica.validarRealizable(t.estado,t.inicioMillis)
        try { politica.validarEditable(t.estado,t.inicioMillis!!); fail("No se edita un turno iniciado") }
        catch (e: DomainValidationException) { assertEquals(ErrorReason.BOOKING_NOT_EDITABLE,e.error.reason) }
    }
    @Test fun `cancelados y completados historicos no ofrecen acciones de reserva`() {
        for (estado in listOf("cancelado","Cancelada","false","realizado","Completado")) {
            val acciones = evaluar(turno(estado,inicio=ahora-1),ahora)
            assertEquals(AccionesTurno(),acciones)
        }
    }
    @Test fun `sin fecha o identificador no habilita edicion ni finalizacion`() {
        val sinFecha = evaluar(turno(inicio=null),ahora)
        assertFalse(sinFecha.editable)
        assertFalse(sinFecha.completable)
        assertFalse(sinFecha.recordatorioHabilitado)
        assertFalse(evaluar(turno(inicio=ahora-1).copy(id=""),ahora).completable)
        assertFalse(evaluar(turno().copy(id=""),ahora).editable)
    }
    @Test fun `una hora exacta no admite recordatorio vencido y la configuracion vive con el modelo`() {
        assertFalse(evaluar(turno(inicio=ahora+3_600_000L),ahora).recordatorioHabilitado)
        assertTrue(evaluar(turno(inicio=ahora+3_600_001L),ahora).recordatorioHabilitado)
        assertEquals(listOf(86_400_000L,3_600_000L),RecordatorioTurnoLocal("t1","cliente",ahora).pending)
    }
}
