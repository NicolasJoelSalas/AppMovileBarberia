package ar.edu.unaj.am.myapplication.ui

import androidx.lifecycle.ViewModel
import ar.edu.unaj.am.myapplication.core.domain.repository.SesionActual
import ar.edu.unaj.am.myapplication.core.domain.usecase.ObserveTurnosUseCase
import ar.edu.unaj.am.myapplication.notifications.AvisosTurnos
import ar.edu.unaj.am.myapplication.core.domain.usecase.RegistroNotificaciones
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AppShellViewModel @Inject constructor(
    val observeTurnos: ObserveTurnosUseCase,
    val notificaciones: RegistroNotificaciones,
    val avisosTurnos: AvisosTurnos,
    val solicitudPermiso: ar.edu.unaj.am.myapplication.core.domain.usecase.SolicitudPermisoNotificaciones,
    private val auth: SesionActual
) : ViewModel() {
    fun clienteActualId(): String? = auth.usuarioId()
}
