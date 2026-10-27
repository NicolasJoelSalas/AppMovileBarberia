package ar.edu.unaj.am.myapplication

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import ar.edu.unaj.am.myapplication.core.database.AppDatabase
import ar.edu.unaj.am.myapplication.core.database.mapper.toDomain
import ar.edu.unaj.am.myapplication.core.database.model.TurnosEntity
import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.TurnoRepository
import ar.edu.unaj.am.myapplication.core.domain.usecase.FiltrarTurnosPorSemanaUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.ObserveTurnosUseCase
import ar.edu.unaj.am.myapplication.features.turnos.list.TurnoListViewModel
import ar.edu.unaj.am.myapplication.features.turnos.list.TurnosListRoute
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

/** Room real → repositorio sin red → ViewModel → pantalla. No usa la base ni la sesión del usuario. */
class AlmacenamientoOfflineTest {
    @get:Rule val compose = createComposeRule()

    @Test fun clienteConservaTurnosAlFallarLaRedYReabrirLaBase() = comprobar(comoCliente = true)
    @Test fun barberoConservaSoloSusTurnosAlFallarLaRedYReabrirLaBase() = comprobar(comoCliente = false)

    private fun comprobar(comoCliente: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "offline-${java.util.UUID.randomUUID()}.db")
        val inicio = Instant.parse("2026-11-02T13:00:00Z").toEpochMilli()
        val propio = TurnosEntity("propio", "ana", "barbero", "corte", inicio, inicio + 4500000,
            "confirmado", "Cliente visible", "Servicio visible", 100000)
        fun abrir() = Room.databaseBuilder<AppDatabase>(name = file.absolutePath)
            .setDriver(BundledSQLiteDriver()).build()
        var database = abrir()
        val fallos = MutableSharedFlow<AppResult<List<Turno>>>()
        val store = ViewModelStore()
        val reloj = Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"), ZoneId.of("America/Argentina/Buenos_Aires"))
        fun modelo(offline: Boolean): TurnoListViewModel {
            val dao = database.turnoDao()
            val repo = object : TurnoRepository {
                override fun observeTurnos(comoCliente: Boolean) = flow<AppResult<List<Turno>>> {
                    val rows = if (comoCliente) dao.observarPorCliente("ana").first() else dao.observarPorBarbero("barbero").first()
                    emit(AppResult.Success(rows.map { it.toDomain() }))
                    if (offline) emit(AppResult.Error(DomainError.Network))
                    emitAll(fallos)
                }
                override suspend fun marcarRealizado(turnoId: String) = AppResult.Error(DomainError.Network)
            }
            return ViewModelProvider(store, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    TurnoListViewModel(ObserveTurnosUseCase(repo), comoCliente, FiltrarTurnosPorSemanaUseCase(reloj)) as T
            })[TurnoListViewModel::class.java]
        }
        try {
            runBlocking(Dispatchers.IO) {
                database.turnoDao().guardarTodos(listOf(propio, propio.copy(id = "ajeno", clienteId = "otro",
                    barberoId = "otro", clienteNombre = "Cliente ajeno", servicioNombre = "Servicio ajeno")))
            }
            lateinit var inicial: TurnoListViewModel
            compose.runOnIdle { inicial = modelo(offline = false) }
            val visible = mutableStateOf(inicial)
            compose.setContent { MaterialTheme { TurnosListRoute(visible.value) } }
            compose.waitUntil(10000) { compose.onAllNodesWithText("Servicio visible").fetchSemanticsNodes().isNotEmpty() }
            runBlocking { fallos.emit(AppResult.Error(DomainError.Network)) }
            compose.runOnIdle { assertNotNull(inicial.avisoSincronizacion.value) }
            compose.onNodeWithText("Servicio visible").assertIsDisplayed()
            compose.runOnIdle { store.clear() }
            database.close()
            database = abrir()
            compose.runOnIdle { visible.value = modelo(offline = true) }
            compose.waitUntil(10000) { visible.value.avisoSincronizacion.value != null }
            compose.onNodeWithText("Servicio visible").assertIsDisplayed()
            compose.onNodeWithText("Servicio ajeno").assertDoesNotExist()
        } finally {
            compose.runOnIdle { store.clear() }
            database.close()
            context.deleteDatabase(file.absolutePath)
        }
    }
}
