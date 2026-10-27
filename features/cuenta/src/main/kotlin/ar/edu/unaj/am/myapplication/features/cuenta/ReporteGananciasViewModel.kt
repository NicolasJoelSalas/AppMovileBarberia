package ar.edu.unaj.am.myapplication.features.cuenta

import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.usecase.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ReporteGananciasUiState(
    val cargando: Boolean = true,
    val mensual: Boolean = false,
    val offset: Int = 0,
    val reporte: ReporteGanancias? = null,
    val error: DomainError? = null
)

@HiltViewModel
class ReporteGananciasViewModel @Inject constructor(
    private val observar: ObserveTurnosUseCase,
    private val calcular: CalcularReporteGananciasUseCase = CalcularReporteGananciasUseCase()
) : ViewModel() {
    private val state = MutableStateFlow(ReporteGananciasUiState())
    val uiState = state.asStateFlow()
    private var turnos: List<Turno> = emptyList()
    private var job: Job? = null

    fun cargar() {
        job?.cancel()
        state.update { it.copy(cargando = it.reporte == null, error = null) }
        job = viewModelScope.launch {
            observar(comoCliente = false).collect { result ->
                when (result) {
                    is AppResult.Success -> {
                        turnos = result.data
                        state.update { it.copy(cargando = false, error = null,
                            reporte = calcular(turnos, it.mensual, it.offset)) }
                    }
                    is AppResult.Error -> state.update { it.copy(cargando = false, error = result.error) }
                }
            }
        }
    }
    fun detener() {
        job?.cancel()
        turnos = emptyList()
        state.value = ReporteGananciasUiState()
    }
    fun periodo(mensual: Boolean) {
        state.update { it.copy(mensual = mensual, offset = 0) }
        actualizar()
    }
    fun mover(delta: Int) {
        state.update { it.copy(offset = (it.offset + delta).coerceAtMost(0)) }
        actualizar()
    }
    fun actualizar() {
        if (!state.value.cargando && state.value.reporte != null)
            state.update { it.copy(reporte = calcular(turnos, it.mensual, it.offset)) }
    }
}
