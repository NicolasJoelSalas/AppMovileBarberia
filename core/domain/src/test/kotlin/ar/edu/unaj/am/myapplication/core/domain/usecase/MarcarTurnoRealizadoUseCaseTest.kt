package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.TurnoRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class MarcarTurnoRealizadoUseCaseTest {
    @Test fun `solo permite pendientes desde el inicio inclusive`() = runTest {
        val now = Instant.parse("2026-10-16T13:00:00Z")
        val calls = mutableListOf<String>()
        val repo = object : TurnoRepository {
            override fun observeTurnos(comoCliente: Boolean) = flowOf(AppResult.Success(emptyList<Turno>()))
            override suspend fun marcarRealizado(turnoId: String): AppResult<Unit> {
                calls.add(turnoId)
                return AppResult.Success(Unit)
            }
        }
        val completar = MarcarTurnoRealizadoUseCase(repo, Clock.fixed(now, ZoneOffset.UTC))
        val turno = Turno("turno", "cliente", "barbero", "corte", now.toEpochMilli(),
            now.toEpochMilli() + 2400000, "confirmado")
        assertTrue(completar(turno.copy(inicioMillis = now.toEpochMilli() + 1)) is AppResult.Error)
        assertTrue(completar(turno.copy(inicioMillis = null)) is AppResult.Error)
        assertTrue(completar(turno.copy(estado = "cancelado")) is AppResult.Error)
        assertTrue(completar(turno.copy(estado = "realizado")) is AppResult.Error)
        assertTrue(calls.isEmpty())
        assertTrue(completar(turno) is AppResult.Success)
        assertTrue(completar(turno.copy(inicioMillis = now.toEpochMilli() - 3600000)) is AppResult.Success)
        assertEquals(listOf("turno", "turno"), calls)
    }
}
