package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.TurnoRepository
import java.time.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ResumenBarberoUseCasesTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-11T12:00:00Z"), ZoneId.of("America/Argentina/Buenos_Aires"))
    private fun turno(id: String, fecha: String = "2026-10-08T12:00:00Z", estado: String = "realizado",
        cobrado: Long? = 125050) = Turno(id, "cliente", "barbero", "corte",
        Instant.parse(fecha).toEpochMilli(), Instant.parse(fecha).plusSeconds(2400).toEpochMilli(), estado,
        precioServicioCentavos = 999900, precioCobradoCentavos = cobrado)

    @Test fun `suma precios historicos solo de realizados y cuenta importes faltantes`() {
        val result = CalcularResumenBarberoUseCase(clock)(listOf(
            turno("hecho"), turno("sin-precio", cobrado = null),
            turno("pendiente", estado = "Confirmado"), turno("cancelado", estado = "Cancelado")))
        assertEquals(125050L, result.gananciasCentavos)
        assertEquals(2, result.realizados.size)
        assertEquals(listOf("pendiente"), result.pendientes.map { it.id })
        assertEquals(1, result.realizadosSinPrecio)
    }

    @Test fun `respeta lunes local inclusivo y siguiente lunes exclusivo`() {
        val calcular = CalcularResumenBarberoUseCase(clock)
        val datos = listOf(turno("antes", "2026-10-05T02:59:59Z"),
            turno("lunes", "2026-10-05T03:00:00Z"), turno("domingo", "2026-10-12T02:59:59Z"),
            turno("siguiente", "2026-10-12T03:00:00Z"))
        assertEquals(listOf("lunes", "domingo"), calcular(datos).realizados.map { it.id })
        assertEquals(listOf("siguiente"), calcular(datos, 1).realizados.map { it.id })
        assertEquals(listOf("antes"), calcular(datos, -1).realizados.map { it.id })
    }

    @Test fun `no escribe turnos futuros realizados ni sin precio y conserva precio confirmado`() = runBlocking {
        val llamadas = mutableListOf<Pair<String, Long>>()
        val repository = object : TurnoRepository {
            override fun observeTurnos(comoCliente: Boolean) = flowOf(AppResult.Success(emptyList<Turno>()))
            override suspend fun marcarRealizado(turnoId: String, precioEsperadoCentavos: Long): AppResult<Unit> {
                llamadas += turnoId to precioEsperadoCentavos
                return AppResult.Success(Unit)
            }
        }
        val completar = MarcarTurnoRealizadoUseCase(repository, clock)
        assertTrue(completar(turno("ya-hecho")) is AppResult.Error)
        assertTrue(completar(turno("futuro", "2026-10-12T12:00:00Z", "confirmado")) is AppResult.Error)
        assertTrue(completar(turno("sin-precio", estado = "confirmado").copy(precioServicioCentavos = null)) is AppResult.Error)
        assertTrue(llamadas.isEmpty())
        assertEquals(AppResult.Success(Unit), completar(turno("valido", estado = "confirmado")))
        assertEquals(listOf("valido" to 999900L), llamadas)
    }
}
