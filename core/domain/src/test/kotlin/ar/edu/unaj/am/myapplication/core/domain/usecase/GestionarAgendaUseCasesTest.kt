package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.ReservaRepository
import java.time.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class GestionarAgendaUseCasesTest {
    private val valid = AgendaSemanal("America/Argentina/Buenos_Aires", List(6) {
        DiaAtencion(true, listOf(FranjaAtencion("09:00", "13:00"), FranjaAtencion("16:00", "20:00")))
    })
    private val validate = ValidarAgendaUseCase()
    @Test fun `breaks and closed days are allowed`() {
        assertNull(validate(valid))
        assertNull(validate(valid.copy(dias = List(6) { DiaAtencion(false, emptyList()) })))
    }
    @Test fun `open day without spans and invalid clock times are rejected`() {
        for (spans in listOf(emptyList(), listOf(FranjaAtencion("25:00", "26:00")),
            listOf(FranjaAtencion("9:00", "10:00")))) {
            assertNotNull(validate(valid.copy(dias = valid.dias.toMutableList().also { it[0] = DiaAtencion(true, spans) })))
        }
    }
    @Test fun `overlaps reversed spans and too short spans are rejected`() {
        for (spans in listOf(listOf(FranjaAtencion("13:00", "09:00")),
            listOf(FranjaAtencion("09:00", "09:30")),
            listOf(FranjaAtencion("09:00", "11:00"),FranjaAtencion("10:00", "12:00")))) {
            assertNotNull(validate(valid.copy(dias = List(6) { DiaAtencion(true, spans) })))
        }
    }
    @Test fun `invalid agenda never reaches persistence`() = runTest {
        val repo = Fake(valid)
        val result = save(repo)(valid.copy(dias = emptyList()))
        assertTrue(result is AppResult.Error)
        assertEquals(0, repo.writes)
        assertEquals(0, repo.publications)
    }
    @Test fun `saved agenda is published excluding existing bookings`() = runTest {
        val repo = Fake(valid)
        val result = save(repo)(valid) as AppResult.Success
        assertNull(result.data.errorPublicacion)
        assertTrue(result.data.horariosPublicados!! > 0)
        assertEquals(1, repo.writes)
        assertEquals(1, repo.publications)
    }
    @Test fun `publication failure distinguishes saved agenda from unsaved failure`() = runTest {
        val repo = Fake(valid)
        repo.publishResult = AppResult.Error(DomainError.Network)
        val result = save(repo)(valid) as AppResult.Success
        assertEquals(DomainError.Network, result.data.errorPublicacion)
        assertNull(result.data.horariosPublicados)
        repo.saveResult = AppResult.Error(DomainError.Network)
        assertTrue(save(repo)(valid) is AppResult.Error)
        assertEquals(1, repo.publications)
    }
    private fun save(repo: Fake) = GuardarYPublicarAgendaUseCase(repo, PublicarSemanaUseCase(repo,
        clock = Clock.fixed(Instant.parse("2026-10-12T11:00:00Z"), ZoneOffset.UTC)))
    private class Fake(var agenda: AgendaSemanal) : ReservaRepository {
        var writes = 0
        var publications = 0
        var saveResult: AppResult<Unit> = AppResult.Success(Unit)
        var publishResult: AppResult<Int>? = null
        override suspend fun obtenerMiAgenda(semanaOffset: Int) = AppResult.Success(agenda)
        override suspend fun guardarMiAgenda(agenda: AgendaSemanal): AppResult<Unit> {
            writes++
            if (saveResult is AppResult.Success) this.agenda = agenda
            return saveResult
        }
        override suspend fun obtenerMisTurnos() = AppResult.Success(emptyList<Turno>())
        override suspend fun publicarSemana(agenda: AgendaSemanal, semana: SemanaDisponible): AppResult<Int> {
            publications++
            return publishResult ?: AppResult.Success(semana.horarios.size)
        }
        override suspend fun cargarCatalogo(): AppResult<CatalogoReservas> = error("No usado")
        override suspend fun consultarSemana(barberoId: String, servicioId: String, semanaOffset: Int): AppResult<SemanaDisponible> = error("No usado")
        override suspend fun reservar(solicitud: SolicitudReserva): AppResult<String> = error("No usado")
    }
}
