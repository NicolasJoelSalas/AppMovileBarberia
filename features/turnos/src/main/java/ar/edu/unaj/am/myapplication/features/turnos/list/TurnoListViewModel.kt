package ar.edu.unaj.am.myapplication.features.turnos.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.unaj.am.myapplication.core.domain.model.AppResult
import ar.edu.unaj.am.myapplication.core.domain.model.DomainError
import ar.edu.unaj.am.myapplication.core.domain.model.Turno
import ar.edu.unaj.am.myapplication.core.domain.usecase.ObserveTurnosUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.MarcarTurnoRealizadoUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.FiltrarTurnosPorSemanaUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface TurnosListUiState {
    data object Loading : TurnosListUiState
    data class Success(val turnos: List<Turno>) : TurnosListUiState
    data class Error(val error: DomainError) : TurnosListUiState
}

class TurnoListViewModel(
    private val observeTurnos: ObserveTurnosUseCase,
    private val comoCliente: Boolean = false,
    private val filtrarSemana: FiltrarTurnosPorSemanaUseCase = FiltrarTurnosPorSemanaUseCase(),
    private val completarTurno: MarcarTurnoRealizadoUseCase? = null,
    private val cancelarTurno: ar.edu.unaj.am.myapplication.core.domain.usecase.CancelarTurnoUseCase? = null
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<TurnosListUiState>(TurnosListUiState.Loading)
    val uiState = mutableUiState.asStateFlow()
    private val mutableAvisoSincronizacion = MutableStateFlow<DomainError?>(null)
    val avisoSincronizacion = mutableAvisoSincronizacion.asStateFlow()
    private var observation: Job? = null
    private var todosLosTurnos: List<Turno> = emptyList()
    private val mutableTurnosParaRecordatorios = MutableStateFlow<List<Turno>?>(null)
    val turnosParaRecordatorios = mutableTurnosParaRecordatorios.asStateFlow()
    private val mutableSemanaOffset = MutableStateFlow(0)
    val semanaOffset = mutableSemanaOffset.asStateFlow()
    val puedeCompletar: Boolean get() = !comoCliente && completarTurno != null
    private val mutableCompletandoId = MutableStateFlow<String?>(null)
    val completandoId = mutableCompletandoId.asStateFlow()
    private val mutableErrorCompletar = MutableStateFlow<DomainError?>(null)
    val errorCompletar = mutableErrorCompletar.asStateFlow()

    val puedeCancelar: Boolean get() = comoCliente && cancelarTurno != null
    fun cancelar(turno: Turno) {
        val accion = cancelarTurno ?: return
        if (!puedeCancelar || mutableCompletandoId.value != null) return
        mutableCompletandoId.value = turno.id
        mutableErrorCompletar.value = null
        viewModelScope.launch {
            try {
                when (val result = accion(turno.id)) {
                    is AppResult.Error -> mutableErrorCompletar.value = result.error
                    is AppResult.Success -> Unit
                }
            } finally { mutableCompletandoId.value = null }
        }
    }
    fun marcarRealizado(turno: Turno) {
        val completar = completarTurno ?: return
        if (!puedeCompletar || mutableCompletandoId.value != null) return
        mutableCompletandoId.value = turno.id
        mutableErrorCompletar.value = null
        viewModelScope.launch {
            try {
                when (val result = completar(turno)) {
                    is AppResult.Success -> {
                        todosLosTurnos = todosLosTurnos.map {
                            if (it.id == turno.id) it.copy(estado = "realizado") else it
                        }
                        mutableTurnosParaRecordatorios.value = todosLosTurnos
                        actualizarSemana()
                    }
                    is AppResult.Error -> mutableErrorCompletar.value = result.error
                }
            } finally { mutableCompletandoId.value = null }
        }
    }

    fun seleccionarSemana(offset: Int) {
        if (offset !in 0..1) return
        mutableSemanaOffset.value = offset
        actualizarSemana()
    }

    fun actualizarSemana() {
        if (mutableUiState.value is TurnosListUiState.Success) {
            mutableUiState.value = TurnosListUiState.Success(filtrarSemana(todosLosTurnos, mutableSemanaOffset.value))
        }
    }

    init { onRetry() }

    fun onRetry() {
        observation?.cancel()
        mutableAvisoSincronizacion.value = null
        mutableUiState.value = TurnosListUiState.Loading
        observation = viewModelScope.launch {
            observeTurnos(comoCliente).collect { result ->
                mutableUiState.value = when (result) {
                    is AppResult.Success -> {
                        mutableAvisoSincronizacion.value = null
                        todosLosTurnos = result.data
                        mutableTurnosParaRecordatorios.value = result.data
                        TurnosListUiState.Success(filtrarSemana(result.data, mutableSemanaOffset.value))
                    }
                    is AppResult.Error -> if (comoCliente && mutableUiState.value is TurnosListUiState.Success) {
                        mutableAvisoSincronizacion.value = result.error
                        mutableUiState.value
                    } else TurnosListUiState.Error(result.error)
                }
            }
        }
    }
}
