package ar.edu.unaj.am.myapplication.core.ui.maps

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ar.edu.unaj.am.myapplication.core.domain.model.Coordenadas
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Geometry

private const val ESTILO_OSM = """{"version":8,"sources":{"osm":{"type":"raster","tiles":["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],"tileSize":256,"maxzoom":19,"attribution":"© OpenStreetMap contributors"}},"layers":[{"id":"osm","type":"raster","source":"osm"}]}"""

/** Componente compartido: la selección y las rutas siguen perteneciendo a sus features. */
@Composable
fun MapaBarberia(
    destino: Coordenadas?,
    modifier: Modifier = Modifier,
    origen: Coordenadas? = null,
    recorrido: List<Coordenadas> = emptyList(),
    onSeleccionar: ((Coordenadas) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val seleccionar by rememberUpdatedState(onSeleccionar)
    var mapa by remember { mutableStateOf<MapLibreMap?>(null) }
    var listo by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        HttpRequestUtil.setOkHttpClient(OkHttpClient.Builder().addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder()
                .header("User-Agent", "BarberApp-UNAJ/1.0 (${context.packageName}; Android)").build())
        }.build())
        MapView(context).apply { onCreate(null) }
    }
    fun cargarEstilo(map: MapLibreMap) {
        listo = false
        error = false
        map.setStyle(Style.Builder().fromJson(ESTILO_OSM)) { style ->
            style.addSource(GeoJsonSource("ruta"))
            style.addLayer(LineLayer("ruta-linea", "ruta").withProperties(
                lineColor("#1565C0"), lineWidth(5f), lineCap("round"), lineJoin("round")))
            style.addSource(GeoJsonSource("origen"))
            style.addLayer(CircleLayer("origen-punto", "origen").withProperties(
                circleColor("#1565C0"), circleRadius(8f), circleStrokeColor("#FFFFFF"), circleStrokeWidth(3f)))
            style.addImage("pin-barberia", crearPin())
            style.addSource(GeoJsonSource("destino"))
            style.addLayer(SymbolLayer("destino-pin", "destino").withProperties(
                iconImage("pin-barberia"), iconAnchor("bottom"), iconAllowOverlap(true), iconSize(0.8f)))
            listo = true
        }
    }
    DisposableEffect(mapView, lifecycle) {
        var destruido = false
        val failed = MapView.OnDidFailLoadingMapListener { error = true }
        mapView.addOnDidFailLoadingMapListener(failed)
        mapView.getMapAsync { map ->
            if (!destruido) {
                mapa = map
                map.uiSettings.isAttributionEnabled = false // Atribución visible y enlazada debajo.
                map.uiSettings.isLogoEnabled = false
                map.setPrefetchZoomDelta(0)
                map.addOnMapClickListener { point ->
                    seleccionar?.invoke(Coordenadas(point.latitude, point.longitude))
                    seleccionar != null
                }
                cargarEstilo(map)
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        val memory = object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            override fun onLowMemory() { mapView.onLowMemory() }
            override fun onTrimMemory(level: Int) { if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) mapView.onLowMemory() }
        }
        context.applicationContext.registerComponentCallbacks(memory)
        lifecycle.addObserver(observer)
        onDispose {
            destruido = true
            lifecycle.removeObserver(observer)
            context.applicationContext.unregisterComponentCallbacks(memory)
            mapView.removeOnDidFailLoadingMapListener(failed)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
            mapa = null
        }
    }
    LaunchedEffect(listo, destino, origen, recorrido) {
        val map = mapa ?: return@LaunchedEffect
        if (!listo) return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        style.getSourceAs<GeoJsonSource>("destino")?.setGeoJson(destino?.point().coleccion())
        style.getSourceAs<GeoJsonSource>("origen")?.setGeoJson(origen?.point().coleccion())
        style.getSourceAs<GeoJsonSource>("ruta")?.setGeoJson(
            (if (recorrido.size >= 2) LineString.fromLngLats(recorrido.map { it.point() }) else null).coleccion())
        val puntos = (recorrido + listOfNotNull(origen, destino)).distinct()
        mapView.post {
            if (mapa === map) {
                if (puntos.size > 1) map.moveCamera(CameraUpdateFactory.newLatLngBounds(
                    LatLngBounds.Builder().includes(puntos.map { LatLng(it.latitud, it.longitud) }).build(), 72))
                else {
                    val centro = puntos.firstOrNull() ?: Coordenadas(-34.80, -58.28)
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(centro.latitud, centro.longitud),
                        if (puntos.isEmpty()) 10.0 else 16.0))
                }
            }
        }
    }
    Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
            if (!listo && !error) CircularProgressIndicator(Modifier.align(Alignment.Center))
            if (error) Surface(Modifier.align(Alignment.TopCenter).padding(8.dp), tonalElevation = 4.dp) {
                Column(Modifier.padding(12.dp)) {
                    Text("No se pudo cargar el mapa. Revisá tu conexión.")
                    TextButton(onClick = { mapa?.let { cargarEstilo(it) } }) { Text("Reintentar") }
                }
            }
        }
        val uriHandler = LocalUriHandler.current
        Surface(color = MaterialTheme.colorScheme.surface) {
            TextButton(onClick = { uriHandler.openUri("https://www.openstreetmap.org/copyright") }) {
                Text("© OpenStreetMap contributors", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun Coordenadas.point() = Point.fromLngLat(longitud, latitud)
private fun Geometry?.coleccion(): FeatureCollection = FeatureCollection.fromFeatures(
    if (this == null) emptyList() else listOf(Feature.fromGeometry(this)))

private fun crearPin(): Bitmap = Bitmap.createBitmap(48, 64, Bitmap.Config.ARGB_8888).also { bitmap ->
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(190, 40, 40) }
    val path = Path().apply {
        moveTo(24f, 63f); cubicTo(18f, 47f, 2f, 34f, 2f, 23f)
        cubicTo(2f, -5f, 46f, -5f, 46f, 23f); cubicTo(46f, 34f, 30f, 47f, 24f, 63f)
    }
    canvas.drawPath(path, paint)
    paint.color = Color.WHITE
    canvas.drawCircle(24f, 23f, 9f, paint)
}
