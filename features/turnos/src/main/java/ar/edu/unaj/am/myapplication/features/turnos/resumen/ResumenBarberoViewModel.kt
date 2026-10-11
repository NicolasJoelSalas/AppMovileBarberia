package ar.edu.unaj.am.myapplication.features.turnos.resumen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.usecase.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ResumenBarberoUiState(
    val cargando: Boolean = true,
    val resumen: ResumenBarbero? = null,
    val semanaOffset: Int = 0,
    val completandoId: String? = null,
    val mensaje: String? = null,
    val error: DomainError? = null
)

class ResumenBarberoViewModel(
    private val observar: ObserveTurnosUseCase,
    private val calcular: CalcularResumenBarberoUseCase,
    private val completar: MarcarTurnoRealizadoUseCase
) : ViewModel() {
    private val estado = MutableStateFlow(ResumenBarberoUiState())
    val uiState = estado.asStateFlow()
    private var turnos = emptyList<Turno>()
    private var observacion: Job? = null

    init { reintentar() }

    fun reintentar() {
        observacion?.cancel()
        estado.value = estado.value.copy(cargando = true, error = null)
        observacion = viewModelScope.launch {
            observar(comoCliente = false).collect { resultado ->
                when (resultado) {
                    is AppResult.Success -> {
                        turnos = resultado.data
                        estado.value = estado.value.copy(cargando = false, error = null,
                            resumen = calcular(turnos, estado.value.semanaOffset))
                    }
                    is AppResult.Error -> estado.value = estado.value.copy(
                        cargando = false, error = resultado.error, resumen = null)
                }
            }
        }
    }

    fun semana(offset: Int) {
        estado.value = estado.value.copy(semanaOffset = offset.coerceAtMost(1), mensaje = null,
            resumen = if (estado.value.error == null && !estado.value.cargando)
                calcular(turnos, offset.coerceAtMost(1)) else null)
    }

    fun actualizarSemana() {
        if (!estado.value.cargando && estado.value.error == null) {
            estado.value = estado.value.copy(resumen = calcular(turnos, estado.value.semanaOffset))
        }
    }

    fun marcarRealizado(turno: Turno) {
        if (estado.value.completandoId != null) return
        estado.value = estado.value.copy(completandoId = turno.id, mensaje = null)
        viewModelScope.launch {
            val resultado = completar(turno)
            estado.value = estado.value.copy(completandoId = null,
                mensaje = when (resultado) {
                    is AppResult.Success -> "Turno marcado como realizado."
                    is AppResult.Error -> (resultado.error as? DomainError.Unknown)?.message
                        ?: "No se pudo completar el turno. Volvé a intentar."
                })
        }
    }
}
