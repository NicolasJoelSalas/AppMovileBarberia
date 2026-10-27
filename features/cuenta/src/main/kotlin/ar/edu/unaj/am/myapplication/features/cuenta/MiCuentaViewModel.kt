package ar.edu.unaj.am.myapplication.features.cuenta

import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

import ar.edu.unaj.am.myapplication.features.cuenta.R

import ar.edu.unaj.am.myapplication.core.ui.components.UiMessage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.unaj.am.myapplication.core.domain.model.AppResult
import ar.edu.unaj.am.myapplication.core.domain.usecase.ActualizarMiNombreUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.ObtenerMiPerfilUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MiCuentaViewModel @Inject constructor(
    private val obtenerMiPerfil: ObtenerMiPerfilUseCase,
    private val actualizarMiNombre: ActualizarMiNombreUseCase
) : ViewModel() {
    private val state = MutableStateFlow(MiCuentaUiState())
    val uiState = state.asStateFlow()

    init { cargarPerfil() }

    fun cargarPerfil() {
        if (state.value.guardando) return
        state.update { it.copy(cargando = true, error = null, mensaje = null) }
        viewModelScope.launch {
            when (val result = obtenerMiPerfil()) {
                is AppResult.Success -> state.update {
                    it.copy(cargando = false, perfil = result.data, nombre = result.data.nombre)
                }
                is AppResult.Error -> state.update { it.copy(cargando = false, error = result.error) }
            }
        }
    }

    fun onNombreChange(nombre: String) {
        if (!state.value.guardando) state.update { it.copy(nombre = nombre, error = null, mensaje = null) }
    }

    fun guardarCambios(onGuardado: () -> Unit) {
        if (!state.value.puedeGuardar) return
        val nombre = state.value.nombre.trim()
        state.update { it.copy(guardando = true, error = null, mensaje = null) }
        viewModelScope.launch {
            when (val result = actualizarMiNombre(nombre)) {
                is AppResult.Success -> {
                    state.update { it.copy(guardando = false, nombre = nombre,
                        perfil = it.perfil?.copy(nombre = nombre), mensaje = UiMessage(R.string.cuenta_saved)) }
                    onGuardado()
                }
                is AppResult.Error -> state.update { it.copy(guardando = false, error = result.error) }
            }
        }
    }
}
