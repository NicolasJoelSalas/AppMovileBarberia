package ar.edu.unaj.am.myapplication.features.home

import ar.edu.unaj.am.myapplication.features.home.R

import androidx.compose.ui.res.stringResource

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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.unaj.am.myapplication.core.domain.model.Publicacion
import ar.edu.unaj.am.myapplication.core.ui.components.*
import coil3.compose.SubcomposeAsyncImage   
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
                Text(if (esBarbero) stringResource(R.string.features_home_publicaciones) else stringResource(R.string.features_home_inicio), fontWeight = FontWeight.Bold)
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
                Text(stringResource(R.string.features_home_agregar_trabajo), fontWeight = FontWeight.SemiBold)
            }
        }
    }) { padding ->
        when {
            state.cargando -> LoadingContent(message = stringResource(R.string.features_home_cargando_trabajos), modifier = Modifier.padding(padding))
            state.error != null && state.publicaciones.isEmpty() -> ErrorContent(title = stringResource(R.string.features_home_no_se_pudieron_cargar_los_trabajos),
                message = state.error!!.asMessage(), onRetry = viewModel::reintentar, modifier = Modifier.padding(padding))
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)) {
                state.error?.let { error ->
                    item {
                        Column {
                            Text(stringResource(R.string.home_cached, error.asMessage()))
                            TextButton(onClick = viewModel::reintentar) { Text(stringResource(R.string.features_home_actualizar)) }
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
                                Text(stringResource(R.string.features_home_nuestra_barberia), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.features_home_cortes_y_estilos), style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(if (esBarbero) stringResource(R.string.features_home_mostra_tu_trabajo_y_comparti_tus_ultimos_cortes_con_tus_clientes)
                            else stringResource(R.string.features_home_descubri_el_trabajo_de_nuestros_barberos_y_elegi_tu_proximo_corte),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        state.mensaje?.let {
                            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Text(it.asString(), modifier = Modifier.fillMaxWidth().padding(12.dp),
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
                            Text(stringResource(R.string.features_home_todavia_no_hay_publicaciones), style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold)
                            Text(if (esBarbero) stringResource(R.string.features_home_agrega_una_foto_para_empezar_a_mostrar_tu_trabajo)
                                else stringResource(R.string.features_home_pronto_podras_conocer_los_cortes_y_estilos_de_nuestros_barberos),
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
        title = { Text(stringResource(R.string.features_home_modificar_descripcion)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.descripcionModificada,
                    onValueChange = viewModel::cambiarDescripcion,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.guardandoDescripcion,
                    label = { Text(stringResource(R.string.features_home_descripcion_opcional)) },
                    minLines = 3, maxLines = 6,
                    supportingText = { Text(stringResource(R.string.home_description_count, state.descripcionModificada.length)) }
                )
                state.errorDescripcion?.let { Text(it.asMessage(), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::guardarDescripcion,
                enabled = !state.guardandoDescripcion &&
                    state.descripcionModificada.trim() != state.modificando?.descripcion) {
                Text(if (state.guardandoDescripcion) stringResource(R.string.features_home_guardando) else stringResource(R.string.features_home_guardar_cambios))
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::cerrarDescripcion, enabled = !state.guardandoDescripcion) {
                Text(stringResource(R.string.features_home_cancelar))
            }
        }
    )
    borrar?.let { publicacion -> AlertDialog(
        onDismissRequest = { borrar = null }, title = { Text(stringResource(R.string.features_home_eliminar_publicacion)) },
        text = { Text(stringResource(R.string.features_home_este_trabajo_dejara_de_aparecer_en_las_publicaciones)) },
        confirmButton = { TextButton(onClick = { viewModel.eliminar(publicacion); borrar = null }) { Text(stringResource(R.string.features_home_eliminar)) } },
        dismissButton = { TextButton(onClick = { borrar = null }) { Text(stringResource(R.string.features_home_cancelar)) } }) }
}

@Composable
private fun TrabajoCard(publicacion: Publicacion, puedeEliminar: Boolean, eliminando: Boolean,
    onEliminar: () -> Unit, onEditar: () -> Unit, onReservar: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = colors.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        FotoTrabajo(publicacion.imagenUrl, stringResource(R.string.home_work_by, publicacion.barberoNombre), Modifier.fillMaxWidth().aspectRatio(1f),
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
                    Text(stringResource(R.string.features_home_barbero), style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant)
                    Text(publicacion.barberoNombre.ifBlank { stringResource(R.string.features_home_sin_nombre) },
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
                        .format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG).withLocale(androidx.compose.ui.platform.LocalConfiguration.current.locales[0])),
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
                Text(stringResource(R.string.features_home_modificar_descripcion))
            }
            if (puedeEliminar) TextButton(onClick = onEliminar, enabled = !eliminando,
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.textButtonColors(contentColor = colors.error)) {
                if (eliminando) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (eliminando) stringResource(R.string.features_home_eliminando) else stringResource(R.string.features_home_eliminar_publicacion))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTrabajo(state: HomeUiState, vm: HomeViewModel) {
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
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
        else if (uri != null) vm.descartarCaptura(uri)
        cameraUri = null
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.imagen(uri.toString())
        }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.features_home_agregar_trabajo), fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = salir, enabled = !state.publicando) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.features_home_volver))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer))
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {
                        vm.prepararCaptura { uri ->
                            cameraUri = uri
                            runCatching { camera.launch(Uri.parse(uri)) }.onFailure {
                                vm.descartarCaptura(uri)
                                vm.errorFoto(resources.getString(R.string.features_home_no_se_pudo_abrir_la_camara_podes_elegir_una_foto_de_la_galeria))
                            }
                        }
                    }, enabled = !state.publicando, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.features_home_camara)) }
                    OutlinedButton(onClick = {
                        runCatching { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                            .onFailure { vm.errorFoto(resources.getString(R.string.features_home_no_se_pudo_abrir_la_galeria)) }
                    }, enabled = !state.publicando, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.features_home_galeria)) }
                }
            }
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    FotoTrabajo(state.imagen, stringResource(R.string.features_home_vista_previa_del_trabajo), Modifier.fillMaxWidth().aspectRatio(1f),
                        BiasAlignment(state.recorteX, state.recorteY))
                }
            }
            if (state.imagen.isNotBlank()) item {
                Text(stringResource(R.string.features_home_ajustar_encuadre), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.features_home_todas_las_fotos_se_publican_en_formato_cuadrado), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.features_home_horizontal), style = MaterialTheme.typography.labelMedium)
                Slider(value = state.recorteX, onValueChange = { vm.encuadre(it, state.recorteY) },
                    valueRange = -1f..1f, enabled = !state.publicando)
                Text(stringResource(R.string.features_home_vertical), style = MaterialTheme.typography.labelMedium)
                Slider(value = state.recorteY, onValueChange = { vm.encuadre(state.recorteX, it) },
                    valueRange = -1f..1f, enabled = !state.publicando)
            }
            item {
                OutlinedTextField(value = state.descripcion, onValueChange = vm::descripcion,
                    label = { Text(stringResource(R.string.features_home_descripcion_opcional)) }, supportingText = { Text(stringResource(R.string.home_description_count, state.descripcion.length)) },
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
                    Text(if (state.publicando) stringResource(R.string.features_home_publicando) else stringResource(R.string.features_home_publicar_trabajo))
                }
            }
        }
    }
    if (confirmarSalida) AlertDialog(onDismissRequest = { confirmarSalida = false },
        title = { Text(stringResource(R.string.features_home_descartar_publicacion)) }, text = { Text(stringResource(R.string.features_home_la_foto_y_la_descripcion_todavia_no_se_publicaron)) },
        confirmButton = { TextButton(onClick = { confirmarSalida = false; vm.cerrarEditor() }) { Text(stringResource(R.string.features_home_descartar)) } },
        dismissButton = { TextButton(onClick = { confirmarSalida = false }) { Text(stringResource(R.string.features_home_seguir_editando)) } })
}

@Composable
private fun FotoTrabajo(model: String, descripcion: String, modifier: Modifier, alignment: Alignment = Alignment.Center,
    rutaLocal: String? = null) {
    val imagen = rutaLocal?.takeIf { it.isNotBlank() }?.let {
        Uri.Builder().scheme("file").path(it).build()
    } ?: model.ifBlank { null }
    SubcomposeAsyncImage(model = imagen, contentDescription = descripcion,
        modifier = modifier, contentScale = ContentScale.Crop, alignment = alignment,
        loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
        error = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(48.dp))
                    Text(if (model.isBlank()) stringResource(R.string.features_home_elegi_una_foto) else stringResource(R.string.features_home_no_se_pudo_mostrar_la_foto))
                }
            }
        })
}
