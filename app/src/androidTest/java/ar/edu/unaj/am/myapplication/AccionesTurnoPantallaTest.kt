package ar.edu.unaj.am.myapplication

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import ar.edu.unaj.am.myapplication.core.domain.model.Turno
import ar.edu.unaj.am.myapplication.core.domain.usecase.EvaluarAccionesTurnoUseCase
import ar.edu.unaj.am.myapplication.features.turnos.list.TurnosListScreen
import ar.edu.unaj.am.myapplication.features.turnos.list.TurnosListUiState
import org.junit.Rule
import org.junit.Test

class AccionesTurnoPantallaTest {
    @get:Rule val compose = createComposeRule()

    @Test fun botonesRespetanLasAccionesDelDominioAntesYDespuesDelInicio() {
        val inicio = 200_000_000L
        val ahora = mutableLongStateOf(inicio-7_200_000L)
        val turno = Turno("t1","cliente","barbero","corte",inicio,inicio+2_400_000L,"pendiente",clienteNombre="Cliente",servicioNombre="Corte")
        val evaluar = EvaluarAccionesTurnoUseCase()
        var modificar = ""
        var completar = ""
        compose.setContent {
            modificar = stringResource(ar.edu.unaj.am.myapplication.features.turnos.R.string.features_turnos_modificar_turno)
            completar = stringResource(ar.edu.unaj.am.myapplication.features.turnos.R.string.features_turnos_marcar_realizado)
            MaterialTheme {
                TurnosListScreen(TurnosListUiState.Success(listOf(turno)),onRetry={},
                    ahoraMillis=ahora.longValue,
                    accionesTurnos=mapOf(turno.id to evaluar(turno,ahora.longValue)),
                    onModificar={},onMarcarRealizado={})
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText(modificar).performScrollTo().assertIsEnabled()
        compose.onNodeWithText(completar).performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { ahora.longValue=inicio }
        compose.onNodeWithText(modificar).assertDoesNotExist()
        compose.onNodeWithText(completar).performScrollTo().assertIsEnabled()
    }
}
