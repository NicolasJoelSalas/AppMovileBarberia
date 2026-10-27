package ar.edu.unaj.am.myapplication.core.data.errors

import ar.edu.unaj.am.myapplication.core.domain.model.*
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*
import android.util.SparseArray

class ErroresRemotosTest {
    private val fallback = ErrorReason.RESERVATION_OPERATION
    @Test fun `preserva el motivo de negocio sin traducirlo`() {
        val error = DomainValidationException(DomainError.Known(ErrorReason.BOOKING_TAKEN))
        assertEquals(error.error, errorRemoto(error, fallback))
    }
    @Test fun `clasifica red y permisos sin filtrar mensajes del servidor`() {
        mockConstruction(SparseArray::class.java).use {
        assertEquals(DomainError.Network, errorRemoto(IOException("detalle"), fallback))
        assertEquals(DomainError.Network, errorRemoto(firestoreError(FirebaseFirestoreException.Code.UNAVAILABLE), fallback))
        assertEquals(DomainError.Known(ErrorReason.ACCESS_DENIED), errorRemoto(firestoreError(FirebaseFirestoreException.Code.PERMISSION_DENIED), fallback))
        assertEquals(DomainError.Known(fallback), errorRemoto(IllegalStateException("detalle"), fallback))
        }
    }
    private fun firestoreError(code: FirebaseFirestoreException.Code): FirebaseFirestoreException =
        mock(FirebaseFirestoreException::class.java).also { `when`(it.code).thenReturn(code) }
    @Test(expected = CancellationException::class) fun `propaga la cancelacion`() {
        errorRemoto(CancellationException(), fallback)
    }
}
