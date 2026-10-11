package ar.edu.unaj.am.myapplication.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.usecase.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class HomeUiState(
    val publicaciones: List<Publicacion> = emptyList(),
    val cargando: Boolean = true,
    val error: DomainError? = null,
    val editando: Boolean = false,
    val imagen: String = "",
    val descripcion: String = "",
    val recorteX: Float = 0f,
    val recorteY: Float = 0f,
    val publicando: Boolean = false,
    val eliminando: Boolean = false,
    val errorEdicion: DomainError? = null,
    val mensaje: String? = null
)

class HomeViewModel(
    private val observar: ObservarPublicacionesUseCase,
    private val publicar: PublicarTrabajoUseCase,
    private val eliminar: EliminarPublicacionUseCase,
    private val savedState: SavedStateHandle = SavedStateHandle()
) : ViewModel() {
    private val state = MutableStateFlow(HomeUiState(
        editando = savedState["editando"] ?: false,
        imagen = savedState["imagen"] ?: "",
        descripcion = savedState["descripcion"] ?: "",
        recorteX = savedState["recorteX"] ?: 0f,
        recorteY = savedState["recorteY"] ?: 0f))
    val uiState = state.asStateFlow()
    private var observacion: Job? = null
    private var solicitudId: String = savedState["solicitudId"] ?: UUID.randomUUID().toString()
    init {
        viewModelScope.launch {
            state.collect {
                savedState["editando"] = it.editando
                savedState["imagen"] = it.imagen
                savedState["descripcion"] = it.descripcion
                savedState["recorteX"] = it.recorteX
                savedState["recorteY"] = it.recorteY
                savedState["solicitudId"] = solicitudId
            }
        }
        reintentar()
    }

    fun reintentar() {
        observacion?.cancel()
        state.update { it.copy(cargando = true, error = null) }
        observacion = viewModelScope.launch {
            observar().collect { result ->
                state.update { when (result) {
                    is AppResult.Success -> it.copy(cargando = false, publicaciones = result.data, error = null)
                    is AppResult.Error -> it.copy(cargando = false, error = result.error)
                } }
            }
        }
    }
    fun nueva() {
        if (state.value.publicando || state.value.eliminando) return
        solicitudId = UUID.randomUUID().toString()
        state.update { it.copy(editando = true, imagen = "", descripcion = "", recorteX = 0f,
            recorteY = 0f, errorEdicion = null, mensaje = null) }
    }
    fun cerrarEditor() {
        if (!state.value.publicando) state.update { it.copy(editando = false, imagen = "", descripcion = "") }
    }
    fun imagen(uri: String) {
        if (state.value.publicando) return
        solicitudId = UUID.randomUUID().toString()
        state.update { it.copy(imagen = uri, recorteX = 0f, recorteY = 0f, errorEdicion = null) }
    }
    fun descripcion(value: String) {
        if (!state.value.publicando && value.length <= 300) state.update { it.copy(descripcion = value) }
    }
    fun encuadre(x: Float, y: Float) {
        if (!state.value.publicando) {
            solicitudId = UUID.randomUUID().toString()
            state.update { it.copy(recorteX = x.coerceIn(-1f, 1f), recorteY = y.coerceIn(-1f, 1f)) }
        }
    }
    fun errorFoto(message: String) { state.update { it.copy(errorEdicion = DomainError.Unknown(message)) } }
    fun confirmar() {
        val actual = state.value
        if (actual.publicando || actual.imagen.isBlank()) return
        state.update { it.copy(publicando = true, errorEdicion = null) }
        viewModelScope.launch {
            val result = publicar(NuevaPublicacion(solicitudId, actual.imagen, actual.descripcion,
                actual.recorteX, actual.recorteY))
            state.update { when (result) {
                is AppResult.Success -> it.copy(publicando = false, editando = false, imagen = "",
                    descripcion = "", mensaje = "Trabajo publicado.")
                is AppResult.Error -> it.copy(publicando = false, errorEdicion = result.error)
            } }
        }
    }
    fun eliminar(publicacion: Publicacion) {
        if (!publicacion.esPropia || state.value.eliminando || state.value.publicando) return
        state.update { it.copy(eliminando = true, mensaje = null) }
        viewModelScope.launch {
            val result = eliminar(publicacion.id)
            state.update { when (result) {
                is AppResult.Success -> it.copy(eliminando = false, mensaje = "Publicación eliminada.")
                is AppResult.Error -> it.copy(eliminando = false, mensaje = "No se pudo eliminar la publicación. Reintentá.")
            } }
        }
    }
}
