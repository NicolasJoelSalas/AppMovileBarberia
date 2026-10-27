package ar.edu.unaj.am.myapplication.core.domain.usecase

import javax.inject.Inject

import ar.edu.unaj.am.myapplication.core.domain.repository.NovedadesTurnosRepository

class ConsultarNovedadesTurnosUseCase @Inject constructor(private val repository: NovedadesTurnosRepository) {
    suspend operator fun invoke(uid: String, comoCliente: Boolean = false) = repository.consultar(uid, comoCliente)
}

class ObservarNovedadesTurnosUseCase @Inject constructor(private val repository: NovedadesTurnosRepository) {
    operator fun invoke(uid: String, comoCliente: Boolean = false) = repository.observar(uid, comoCliente)
}
