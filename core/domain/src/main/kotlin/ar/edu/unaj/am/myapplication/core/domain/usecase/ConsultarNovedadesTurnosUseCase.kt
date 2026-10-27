package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.repository.NovedadesTurnosRepository

class ConsultarNovedadesTurnosUseCase(private val repository: NovedadesTurnosRepository) {
    suspend operator fun invoke(uid: String) = repository.consultar(uid)
}

class ObservarNovedadesTurnosUseCase(private val repository: NovedadesTurnosRepository) {
    operator fun invoke(uid: String) = repository.observar(uid)
}
