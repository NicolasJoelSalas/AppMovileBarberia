package ar.edu.unaj.am.myapplication.core.domain.model

/**
 * Resultado de una operación del dominio.
 *
 * En vez de lanzar excepciones hacia la UI, cada capa devuelve un valor que
 * dice explícitamente si salió bien o mal. El `when` en el ViewModel es
 * exhaustivo: el compilador nos obliga a manejar el caso de error.
 */
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Error(val error: DomainError) : AppResult<Nothing>
}

/**
 * Errores que entiende el negocio. La UI decide cómo mostrarlos (texto, ícono…),
 * y la capa de datos decide cómo traducir un IOException o un HTTP 404 a uno de estos.
 */
sealed interface DomainError {
    data object Network : DomainError
    data object NotFound : DomainError
    data object EmptyTitle : DomainError
    data class Known(val reason: ErrorReason, val dayOfWeek: Int? = null) : DomainError
    data class Unknown(val message: String?) : DomainError
}
