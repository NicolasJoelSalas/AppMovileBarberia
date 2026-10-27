package ar.edu.unaj.am.myapplication.core.ui.maps

import androidx.compose.runtime.staticCompositionLocalOf
import ar.edu.unaj.am.myapplication.core.domain.repository.UbicacionDispositivoRepository

/** La aplicación provee el adaptador; los componentes compartidos solo conocen el contrato. */
val LocalUbicacionDispositivo = staticCompositionLocalOf<UbicacionDispositivoRepository> {
    error("Falta proveer UbicacionDispositivoRepository")
}
