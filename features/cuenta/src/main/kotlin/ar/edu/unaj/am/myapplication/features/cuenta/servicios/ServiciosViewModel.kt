package ar.edu.unaj.am.myapplication.features.cuenta.servicios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.usecase.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

data class EditorServicio(
    val id: String, val nuevo: Boolean, val nombre: String = "", val precio: String = "",
    val duracion: String = "40"
)

data class ServiciosUiState(
    val cargando: Boolean = true, val guardando: Boolean = false,
    val servicios: List<ServicioBarbero> = emptyList(), val editor: EditorServicio? = null,
    val error: DomainError? = null
)

class ServiciosViewModel(
    private val obtener: ObtenerMisServiciosUseCase,
    private val guardarServicio: GuardarServicioUseCase,
    private val cambiarEstado: CambiarEstadoServicioUseCase
) : ViewModel() {
    private val state = MutableStateFlow(ServiciosUiState())
    val uiState = state.asStateFlow()
    init { cargar() }

    fun cargar() {
        if (state.value.guardando || state.value.editor != null) return
        state.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch { actualizar() }
    }
    private suspend fun actualizar() {
        when (val result = obtener()) {
            is AppResult.Success -> state.update { it.copy(cargando = false, servicios = result.data) }
            is AppResult.Error -> state.update { it.copy(cargando = false, error = result.error) }
        }
    }
    fun editar(servicio: ServicioBarbero? = null) {
        if (state.value.guardando || state.value.cargando) return
        state.update { it.copy(error = null, editor = if (servicio == null)
            EditorServicio(UUID.randomUUID().toString(), nuevo = true)
        else EditorServicio(servicio.id, nuevo = false, servicio.nombre,
            BigDecimal.valueOf(servicio.precioCentavos, 2).toPlainString(), servicio.duracionMinutos.toString())) }
    }
    fun nombre(value: String) { modificar { it.copy(nombre = value) } }
    fun precio(value: String) { modificar { it.copy(precio = value) } }
    fun duracion(value: String) { modificar { it.copy(duracion = value) } }
    private fun modificar(block: (EditorServicio) -> EditorServicio) {
        if (!state.value.guardando) state.update { it.copy(editor = it.editor?.let(block), error = null) }
    }
    fun cerrarEditor() { if (!state.value.guardando) state.update { it.copy(editor = null, error = null) } }

    fun guardar() {
        val editor = state.value.editor ?: return
        if (state.value.guardando) return
        val centavos = parsearPrecioCentavos(editor.precio)
        val duracion = editor.duracion.toIntOrNull()
        if (centavos == null || duracion == null) {
            state.update { it.copy(error = DomainError.Known(ErrorReason.SERVICE_FORMAT)) }
            return
        }
        state.update { it.copy(guardando = true, error = null) }
        viewModelScope.launch {
            when (val result = guardarServicio(ServicioBarbero(editor.id, editor.nombre, centavos, duracion))) {
                is AppResult.Error -> state.update { it.copy(guardando = false, error = result.error) }
                is AppResult.Success -> {
                    state.update { it.copy(editor = null) }
                    actualizar()
                    state.update { it.copy(guardando = false) }
                }
            }
        }
    }
    fun estado(servicio: ServicioBarbero) {
        if (state.value.guardando || state.value.cargando) return
        state.update { it.copy(guardando = true, error = null) }
        viewModelScope.launch {
            when (val result = cambiarEstado(servicio.id, !servicio.activo)) {
                is AppResult.Error -> state.update { it.copy(error = result.error) }
                is AppResult.Success -> actualizar()
            }
            state.update { it.copy(guardando = false) }
        }
    }
}

internal fun parsearPrecioCentavos(value: String): Long? = try {
    val normalizado = value.trim().replace(',', '.')
    if (!normalizado.matches(Regex("[0-9]+(?:\\.[0-9]{1,2})?"))) null
    else BigDecimal(normalizado).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
} catch (_: NumberFormatException) { null } catch (_: ArithmeticException) { null }
