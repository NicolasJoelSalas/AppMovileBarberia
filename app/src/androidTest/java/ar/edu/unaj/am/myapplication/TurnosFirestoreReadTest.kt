package ar.edu.unaj.am.myapplication

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import ar.edu.unaj.am.myapplication.core.data.repository.FirestoreTurnoRepository
import ar.edu.unaj.am.myapplication.core.domain.model.AppResult
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Consulta de solo lectura: requiere una sesión de barbero en la app del dispositivo. */
@RunWith(AndroidJUnit4::class)
class TurnosFirestoreReadTest {
    @Test
    fun readsOnlyAuthenticatedBarberAppointmentsFromServer() {
        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid
        assumeTrue("Requiere una sesión de barbero iniciada en la app del emulador.", uid != null)
        Log.i("TURNOS_READ_TEST", "UID de la sesión utilizada: $uid")
        val snapshot = Tasks.await(
            FirebaseFirestore.getInstance().collection("turnos")
                .whereEqualTo("BarberoId", uid)
                .get(Source.SERVER),
            30, TimeUnit.SECONDS
        )
        assertFalse("La consulta debe provenir del servidor.", snapshot.metadata.isFromCache)
        assertTrue(snapshot.documents.all { it.getString("BarberoId") == uid })
        val result = runBlocking {
            withTimeout(30000) { FirestoreTurnoRepository().observeTurnos().first() }
        }
        assertTrue("El repositorio devolvió un error: $result", result is AppResult.Success)
        val turnos = (result as AppResult.Success).data
        assertTrue(turnos.all { it.barberoId == uid })
        Log.i("TURNOS_READ_TEST", "Lectura del servidor correcta: ${snapshot.size()} turnos; repositorio: ${turnos.size}.")
    }
}
