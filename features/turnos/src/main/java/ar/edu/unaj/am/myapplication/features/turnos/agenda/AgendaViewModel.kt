package ar.edu.unaj.am.myapplication.features.turnos.agenda

import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

import ar.edu.unaj.am.myapplication.features.turnos.R

import ar.edu.unaj.am.myapplication.core.ui.components.UiMessage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.usecase.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AgendaUiState(
    val cargando: Boolean = true,
    val guardando: Boolean = false,
    val agenda: AgendaSemanal? = null,
    val cambios: Boolean = false,
    val semanaOffset: Int = 0,
    val error: DomainError? = null,
    val mensaje: UiMessage? = null
)

@HiltViewModel
class AgendaViewModel @Inject constructor(
    private val obtener: ObtenerMiAgendaUseCase,
    private val guardar: GuardarYPublicarAgendaUseCase
) : ViewModel() {
    private val state = MutableStateFlow(AgendaUiState())
    val uiState = state.asStateFlow()
    private var original: AgendaSemanal? = null
    private val borradores = mutableMapOf<Int, Pair<AgendaSemanal, AgendaSemanal?>>()
    init { cargar() }

    fun semana(offset: Int) {
        if (offset !in 0..1 || offset == state.value.semanaOffset || state.value.guardando || state.value.cargando) return
        state.value.agenda?.let { borradores[state.value.semanaOffset] = it to original }
        state.value = AgendaUiState(semanaOffset = offset)
        val draft = borradores[offset]
        if (draft != null) {
            original = draft.second
            state.value = AgendaUiState(cargando = false, agenda = draft.first, semanaOffset = offset,
                cambios = draft.first != original)
        } else cargar()
    }

    fun cargar() {
        if (state.value.guardando) return
        state.update { it.copy(cargando = true, error = null) }
        val offset = state.value.semanaOffset
        viewModelScope.launch {
            when (val result = obtener(offset)) {
                is AppResult.Error -> state.update { it.copy(cargando = false, error = result.error) }
                is AppResult.Success -> {
                    original = result.data
                    state.value = AgendaUiState(cargando = false, agenda = result.data, semanaOffset = offset)
                }
            }
        }
    }

    private fun editar(index: Int, edit: (DiaAtencion) -> DiaAtencion) {
        if (state.value.guardando || state.value.cargando) return
        val agenda = state.value.agenda ?: return
        if (index !in agenda.dias.indices) return
        val changed = agenda.copy(dias = agenda.dias.mapIndexed { i, day -> if (i == index) edit(day) else day })
        state.update { it.copy(agenda = changed, cambios = changed != original, error = null, mensaje = null) }
    }
    fun habilitar(index: Int, enabled: Boolean) = editar(index) {
        it.copy(habilitado = enabled, franjas = if (enabled && it.franjas.isEmpty())
            listOf(FranjaAtencion("", "")) else it.franjas)
    }
    fun agregar(index: Int) = editar(index) { it.copy(franjas = it.franjas + FranjaAtencion("", "")) }
    fun quitar(index: Int, span: Int) = editar(index) {
        val franjas = it.franjas.filterIndexed { i, _ -> i != span }
        it.copy(franjas = franjas, habilitado = it.habilitado && franjas.isNotEmpty())
    }
    fun hora(index: Int, span: Int, inicio: Boolean, value: String) = editar(index) { day ->
        day.copy(franjas = day.franjas.mapIndexed { i, item ->
            if (i != span) item else if (inicio) item.copy(inicio = value) else item.copy(fin = value)
        })
    }
    fun guardar() {
        val current = state.value
        val agenda = current.agenda ?: return
        if (current.guardando || current.cargando) return
        state.update { it.copy(guardando = true, error = null, mensaje = null) }
        viewModelScope.launch {
            when (val result = guardar(agenda)) {
                is AppResult.Error -> state.update { it.copy(guardando = false, error = result.error) }
                is AppResult.Success -> {
                    original = agenda
                    state.update { it.copy(guardando = false, cambios = false,
                        error = result.data.errorPublicacion,
                        mensaje = if (result.data.errorPublicacion != null || result.data.horariosPublicados == null)
                            UiMessage(R.string.agenda_pending)
                        else UiMessage(R.string.agenda_published, listOf(requireNotNull(result.data.horariosPublicados)))) }
                }
            }
        }
    }

    fun tieneCambiosPendientes() = state.value.cambios || borradores.any { (offset, draft) ->
        offset != state.value.semanaOffset && draft.first != draft.second
    }
}
