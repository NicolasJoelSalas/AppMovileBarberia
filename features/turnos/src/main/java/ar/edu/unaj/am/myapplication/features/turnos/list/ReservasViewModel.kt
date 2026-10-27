package ar.edu.unaj.am.myapplication.features.turnos.list

import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

import ar.edu.unaj.am.myapplication.features.turnos.R

import ar.edu.unaj.am.myapplication.core.ui.components.UiMessage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.usecase.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ReservasUiState(
    val turnoId: String? = null,
    val inicioOriginal: Long? = null,
    val barberoOriginal: String? = null,
    val loading: Boolean = true,
    val catalogo: CatalogoReservas? = null,
    val barberoId: String = "",
    val servicioId: String = "",
    val semana: SemanaDisponible? = null,
    val semanaOffset: Int = 0,
    val selected: HorarioDisponible? = null,
    val nombre: String = "",
    val referencia: String? = null,
    val cambiarReferencia: Boolean = false,
    val reservando: Boolean = false,
    val reservaConfirmada: Boolean = false,
    val error: DomainError? = null,
    val mensaje: UiMessage? = null
)

@HiltViewModel
class ReservasViewModel @Inject constructor(
    private val catalogo: CargarCatalogoReservasUseCase,
    private val consultar: ConsultarSemanaUseCase,
    private val reservar: ReservarTurnoUseCase,
    private val modificar: ModificarTurnoUseCase? = null,
    private val politica: PoliticaReservas = PoliticaReservas()
) : ViewModel() {
    private val state = MutableStateFlow(ReservasUiState())
    val uiState = state.asStateFlow()
    private var availabilityJob: Job? = null
    private var requestId: String? = null
    init { cargar() }

    fun cargar() {
        if (state.value.reservando) return
        availabilityJob?.cancel()
        state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = catalogo()) {
                is AppResult.Error -> state.update { it.copy(loading = false, error = result.error) }
                is AppResult.Success -> state.update {
                    it.copy(loading = false, catalogo = result.data, nombre = result.data.nombreCliente)
                }
            }
        }
    }
    fun editar(turnoId: String, barberoId: String, servicioId: String, inicio: Long, referencia: String? = null) {
        if (state.value.turnoId == turnoId) return
        val zona = state.value.catalogo?.barberos?.firstOrNull { it.id == barberoId }?.zonaHoraria
        val offset = if (zona == null) politica.offsetOriginal(inicio) else politica.offsetOriginal(inicio,java.time.ZoneId.of(zona))
        state.update { it.copy(turnoId = turnoId, referencia = referencia, inicioOriginal = inicio, barberoOriginal = barberoId, barberoId = barberoId, servicioId = servicioId,
            semanaOffset = offset) }
        refrescar()
    }
    fun nombre(value: String) { if (!state.value.reservando) state.update { it.copy(nombre = value) } }
    fun referencia(value: String?) {
        if (state.value.reservando) return
        state.update { it.copy(referencia = value, cambiarReferencia = true) }
    }
    fun semana(offset: Int) {
        if (offset !in 0..1 || state.value.reservando || offset == state.value.semanaOffset) return
        state.update { it.copy(semanaOffset = offset, selected = null, semana = null, mensaje = null) }
        requestId = null
        refrescar()
    }
    fun barbero(id: String) {
        if (state.value.reservando) return
        state.update { it.copy(barberoId = id,
            servicioId = it.servicioId.takeIf { servicio -> it.catalogo?.servicios?.any { opcion ->
                opcion.id == servicio && opcion.barberoId == id
            } == true }.orEmpty(), selected = null, semana = null, mensaje = null) }
        requestId = null
        refrescar()
    }
    fun servicio(id: String) {
        if (state.value.reservando) return
        state.update { it.copy(servicioId = id, selected = null, semana = null, mensaje = null) }
        requestId = null
        refrescar()
    }
    fun seleccionar(slot: HorarioDisponible?) {
        if (state.value.reservando) return
        requestId = slot?.let { UUID.randomUUID().toString() }
        state.update { it.copy(selected = slot, mensaje = null) }
    }
    fun refrescar() {
        val current = state.value
        if (current.reservando || current.barberoId.isBlank() || current.servicioId.isBlank()) return
        availabilityJob?.cancel()
        requestId = null
        state.update { it.copy(loading = true, semana = null, selected = null, error = null) }
        availabilityJob = viewModelScope.launch {
            when (val result = consultar(current.barberoId, current.servicioId, current.semanaOffset, current.turnoId)) {
                is AppResult.Error -> state.update { it.copy(loading = false, error = result.error) }
                is AppResult.Success -> state.update { it.copy(loading = false, semana = result.data) }
            }
        }
    }
    fun confirmar() {
        val current = state.value
        val selected = current.selected ?: return
        if (current.reservando) return
        state.update { it.copy(reservando = true, error = null, mensaje = null) }
        viewModelScope.launch {
            val solicitud = SolicitudReserva(
                requestId ?: UUID.randomUUID().toString().also { requestId = it },
                current.barberoId, current.servicioId, selected.inicioMillis, current.nombre,
                referenciaLocal = current.referencia.takeIf { current.cambiarReferencia },
                cambiarReferencia = current.cambiarReferencia,
                finMillisEsperado = selected.finMillis
            )
            val result = if (current.turnoId != null) modificar?.invoke(current.turnoId, solicitud)
                ?: AppResult.Error(DomainError.Known(ErrorReason.BOOKING_UPDATE)) else reservar(solicitud)
            when (result) {
                is AppResult.Success -> {
                    state.update { it.copy(reservando = false, selected = null, reservaConfirmada = true,
                        mensaje = if (current.turnoId != null) UiMessage(R.string.turnos_changed) else UiMessage(R.string.turnos_booked)) }
                    requestId = null
                    refrescar()
                }
                is AppResult.Error -> state.update { it.copy(reservando = false, error = result.error) }
            }
        }
    }
}
