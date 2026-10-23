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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ContentCut
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
        TopAppBar(title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (esBarbero) "Publicaciones" else "Inicio", fontWeight = FontWeight.Bold)
            }
        },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer))
    }, bottomBar = {
        if (esBarbero) Surface(color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp) {
            Button(onClick = viewModel::nueva, enabled = !state.eliminando && state.modificando == null,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
                    .heightIn(min = 50.dp), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Agregar trabajo", fontWeight = FontWeight.SemiBold)
            }
        }
    }) { padding ->
        when {
            state.cargando -> LoadingContent(message = "Cargando trabajos…", modifier = Modifier.padding(padding))
            state.error != null && state.publicaciones.isEmpty() -> ErrorContent(title = "No se pudieron cargar los trabajos",
                message = state.error!!.asMessage(), onRetry = viewModel::reintentar, modifier = Modifier.padding(padding))
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)) {
                state.error?.let { error ->
                    item {
                        Column {
                            Text("Mostrando los trabajos guardados. ${error.asMessage()}")
                            TextButton(onClick = viewModel::reintentar) { Text("Actualizar") }
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Surface(shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                                Icon(Icons.Outlined.ContentCut, contentDescription = null,
                                    modifier = Modifier.padding(12.dp).size(24.dp))
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("NUESTRA BARBERÍA", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Cortes y estilos", style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(if (esBarbero) "Mostrá tu trabajo y compartí tus últimos cortes con tus clientes."
                            else "Descubrí el trabajo de nuestros barberos y elegí tu próximo corte.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        state.mensaje?.let {
                            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Text(it, modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }
                if (state.publicaciones.isEmpty()) item {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.fillMaxWidth().padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.primary)
                            Text("Todavía no hay publicaciones", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold)
                            Text(if (esBarbero) "Agregá una foto para empezar a mostrar tu trabajo."
                                else "Pronto podrás conocer los cortes y estilos de nuestros barberos.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(state.publicaciones, key = { it.id }) { publicacion ->
                    TrabajoCard(publicacion, puedeEliminar = esBarbero && publicacion.esPropia,
                        eliminando = state.eliminando, onEliminar = { borrar = publicacion },
                        onEditar = { viewModel.editarDescripcion(publicacion) },
                        onReservar = if (esBarbero) null else { { onReservar(publicacion.barberoId) } })
                }
            }
        }
    }
    if (esBarbero && state.modificando != null) AlertDialog(
        onDismissRequest = viewModel::cerrarDescripcion,
        title = { Text("Modificar descripción") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.descripcionModificada,
                    onValueChange = viewModel::cambiarDescripcion,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.guardandoDescripcion,
                    label = { Text("Descripción (opcional)") },
                    minLines = 3, maxLines = 6,
                    supportingText = { Text("${state.descripcionModificada.length}/300") }
                )
                state.errorDescripcion?.let { Text(it.asMessage(), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::guardarDescripcion,
                enabled = !state.guardandoDescripcion &&
                    state.descripcionModificada.trim() != state.modificando?.descripcion) {
                Text(if (state.guardandoDescripcion) "Guardando…" else "Guardar cambios")
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::cerrarDescripcion, enabled = !state.guardandoDescripcion) {
                Text("Cancelar")
            }
        }
    )
    borrar?.let { publicacion -> AlertDialog(
        onDismissRequest = { borrar = null }, title = { Text("¿Eliminar publicación?") },
        text = { Text("Este trabajo dejará de aparecer en las publicaciones.") },
        confirmButton = { TextButton(onClick = { viewModel.eliminar(publicacion); borrar = null }) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = { borrar = null }) { Text("Cancelar") } }) }
}

@Composable
private fun TrabajoCard(publicacion: Publicacion, puedeEliminar: Boolean, eliminando: Boolean,
    onEliminar: () -> Unit, onEditar: () -> Unit, onReservar: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = colors.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        FotoTrabajo(publicacion.imagenUrl, "Trabajo de ${publicacion.barberoNombre}", Modifier.fillMaxWidth().aspectRatio(1f),
            rutaLocal = publicacion.imagenLocalPath)
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = CircleShape, color = colors.primaryContainer,
                    contentColor = colors.onPrimaryContainer) {
                    Icon(Icons.Outlined.Person, contentDescription = null,
                        modifier = Modifier.padding(10.dp).size(22.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Barbero", style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant)
                    Text(publicacion.barberoNombre.ifBlank { "Sin nombre" },
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            if (publicacion.descripcion.isNotBlank()) Text(publicacion.descripcion,
                style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            publicacion.creadoEnMillis?.let {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.CalendarToday, contentDescription = null,
                        tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Text(Instant.ofEpochMilli(it).atZone(ZoneId.of("America/Argentina/Buenos_Aires"))
                        .format(DateTimeFormatter.ofPattern("d 'de' MMMM yyyy", Locale.forLanguageTag("es-AR"))),
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            if (onReservar != null || puedeEliminar) HorizontalDivider(color = colors.outlineVariant)
            onReservar?.let { reservar ->
                ReservarTurnoButton(onClick = reservar, modifier = Modifier.fillMaxWidth())
            }
            if (puedeEliminar) OutlinedButton(onClick = onEditar, enabled = !eliminando,
                modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Modificar descripción")
            }
            if (puedeEliminar) TextButton(onClick = onEliminar, enabled = !eliminando,
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.textButtonColors(contentColor = colors.error)) {
                if (eliminando) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (eliminando) "Eliminando…" else "Eliminar publicación")
            }
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
private fun FotoTrabajo(model: String, descripcion: String, modifier: Modifier, alignment: Alignment = Alignment.Center,
    rutaLocal: String? = null) {
    val imagen = rutaLocal?.let(::File)?.takeIf { it.isFile } ?: model.ifBlank { null }
    SubcomposeAsyncImage(model = imagen, contentDescription = descripcion,
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
