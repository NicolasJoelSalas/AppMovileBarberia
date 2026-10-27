package ar.edu.unaj.am.myapplication.core.data.notifications

import ar.edu.unaj.am.myapplication.core.domain.usecase.RegistroNotificaciones

import android.content.Context
import android.content.SharedPreferences
import ar.edu.unaj.am.myapplication.core.domain.repository.DispositivoRepository
import ar.edu.unaj.am.myapplication.core.domain.usecase.DesvincularDispositivoUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.RegistrarDispositivoUseCase
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseTokenNotificacionesTest {
    private val contexto = mock(Context::class.java)
    private val preferencias = mock(SharedPreferences::class.java)
    private val messaging = mock(FirebaseMessaging::class.java)
    private val registros = mutableListOf<Pair<String, String>>()
    private val bajas = mutableListOf<String>()
    private val repo = object : DispositivoRepository {
        override suspend fun registrar(instalacionId: String, token: String) { registros += instalacionId to token }
        override suspend fun desvincular(instalacionId: String) { bajas += instalacionId }
    }
    private fun registro(): RegistroNotificaciones {
        `when`(contexto.applicationContext).thenReturn(contexto)
        `when`(contexto.getSharedPreferences("fcm_installation", Context.MODE_PRIVATE)).thenReturn(preferencias)
        `when`(preferencias.getString("id", null)).thenReturn("instalacion")
        return RegistroNotificaciones(FirebaseTokenNotificaciones(contexto,messaging), RegistrarDispositivoUseCase(repo), DesvincularDispositivoUseCase(repo))
    }
    @Suppress("UNCHECKED_CAST")
    private fun borrado(falla: Boolean = false) {
        val tarea = mock(Task::class.java) as Task<Void>
        `when`(messaging.deleteToken()).thenReturn(tarea)
        `when`(tarea.addOnSuccessListener(any())).thenAnswer {
            if (!falla) it.getArgument<OnSuccessListener<Void>>(0).onSuccess(null)
            tarea
        }
        `when`(tarea.addOnFailureListener(any())).thenAnswer {
            if (falla) it.getArgument<OnFailureListener>(0).onFailure(IllegalStateException("Sin conexión"))
            tarea
        }
    }
    @Test fun `token renewal reuses installation and logout prevents later registrations`() = runTest {
        borrado()
        val registro = registro()
        registro.registrar("primero")
        registro.registrar("renovado")
        assertEquals(listOf("instalacion" to "primero", "instalacion" to "renovado"), registros)
        registro.cerrarSesion()
        registro.registrar("tardio")
        assertEquals(2, registros.size)
        assertEquals(listOf("instalacion"), bajas)
        assertTrue(registro.sesionCerrada)
    }
    @Test fun `logout unlinks installation even when token deletion fails`() = runTest {
        borrado(falla = true)
        val registro = registro()
        try { registro.cerrarSesion(); fail("Debe conservar el fallo para reintentar") }
        catch (_: IllegalStateException) { }
        assertEquals(listOf("instalacion"), bajas)
        assertTrue(registro.sesionCerrada)
    }
    @Test fun `token arriving after logout cannot register the previous session`() = runTest {
        borrado()
        val registro = registro()
        @Suppress("UNCHECKED_CAST")
        val tarea = mock(Task::class.java) as Task<String>
        val llegada = CompletableDeferred<OnSuccessListener<String>>()
        `when`(messaging.token).thenReturn(tarea)
        `when`(tarea.addOnSuccessListener(any())).thenAnswer {
            llegada.complete(it.getArgument(0)); tarea
        }
        `when`(tarea.addOnFailureListener(any())).thenReturn(tarea)
        val consulta = launch { registro.sincronizar() }
        runCurrent()
        registro.cerrarSesion()
        llegada.await().onSuccess("token-viejo")
        consulta.join()
        assertTrue(registros.isEmpty())
        assertEquals(listOf("instalacion"), bajas)
    }
}
