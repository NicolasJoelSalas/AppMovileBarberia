package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.repository.TurnoRepository

class ObserveTurnosUseCase(private val repository: TurnoRepository) {
    operator fun invoke(comoCliente: Boolean = false) = repository.observeTurnos(comoCliente)
}
