package ar.edu.unaj.am.myapplication.notifications

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class TurnosWorkerTest {
    private val context = mock(Context::class.java, withSettings().extraInterfaces(TurnosWorkerDependencies::class.java))
    private val parametros = mock(WorkerParameters::class.java)
    private val avisos = mock(AvisosTurnos::class.java)

    private fun worker(uid: String? = "ana"): TurnosWorker {
        `when`(context.applicationContext).thenReturn(context)
        `when`((context as TurnosWorkerDependencies).avisosTurnos).thenReturn(avisos)
        val datos = if (uid == null) Data.EMPTY else Data.Builder().putString("uid", uid).build()
        `when`(parametros.inputData).thenReturn(datos)
        return TurnosWorker(context, parametros)
    }
    @Test fun `missing account fails and closed session does not query another user`() = runTest {
        assertEquals(ListenableWorker.Result.failure(), worker(null).doWork())
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        verify(avisos, never()).consultar("ana")
    }
    @Test fun `active session queries changes successfully`() = runTest {
        `when`(avisos.sesionValida("ana")).thenReturn(true)
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        verify(avisos).consultar("ana")
    }
    @Test fun `network error retries instead of dropping the background job`() = runTest {
        `when`(avisos.sesionValida("ana")).thenReturn(true)
        doAnswer { throw IOException("Sin red") }.`when`(avisos).consultar("ana")
        mockStatic(Log::class.java).use {
            assertEquals(ListenableWorker.Result.retry(), worker().doWork())
        }
    }
    @Test fun `cancellation propagates when worker is stopped`() = runTest {
        `when`(avisos.sesionValida("ana")).thenReturn(true)
        doAnswer { throw CancellationException("Sesión cerrada") }.`when`(avisos).consultar("ana")
        try { worker().doWork(); fail("La cancelación no debe reintentarse") }
        catch (_: CancellationException) { }
    }
}
