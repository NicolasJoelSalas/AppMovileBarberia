package ar.edu.unaj.am.myapplication.core.domain.repository

import ar.edu.unaj.am.myapplication.core.domain.model.*

interface ReservaRepository {
    suspend fun obtenerMiAgenda(semanaOffset: Int = 0): AppResult<AgendaSemanal>
    suspend fun guardarMiAgenda(agenda: AgendaSemanal): AppResult<Unit>
    suspend fun obtenerMisTurnos(): AppResult<List<Turno>>
    suspend fun publicarSemana(agenda: AgendaSemanal, semana: SemanaDisponible): AppResult<Int>
    suspend fun cargarCatalogo(): AppResult<CatalogoReservas>
    suspend fun consultarSemana(barberoId: String, servicioId: String, semanaOffset: Int = 0): AppResult<SemanaDisponible>
    suspend fun cancelar(turnoId: String): AppResult<Unit> = AppResult.Error(DomainError.Unknown("Operación no disponible."))
    suspend fun modificar(turnoId: String, solicitud: SolicitudReserva): AppResult<String> = AppResult.Error(DomainError.Unknown("Operación no disponible."))
    suspend fun reservar(solicitud: SolicitudReserva): AppResult<String>
}
