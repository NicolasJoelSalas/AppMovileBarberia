package ar.edu.unaj.am.myapplication.features.turnos.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ar.edu.unaj.am.myapplication.features.turnos.R
import coil3.compose.SubcomposeAsyncImage

@Composable
fun ReferenciaCorteImagen(imagen: String) {
    var ampliada by rememberSaveable(imagen) { mutableStateOf(false) }
    Column {
        SubcomposeAsyncImage(model = imagen,
            contentDescription = stringResource(R.string.turnos_reference_open),
            modifier = Modifier.fillMaxWidth().height(240.dp).clickable(
                onClickLabel = stringResource(R.string.turnos_reference_open)
            ) { ampliada = true },
            contentScale = ContentScale.Fit,
            error = { Text(stringResource(R.string.turnos_reference_error)) })
        TextButton(onClick = { ampliada = true }) {
            Icon(Icons.Outlined.ZoomIn, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.turnos_reference_open))
        }
    }
    if (ampliada) {
        ReferenciaAmpliada(imagen, onCerrar = { ampliada = false })
    }
}

@Composable
private fun ReferenciaAmpliada(imagen: String, onCerrar: () -> Unit) {
    var escala by remember(imagen) { mutableFloatStateOf(1f) }
    var desplazamiento by remember(imagen) { mutableStateOf(Offset.Zero) }
    var tamano by remember { mutableStateOf(IntSize.Zero) }
    Dialog(onDismissRequest = onCerrar,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.turnos_reference_title),
                        modifier = Modifier.weight(1f).padding(8.dp),
                        style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onCerrar) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.turnos_reference_close))
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()
                    .onSizeChanged { tamano = it }
                    .pointerInput(imagen) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            escala = (escala * zoom).coerceIn(1f, 5f)
                            val limiteX = tamano.width * (escala - 1f) / 2f
                            val limiteY = tamano.height * (escala - 1f) / 2f
                            desplazamiento = Offset(
                                (desplazamiento.x + pan.x).coerceIn(-limiteX, limiteX),
                                (desplazamiento.y + pan.y).coerceIn(-limiteY, limiteY))
                        }
                    }, contentAlignment = Alignment.Center) {
                    SubcomposeAsyncImage(model = imagen,
                        contentDescription = stringResource(R.string.turnos_reference_title),
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = escala
                            scaleY = escala
                            translationX = desplazamiento.x
                            translationY = desplazamiento.y
                        }, contentScale = ContentScale.Fit,
                        error = { Text(stringResource(R.string.turnos_reference_error)) })
                }
                Text(stringResource(R.string.turnos_reference_zoom_hint),
                    modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { escala = 1f; desplazamiento = Offset.Zero },
                    modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.turnos_reference_reset))
                }
            }
        }
    }
}
