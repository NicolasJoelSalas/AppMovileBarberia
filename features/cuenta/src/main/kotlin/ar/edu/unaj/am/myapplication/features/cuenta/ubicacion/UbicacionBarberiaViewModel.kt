package ar.edu.unaj.am.myapplication.features.cuenta.ubicacion

import ar.edu.unaj.am.myapplication.features.cuenta.R

import ar.edu.unaj.am.myapplication.core.ui.components.UiMessage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.usecase.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class UbicacionBarberiaUiState(
    val cargando: Boolean = true,
    val cargada: Boolean = false,
    val seleccion: Coordenadas? = null,
    val guardada: Coordenadas? = null,
    val guardando: Boolean = false,
    val error: DomainError? = null,
    val mensaje: UiMessage? = null
) {
    val puedeGuardar get() = cargada && !guardando && seleccion != null && seleccion != guardada
}

class UbicacionBarberiaViewModel(
    private val obtener: ObtenerMiUbicacionUseCase,
    private val guardar: GuardarMiUbicacionUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(UbicacionBarberiaUiState())
    val uiState = _uiState.asStateFlow()

    init { cargar() }

    fun cargar() {
        if (_uiState.value.guardando) return
        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            when (val result = obtener()) {
                is AppResult.Success -> _uiState.update { it.copy(cargando = false, cargada = true,
                    seleccion = result.data, guardada = result.data) }
                is AppResult.Error -> _uiState.update { it.copy(cargando = false, error = result.error) }
            }
        }
    }

    fun seleccionar(punto: Coordenadas) {
        if (!_uiState.value.cargada || _uiState.value.guardando || !punto.esValida) return
        _uiState.update { it.copy(seleccion = punto, mensaje = null, error = null) }
    }

    fun guardar() {
        val state = _uiState.value
        if (!state.puedeGuardar) return
        val punto = state.seleccion ?: return
        _uiState.update { it.copy(guardando = true, error = null, mensaje = null) }
        viewModelScope.launch {
            when (val result = guardar(punto)) {
                is AppResult.Success -> _uiState.update { it.copy(guardando = false,
                    guardada = punto, mensaje = UiMessage(R.string.cuenta_location_saved)) }
                is AppResult.Error -> _uiState.update { it.copy(guardando = false, error = result.error) }
            }
        }
    }
}
