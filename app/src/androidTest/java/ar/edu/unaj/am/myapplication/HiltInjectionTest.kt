package ar.edu.unaj.am.myapplication

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.core.app.ActivityScenario
import ar.edu.unaj.am.myapplication.features.cuenta.ReporteGananciasViewModel
import ar.edu.unaj.am.myapplication.features.turnos.ubicacion.ComoLlegarViewModel
import ar.edu.unaj.am.myapplication.ui.AuthenticatedSessionViewModel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HiltInjectionTest {
    @Test
    fun sessionViewModelsSurviveRecreationAndAreReplacedAfterSessionClear() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val initial = renderSession(scenario, epoch = 0)
            scenario.recreate()
            val recreated = renderSession(scenario, epoch = 0)
            assertSame(initial.first, recreated.first)
            assertSame(initial.second, recreated.second)

            scenario.onActivity { activity ->
                ViewModelProvider(activity)[AuthenticatedSessionViewModel::class.java].clearSession()
            }
            val nextSession = renderSession(scenario, epoch = 1)
            assertNotSame(initial.first, nextSession.first)
            assertNotSame(initial.second, nextSession.second)
        }
    }

    private fun renderSession(
        scenario: ActivityScenario<MainActivity>,
        epoch: Int
    ): Pair<ReporteGananciasViewModel, ComoLlegarViewModel> {
        val ready = CountDownLatch(1)
        val models = AtomicReference<Pair<ReporteGananciasViewModel, ComoLlegarViewModel>>()
        scenario.onActivity { activity ->
            val owner = ViewModelProvider(activity)[AuthenticatedSessionViewModel::class.java]
            activity.setContent {
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                    key(epoch) {
                        // Reportes no consulta hasta llamar a cargar(). Un identificador vacío
                        // de ubicación se valida en dominio sin consultar servicios externos.
                        val report: ReporteGananciasViewModel = hiltViewModel()
                        val location = hiltViewModel<ComoLlegarViewModel, ComoLlegarViewModel.Factory>(
                            creationCallback = { factory -> factory.create("") }
                        )
                        SideEffect {
                            models.set(report to location)
                            ready.countDown()
                        }
                    }
                }
            }
        }
        assertTrue("Hilt no pudo crear los ViewModels de sesión", ready.await(10, TimeUnit.SECONDS))
        return models.get()
    }
}
