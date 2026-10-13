package ar.edu.unaj.am.myapplication.features.home

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.unaj.am.myapplication.core.domain.model.Publicacion
import ar.edu.unaj.am.myapplication.core.ui.components.*
import coil3.compose.SubcomposeAsyncImage
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
//Comentario de prueba
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeRoute(viewModel: HomeViewModel, esBarbero: Boolean, onReservar: (String) -> Unit,
    onEditorVisible: (Boolean) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var borrar by remember { mutableStateOf<Publicacion?>(null) }
    LaunchedEffect(state.editando, esBarbero) { onEditorVisible(state.editando && esBarbero) }
    DisposableEffect(Unit) { onDispose { onEditorVisible(false) } }
    if (state.editando && esBarbero) {
        EditorTrabajo(state, viewModel)
        return
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Inicio", fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer))
    }, floatingActionButton = {
        if (esBarbero) ExtendedFloatingActionButton(onClick = viewModel::nueva,
            icon = { Icon(Icons.Default.Add, contentDescription = null) }, text = { Text("Agregar trabajo") })
    }) { padding ->
        when {
            state.cargando -> LoadingContent(message = "Cargando trabajos…", modifier = Modifier.padding(padding))
            state.error != null -> ErrorContent(title = "No se pudieron cargar los trabajos",
                message = state.error!!.asMessage(), onRetry = viewModel::reintentar, modifier = Modifier.padding(padding))
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Text("Trabajos de nuestra barbería", style = MaterialTheme.typography.titleLarge)
                    Text(if (esBarbero) "Compartí tus cortes y estilos con tus clientes."
                        else "Encontrá tu próximo estilo y reservá tu turno.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.mensaje?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                }
                if (state.publicaciones.isEmpty()) item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Text(if (esBarbero) "Todavía no hay trabajos publicados. Agregá tu primera foto."
                            else "Pronto vas a encontrar fotos de los trabajos de nuestros barberos.",
                            modifier = Modifier.padding(24.dp))
                    }
                }
                items(state.publicaciones, key = { it.id }) { publicacion ->
                    TrabajoCard(publicacion, puedeEliminar = esBarbero && publicacion.esPropia,
                        eliminando = state.eliminando, onEliminar = { borrar = publicacion },
                        onReservar = if (esBarbero) null else { { onReservar(publicacion.barberoId) } })
                }
            }
        }
    }
    borrar?.let { publicacion -> AlertDialog(
        onDismissRequest = { borrar = null }, title = { Text("¿Eliminar publicación?") },
        text = { Text("Este trabajo dejará de aparecer en Inicio.") },
        confirmButton = { TextButton(onClick = { viewModel.eliminar(publicacion); borrar = null }) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = { borrar = null }) { Text("Cancelar") } }) }
}

@Composable
private fun TrabajoCard(publicacion: Publicacion, puedeEliminar: Boolean, eliminando: Boolean,
    onEliminar: () -> Unit, onReservar: (() -> Unit)?) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        FotoTrabajo(publicacion.imagenUrl, "Trabajo de ${publicacion.barberoNombre}", Modifier.fillMaxWidth().aspectRatio(1f))
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(publicacion.barberoNombre.ifBlank { "Barbero" }, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            if (publicacion.descripcion.isNotBlank()) Text(publicacion.descripcion)
            publicacion.creadoEnMillis?.let {
                Text(Instant.ofEpochMilli(it).atZone(ZoneId.of("America/Argentina/Buenos_Aires"))
                    .format(DateTimeFormatter.ofPattern("d 'de' MMMM yyyy", Locale.forLanguageTag("es-AR"))),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            onReservar?.let { OutlinedButton(onClick = it, modifier = Modifier.fillMaxWidth()) { Text("Reservar turno") } }
            if (puedeEliminar) TextButton(onClick = onEliminar, enabled = !eliminando,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Eliminar publicación") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTrabajo(state: HomeUiState, vm: HomeViewModel) {
    val context = LocalContext.current
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmarSalida by rememberSaveable { mutableStateOf(false) }
    val salir = {
        if (!state.publicando) {
            if (state.imagen.isNotBlank() || state.descripcion.isNotBlank()) confirmarSalida = true
            else vm.cerrarEditor()
        }
    }
    BackHandler(onBack = salir)
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = cameraUri
        if (success && uri != null) vm.imagen(uri)
        else if (uri != null) runCatching { context.contentResolver.delete(Uri.parse(uri), null, null) }
        cameraUri = null
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.imagen(uri.toString())
        }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Agregar trabajo", fontWeight = FontWeight.Bold) },
            actions = { TextButton(onClick = salir, enabled = !state.publicando) { Text("Volver") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer))
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {
                        runCatching {
                            val folder = File(context.cacheDir, "trabajos").apply { mkdirs() }
                            // Solo borra capturas temporales de más de un día dentro de esta carpeta.
                            folder.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 86400000 }
                                ?.forEach { it.delete() }
                            val file = File.createTempFile("trabajo-", ".jpg", folder)
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.trabajos.files", file)
                            cameraUri = uri.toString()
                            camera.launch(uri)
                        }.onFailure { vm.errorFoto("No se pudo abrir la cámara. Podés elegir una foto de la galería.") }
                    }, enabled = !state.publicando, modifier = Modifier.weight(1f)) { Text("Cámara") }
                    OutlinedButton(onClick = {
                        runCatching { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                            .onFailure { vm.errorFoto("No se pudo abrir la galería.") }
                    }, enabled = !state.publicando, modifier = Modifier.weight(1f)) { Text("Galería") }
                }
            }
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    FotoTrabajo(state.imagen, "Vista previa del trabajo", Modifier.fillMaxWidth().aspectRatio(1f),
                        BiasAlignment(state.recorteX, state.recorteY))
                }
            }
            if (state.imagen.isNotBlank()) item {
                Text("Ajustar encuadre", style = MaterialTheme.typography.titleMedium)
                Text("Todas las fotos se publican en formato cuadrado.", style = MaterialTheme.typography.bodySmall)
                Text("Horizontal", style = MaterialTheme.typography.labelMedium)
                Slider(value = state.recorteX, onValueChange = { vm.encuadre(it, state.recorteY) },
                    valueRange = -1f..1f, enabled = !state.publicando)
                Text("Vertical", style = MaterialTheme.typography.labelMedium)
                Slider(value = state.recorteY, onValueChange = { vm.encuadre(state.recorteX, it) },
                    valueRange = -1f..1f, enabled = !state.publicando)
            }
            item {
                OutlinedTextField(value = state.descripcion, onValueChange = vm::descripcion,
                    label = { Text("Descripción (opcional)") }, supportingText = { Text("${state.descripcion.length}/300") },
                    enabled = !state.publicando, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
            }
            state.errorEdicion?.let { error -> item { Text(error.asMessage(), color = MaterialTheme.colorScheme.error) } }
            item {
                Button(onClick = vm::confirmar, enabled = !state.publicando && state.imagen.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    if (state.publicando) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(if (state.publicando) "Publicando…" else "Publicar trabajo")
                }
            }
        }
    }
    if (confirmarSalida) AlertDialog(onDismissRequest = { confirmarSalida = false },
        title = { Text("¿Descartar publicación?") }, text = { Text("La foto y la descripción todavía no se publicaron.") },
        confirmButton = { TextButton(onClick = { confirmarSalida = false; vm.cerrarEditor() }) { Text("Descartar") } },
        dismissButton = { TextButton(onClick = { confirmarSalida = false }) { Text("Seguir editando") } })
}

@Composable
private fun FotoTrabajo(model: String, descripcion: String, modifier: Modifier, alignment: Alignment = Alignment.Center) {
    SubcomposeAsyncImage(model = model.ifBlank { null }, contentDescription = descripcion,
        modifier = modifier, contentScale = ContentScale.Crop, alignment = alignment,
        loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
        error = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(48.dp))
                    Text(if (model.isBlank()) "Elegí una foto" else "No se pudo mostrar la foto")
                }
            }
        })
}
