package ar.edu.unaj.am.myapplication.features.turnos.ubicacion

import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.assisted.AssistedFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import ar.edu.unaj.am.myapplication.features.turnos.di.MonotonicClock

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
    val siguiendoUbicacion: Boolean = false,
    val distanciaRestanteMetros: Double? = null,
    val avisoUbicacion: String? = null,
    val error: DomainError? = null
)

@HiltViewModel(assistedFactory = ComoLlegarViewModel.Factory::class)
class ComoLlegarViewModel @AssistedInject constructor(
    @Assisted private val barberoId: String,
    private val obtener: ObtenerUbicacionBarberiaUseCase,
    private val calcular: CalcularRutaBarberiaUseCase,
    @MonotonicClock private val reloj: () -> Long = { System.nanoTime() / 1_000_000 }
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(barberoId: String): ComoLlegarViewModel
    }

    private val _uiState = MutableStateFlow(ComoLlegarUiState())
    val uiState = _uiState.asStateFlow()
    private var carga: Job? = null
    private var consulta: Job? = null
    private var ultimaConsulta: Long? = null
    private var ultimaLectura: Long = Long.MIN_VALUE
    private var primerDesvio: Long? = null
    private var versionConsulta = 0
    private var consultaInterrumpida = false

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
        primerDesvio = null
        _uiState.update { it.copy(origen = punto, ruta = null, distanciaRestanteMetros = null,
            siguiendoUbicacion = false, avisoUbicacion = null) }
        calcularRuta()
    }

    fun iniciarSeguimiento(punto: Coordenadas, precision: Float, instante: Long) {
        if (!punto.esValida || _uiState.value.destino == null) return
        ultimaLectura = Long.MIN_VALUE
        primerDesvio = null
        _uiState.update { it.copy(siguiendoUbicacion = true, avisoUbicacion = null) }
        actualizarPosicion(punto, precision, instante, inicial = true)
    }

    fun actualizarPosicion(punto: Coordenadas, precision: Float, instante: Long, inicial: Boolean = false) {
        val state = _uiState.value
        if (!state.siguiendoUbicacion || !punto.esValida || !precision.isFinite() || precision < 0 || instante <= ultimaLectura) return
        ultimaLectura = instante
        // Una lectura muy imprecisa no desplaza una posición ya establecida ni provoca desvíos.
        if (!inicial && precision > 100) { primerDesvio = null; return }
        val progreso = state.ruta?.let { evaluarProgresoRuta(punto, it) }
        _uiState.update { it.copy(origen = punto, avisoUbicacion = null,
            distanciaRestanteMetros = if (progreso != null && progreso.desvioMetros <= 50)
                progreso.distanciaRestanteMetros else it.distanciaRestanteMetros) }
        if (inicial) { calcularRuta(); return }
        if (precision > 50) { primerDesvio = null; return }
        if (progreso != null && progreso.desvioMetros <= maxOf(50.0, precision.toDouble())) {
            primerDesvio = null
            return
        }
        val desde = primerDesvio
        if (desde == null) { primerDesvio = instante; return }
        val puedeConsultar = ultimaConsulta?.let { reloj() - it >= 10_000 } ?: true
        if (instante - desde >= 3_000 && puedeConsultar && consulta?.isActive != true) {
            primerDesvio = null
            calcularRuta()
        }
    }

    fun avisoUbicacion(mensaje: String?) { _uiState.update { it.copy(avisoUbicacion = mensaje) } }

    fun suspenderConsulta() {
        consultaInterrumpida = consulta?.isActive == true
        versionConsulta++
        consulta?.cancel()
        primerDesvio = null
        _uiState.update { it.copy(calculando = false) }
    }

    fun reanudarConsulta() {
        if (consultaInterrumpida) calcularRuta()
    }

    fun calcularRuta() {
        val state = _uiState.value
        val origen = state.origen ?: return
        val destino = state.destino ?: return
        consultaInterrumpida = false
        consulta?.cancel()
        val version = ++versionConsulta
        _uiState.update { it.copy(calculando = true, error = null) }
        consulta = viewModelScope.launch {
            delay(400) // No consultar por cada toque mientras se ajusta el origen.
            ultimaConsulta = reloj()
            when (val result = calcular(origen, destino)) {
                is AppResult.Success -> if (version == versionConsulta) _uiState.update {
                    it.copy(calculando = false, ruta = result.data,
                        distanciaRestanteMetros = if (it.siguiendoUbicacion && it.origen != null)
                            evaluarProgresoRuta(it.origen, result.data).distanciaRestanteMetros else result.data.distanciaMetros)
                }
                is AppResult.Error -> if (version == versionConsulta) _uiState.update { it.copy(calculando = false, error = result.error) }
            }
        }
    }
}
