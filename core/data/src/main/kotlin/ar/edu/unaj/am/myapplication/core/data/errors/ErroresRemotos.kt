package ar.edu.unaj.am.myapplication.core.data.errors

import ar.edu.unaj.am.myapplication.core.domain.model.*
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.IOException
import kotlinx.coroutines.CancellationException

/** Traduce errores técnicos a motivos estables. Nunca genera texto para la pantalla. */
fun errorRemoto(error: Exception, fallback: ErrorReason): DomainError = when (error) {
    is CancellationException -> throw error
    is DomainValidationException -> error.error
    is FirebaseNetworkException, is IOException -> DomainError.Network
    is FirebaseFirestoreException -> when (error.code) {
        FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> DomainError.Network
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> DomainError.Known(ErrorReason.ACCESS_DENIED)
        FirebaseFirestoreException.Code.UNAUTHENTICATED -> DomainError.Known(ErrorReason.APPOINTMENTS_SIGNIN)
        FirebaseFirestoreException.Code.NOT_FOUND -> DomainError.NotFound
        else -> DomainError.Known(fallback)
    }
    else -> DomainError.Known(fallback)
}
