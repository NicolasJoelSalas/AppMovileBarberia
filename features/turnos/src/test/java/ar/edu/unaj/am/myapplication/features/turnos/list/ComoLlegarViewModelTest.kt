package ar.edu.unaj.am.myapplication.features.turnos.list

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.*
import ar.edu.unaj.am.myapplication.core.domain.usecase.*
import ar.edu.unaj.am.myapplication.core.testing.MainDispatcherRule
import ar.edu.unaj.am.myapplication.features.turnos.ubicacion.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ComoLlegarViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    @Test fun `usa el destino del barbero del turno y cancela la ruta anterior`() = runTest(dispatcher) {
        val repo = FakeUbicacion()
        val rutas = object : RutaRepository {
            override suspend fun calcular(origen: Coordenadas, destino: Coordenadas): AppResult<RutaBarberia> {
                delay(1000)
                return AppResult.Success(RutaBarberia(listOf(origen, destino), 100.0, 60.0))
            }
        }
        val vm = ComoLlegarViewModel("barbero-2", ObtenerUbicacionBarberiaUseCase(repo), CalcularRutaBarberiaUseCase(rutas))
        advanceUntilIdle()
        assertEquals("barbero-2", repo.consultado)
        vm.origen(Coordenadas(-34.0, -58.0))
        advanceTimeBy(500)
        val nuevoOrigen = Coordenadas(-34.1, -58.1)
        vm.origen(nuevoOrigen)
        advanceUntilIdle()
        assertEquals(listOf(nuevoOrigen, repo.destino), vm.uiState.value.ruta?.puntos)
        assertFalse(vm.uiState.value.calculando)
    }

    @Test fun `sin ubicacion del barbero no calcula una ruta inventada`() = runTest(dispatcher) {
        val repo = FakeUbicacion().apply { destino = null }
        var consultas = 0
        val rutas = object : RutaRepository {
            override suspend fun calcular(origen: Coordenadas, destino: Coordenadas): AppResult<RutaBarberia> {
                consultas++
                return AppResult.Error(DomainError.Network)
            }
        }
        val vm = ComoLlegarViewModel("barbero", ObtenerUbicacionBarberiaUseCase(repo), CalcularRutaBarberiaUseCase(rutas))
        advanceUntilIdle()
        vm.origen(Coordenadas(0.0, 0.0))
        advanceUntilIdle()
        assertEquals(0, consultas)
        assertNull(vm.uiState.value.destino)
        assertFalse(vm.uiState.value.cargando)
    }

    private class FakeUbicacion : UbicacionBarberiaRepository {
        var consultado: String? = null
        var destino: Coordenadas? = Coordenadas(-34.8, -58.3)
        override suspend fun obtenerMiUbicacion() = AppResult.Success(destino)
        override suspend fun obtenerUbicacion(barberoId: String): AppResult<Coordenadas?> {
            consultado = barberoId
            return AppResult.Success(destino)
        }
        override suspend fun guardarMiUbicacion(ubicacion: Coordenadas) = AppResult.Success(Unit)
    }

    private class RutasSeguimiento : RutaRepository {
        var consultas = 0
        var demora = 0L
        var falla = false
        override suspend fun calcular(origen: Coordenadas, destino: Coordenadas): AppResult<RutaBarberia> {
            consultas++
            delay(demora)
            return if (falla) AppResult.Error(DomainError.Network)
            else AppResult.Success(RutaBarberia(listOf(origen, destino), 2000.0, 200.0))
        }
    }

    private fun TestScope.modelo(rutas: RutasSeguimiento): ComoLlegarViewModel = ComoLlegarViewModel(
        "barbero", ObtenerUbicacionBarberiaUseCase(FakeUbicacion().apply { destino = Coordenadas(0.0, 0.02) }),
        CalcularRutaBarberiaUseCase(rutas), reloj = { testScheduler.currentTime })

    @Test fun `avanzar por la ruta actualiza punto y distancia sin consultar otra vez`() = runTest(dispatcher) {
        val rutas = RutasSeguimiento()
        val vm = modelo(rutas)
        advanceUntilIdle()
        vm.iniciarSeguimiento(Coordenadas(0.0, 0.0), 10f, 0)
        advanceUntilIdle()
        advanceTimeBy(5_000)
        vm.actualizarPosicion(Coordenadas(0.0, 0.01), 10f, testScheduler.currentTime)
        advanceUntilIdle()
        assertEquals(1, rutas.consultas)
        assertEquals(Coordenadas(0.0, 0.01), vm.uiState.value.origen)
        assertEquals(1000.0, vm.uiState.value.distanciaRestanteMetros!!, 0.01)
    }

    @Test fun `confirma desvio y respeta minimo de diez segundos entre consultas`() = runTest(dispatcher) {
        val rutas = RutasSeguimiento()
        val vm = modelo(rutas)
        advanceUntilIdle()
        vm.iniciarSeguimiento(Coordenadas(0.0, 0.0), 10f, 0)
        advanceUntilIdle()
        advanceTimeBy(3_000)
        vm.actualizarPosicion(Coordenadas(0.001, 0.005), 10f, testScheduler.currentTime)
        advanceTimeBy(3_000)
        vm.actualizarPosicion(Coordenadas(0.001, 0.006), 10f, testScheduler.currentTime)
        advanceUntilIdle()
        assertEquals(1, rutas.consultas)
        advanceTimeBy(4_000)
        vm.actualizarPosicion(Coordenadas(0.001, 0.007), 10f, testScheduler.currentTime)
        advanceUntilIdle()
        assertEquals(2, rutas.consultas)
    }

    @Test fun `lecturas imprecisas duplicadas y un desvio aislado no recalculan`() = runTest(dispatcher) {
        val rutas = RutasSeguimiento()
        val vm = modelo(rutas)
        advanceUntilIdle()
        vm.iniciarSeguimiento(Coordenadas(0.0, 0.0), 10f, 0)
        advanceUntilIdle()
        advanceTimeBy(20_000)
        vm.actualizarPosicion(Coordenadas(0.01, 0.01), 500f, testScheduler.currentTime)
        advanceTimeBy(5_000)
        vm.actualizarPosicion(Coordenadas(0.001, 0.005), 10f, testScheduler.currentTime)
        vm.actualizarPosicion(Coordenadas(0.001, 0.005), 10f, testScheduler.currentTime)
        advanceTimeBy(5_000)
        vm.actualizarPosicion(Coordenadas(0.0, 0.006), 10f, testScheduler.currentTime)
        advanceUntilIdle()
        assertEquals(1, rutas.consultas)
    }

    @Test fun `conserva ruta durante el recalculo y ante falta de conexion`() = runTest(dispatcher) {
        val rutas = RutasSeguimiento()
        val vm = modelo(rutas)
        advanceUntilIdle()
        vm.iniciarSeguimiento(Coordenadas(0.0, 0.0), 10f, 0)
        advanceUntilIdle()
        val anterior = vm.uiState.value.ruta
        rutas.demora = 3_000
        rutas.falla = true
        advanceTimeBy(20_000)
        vm.actualizarPosicion(Coordenadas(0.001, 0.005), 10f, testScheduler.currentTime)
        advanceTimeBy(5_000)
        vm.actualizarPosicion(Coordenadas(0.001, 0.006), 10f, testScheduler.currentTime)
        advanceTimeBy(500)
        assertTrue(vm.uiState.value.calculando)
        assertEquals(anterior, vm.uiState.value.ruta)
        advanceUntilIdle()
        assertEquals(anterior, vm.uiState.value.ruta)
        assertEquals(DomainError.Network, vm.uiState.value.error)
    }

    @Test fun `elegir origen manual desactiva seguimiento e ignora GPS pendiente`() = runTest(dispatcher) {
        val rutas = RutasSeguimiento()
        val vm = modelo(rutas)
        advanceUntilIdle()
        vm.iniciarSeguimiento(Coordenadas(0.0, 0.0), 10f, 0)
        advanceUntilIdle()
        val manual = Coordenadas(0.005, 0.005)
        vm.origen(manual)
        vm.actualizarPosicion(Coordenadas(0.0, 0.01), 10f, 5_000)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.siguiendoUbicacion)
        assertEquals(manual, vm.uiState.value.origen)
        assertEquals(manual, vm.uiState.value.ruta!!.puntos.first())
    }

    @Test fun `salir de pantalla cancela consulta pendiente y conserva ruta`() = runTest(dispatcher) {
        val rutas = RutasSeguimiento()
        val vm = modelo(rutas)
        advanceUntilIdle()
        vm.iniciarSeguimiento(Coordenadas(0.0, 0.0), 10f, 0)
        advanceUntilIdle()
        val anterior = vm.uiState.value.ruta
        rutas.demora = 10_000
        vm.calcularRuta()
        advanceTimeBy(500)
        vm.suspenderConsulta()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.calculando)
        assertEquals(anterior, vm.uiState.value.ruta)
    }

    @Test fun `volver a pantalla reanuda una consulta manual interrumpida`() = runTest(dispatcher) {
        val rutas = RutasSeguimiento().apply { demora = 10_000 }
        val vm = modelo(rutas)
        advanceUntilIdle()
        vm.origen(Coordenadas(0.0, 0.0))
        advanceTimeBy(500)
        vm.suspenderConsulta()
        advanceUntilIdle()
        assertNull(vm.uiState.value.ruta)
        vm.reanudarConsulta()
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.ruta)
        assertFalse(vm.uiState.value.calculando)
        assertEquals(2, rutas.consultas)
    }
}
