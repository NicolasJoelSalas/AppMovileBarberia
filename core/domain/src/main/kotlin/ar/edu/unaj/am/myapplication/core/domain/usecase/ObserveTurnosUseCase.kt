package ar.edu.unaj.am.myapplication.core.domain.usecase

import javax.inject.Inject

import ar.edu.unaj.am.myapplication.core.domain.repository.TurnoRepository

class ObserveTurnosUseCase @Inject constructor(private val repository: TurnoRepository) {
    operator fun invoke(comoCliente: Boolean = false) = repository.observeTurnos(comoCliente)
}
