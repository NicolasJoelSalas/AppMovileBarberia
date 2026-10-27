package ar.edu.unaj.am.myapplication.notifications

import ar.edu.unaj.am.myapplication.core.domain.model.Turno
import ar.edu.unaj.am.myapplication.core.domain.repository.*
import ar.edu.unaj.am.myapplication.core.domain.usecase.*
import ar.edu.unaj.am.myapplication.features.notifications.PresentadorNotificaciones
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AvisosTurnosTest {
    private class Estado : EstadoAvisosRepository {
        override var usuarioId: String? = null
        override var comoCliente = false
        val firmas = mutableMapOf<String, Map<String,List<String?>>>()
        override fun iniciar(uid: String, comoCliente: Boolean) { usuarioId=uid; this.comoCliente=comoCliente }
        override fun cerrar() { usuarioId=null }
        override fun obtenerFirmas(uid: String) = firmas[uid]
        override fun guardarFirmas(uid: String, firmas: Map<String,List<String?>>) { this.firmas[uid]=firmas }
    }
    private val turno = Turno("t1","cliente","barbero","corte",100,200,"pendiente")
    private val estado = Estado()
    private val programador = mock(ProgramadorAvisosTurnos::class.java)
    private val presentador = mock(PresentadorNotificaciones::class.java)
    private var usuario: String? = "barbero"
    private var turnos = listOf(turno)
    private val repo = object : NovedadesTurnosRepository {
        override suspend fun consultar(uid: String, comoCliente: Boolean) = turnos
        override fun observar(uid: String, comoCliente: Boolean): Flow<List<Turno>> = emptyFlow()
    }
    private fun caso(dispatcher: TestDispatcher) = AvisosTurnos(estado,programador,presentador,
        ConsultarNovedadesTurnosUseCase(repo),ObservarNovedadesTurnosUseCase(repo),{ usuario },dispatcher)

    @Test fun `la primera lectura establece la base sin notificar historicos`() = runTest {
        val avisos=caso(StandardTestDispatcher(testScheduler))
        avisos.iniciar("barbero"); avisos.consultar("barbero")
        assertEquals(mapOf(turno.id to firmaTurno(turno)), estado.firmas["barbero"])
        verifyNoInteractions(presentador)
        avisos.detener(); runCurrent()
    }
    @Test fun `el cliente recibe cancelaciones del barbero`() = runTest {
        usuario="cliente"
        val avisos=caso(StandardTestDispatcher(testScheduler))
        avisos.iniciar("cliente",true); avisos.consultar("cliente")
        `when`(presentador.tienePermiso()).thenReturn(true)
        turnos=listOf(turno.copy(estado="cancelado",canceladoPor="barbero"))
        avisos.consultar("cliente"); avisos.consultar("cliente")
        verify(presentador,times(1)).mostrar("t1","cancelacion_cliente")
        avisos.detener(); runCurrent()
    }
    @Test fun `sin permiso actualiza la base para no emitir avisos antiguos al habilitarlo`() = runTest {
        val avisos=caso(StandardTestDispatcher(testScheduler))
        avisos.iniciar("barbero"); avisos.consultar("barbero")
        turnos=listOf(turno.copy(inicioMillis=300,finMillis=400))
        avisos.consultar("barbero")
        `when`(presentador.tienePermiso()).thenReturn(true)
        avisos.consultar("barbero")
        verify(presentador,never()).mostrar(anyString(),anyString())
        avisos.detener(); runCurrent()
    }
    @Test fun `una sesion cerrada no guarda ni avisa cambios posteriores`() = runTest {
        val avisos=caso(StandardTestDispatcher(testScheduler))
        avisos.iniciar("barbero"); avisos.consultar("barbero")
        val antes=estado.firmas.toMap()
        avisos.detener(); usuario="otro"; turnos=emptyList()
        avisos.consultar("barbero")
        assertFalse(avisos.sesionValida("barbero"))
        assertEquals(antes,estado.firmas)
        verify(programador).detener(); verify(presentador).cancelarTodas()
        runCurrent()
    }
}
