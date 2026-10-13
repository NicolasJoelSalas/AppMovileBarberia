package ar.edu.unaj.am.myapplication.features.cuenta

import ar.edu.unaj.am.myapplication.core.domain.model.DomainError
import ar.edu.unaj.am.myapplication.core.domain.model.PerfilUsuario

data class MiCuentaUiState(
    val cargando: Boolean = true,
    val perfil: PerfilUsuario? = null,
    val nombre: String = "",
    val guardando: Boolean = false,
    val error: DomainError? = null,
    val mensaje: String? = null
) {
    val puedeGuardar: Boolean
        get() = perfil != null && !cargando && !guardando &&
            nombre.trim().isNotEmpty() && nombre.trim().length <= 120 && nombre.trim() != perfil.nombre
}
