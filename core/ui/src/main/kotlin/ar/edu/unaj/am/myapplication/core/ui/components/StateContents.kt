package ar.edu.unaj.am.myapplication.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ar.edu.unaj.am.myapplication.core.ui.R
import ar.edu.unaj.am.myapplication.core.ui.theme.MyApplicationTheme

// Componentes reutilizables para los estados típicos de una pantalla.
// Antes estaban copiados en cada pantalla; ahora cualquier feature los usa.

@Composable
fun LoadingContent(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * [extraAction] es un "slot": quien usa el componente decide qué botón extra poner.
 * Es el mismo patrón que usan Scaffold (topBar, floatingActionButton…) o Button (content).
 */
@Composable
fun ErrorContent(
    title: String,
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    extraAction: (@Composable () -> Unit)? = null
) {
    MessageContent(
        icon = Icons.Default.Warning,
        title = title,
        message = message,
        isError = true,
        modifier = modifier
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.core_ui_retry))
            }
            extraAction?.invoke()
        }
    }
}

@Composable
fun MessageContent(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    actions: @Composable () -> Unit = {}
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            actions()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoadingContentPreview() {
    MyApplicationTheme { LoadingContent(message = stringResource(R.string.core_ui_cargando)) }
}

@Preview(showBackground = true)
@Composable
private fun ErrorContentPreview() {
    MyApplicationTheme {
        ErrorContent(
            title = stringResource(R.string.core_ui_ups_algo_salio_mal),
            message = stringResource(R.string.core_ui_no_se_pudo_conectar_con_el_servidor),
            onRetry = {}
        )
    }
}
