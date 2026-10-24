package ar.edu.unaj.am.myapplication.features.turnos.ubicacion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.usecase.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ComoLlegarUiState(
    val cargando: Boolean = true,
    val destino: Coordenadas? = null,
    val origen: Coordenadas? = null,
    val ruta: RutaBarberia? = null,
    val calculando: Boolean = false,
    val error: DomainError? = null
)

class ComoLlegarViewModel(
    private val barberoId: String,
    private val obtener: ObtenerUbicacionBarberiaUseCase,
    private val calcular: CalcularRutaBarberiaUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(ComoLlegarUiState())
    val uiState = _uiState.asStateFlow()
    private var carga: Job? = null
    private var consulta: Job? = null

    init { cargar() }

    fun cargar() {
        if (carga?.isActive == true) return
        _uiState.update { it.copy(cargando = true, error = null) }
        carga = viewModelScope.launch {
            when (val result = obtener(barberoId)) {
                is AppResult.Success -> _uiState.update { it.copy(cargando = false, destino = result.data) }
                is AppResult.Error -> _uiState.update { it.copy(cargando = false, error = result.error) }
            }
        }
    }

    fun origen(punto: Coordenadas) {
        if (!punto.esValida || _uiState.value.destino == null) return
        _uiState.update { it.copy(origen = punto, ruta = null) }
        calcularRuta()
    }

    fun calcularRuta() {
        val state = _uiState.value
        val origen = state.origen ?: return
        val destino = state.destino ?: return
        consulta?.cancel()
        _uiState.update { it.copy(calculando = true, error = null, ruta = null) }
        consulta = viewModelScope.launch {
            delay(400) // No consultar por cada toque mientras se ajusta el origen.
            when (val result = calcular(origen, destino)) {
                is AppResult.Success -> _uiState.update { it.copy(calculando = false, ruta = result.data) }
                is AppResult.Error -> _uiState.update { it.copy(calculando = false, error = result.error) }
            }
        }
    }
}
