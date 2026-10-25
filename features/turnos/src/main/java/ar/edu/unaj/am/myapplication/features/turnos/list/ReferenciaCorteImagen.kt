package ar.edu.unaj.am.myapplication.features.turnos.list

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ar.edu.unaj.am.myapplication.features.turnos.R
import coil3.compose.SubcomposeAsyncImage

@Composable
fun ReferenciaCorteImagen(imagen: String) {
    SubcomposeAsyncImage(model = imagen,
        contentDescription = stringResource(R.string.turnos_reference_title),
        modifier = Modifier.fillMaxWidth().height(240.dp), contentScale = ContentScale.Fit,
        error = { Text(stringResource(R.string.turnos_reference_error)) })
}
