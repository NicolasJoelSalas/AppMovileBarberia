package ar.edu.unaj.am.myapplication.features.turnos.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.unaj.am.myapplication.core.domain.model.AppResult
import ar.edu.unaj.am.myapplication.core.domain.model.DomainError
import ar.edu.unaj.am.myapplication.core.domain.model.Turno
import ar.edu.unaj.am.myapplication.core.domain.usecase.ObserveTurnosUseCase
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
    private val observeTurnos: ObserveTurnosUseCase
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<TurnosListUiState>(TurnosListUiState.Loading)
    val uiState = mutableUiState.asStateFlow()
    private var observation: Job? = null

    init { onRetry() }

    fun onRetry() {
        observation?.cancel()
        mutableUiState.value = TurnosListUiState.Loading
        observation = viewModelScope.launch {
            observeTurnos().collect { result ->
                mutableUiState.value = when (result) {
                    is AppResult.Success -> TurnosListUiState.Success(result.data)
                    is AppResult.Error -> TurnosListUiState.Error(result.error)
                }
            }
        }
    }
}
