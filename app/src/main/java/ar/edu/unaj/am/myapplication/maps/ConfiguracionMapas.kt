package ar.edu.unaj.am.myapplication.maps

import android.content.Context
import org.maplibre.android.MapLibre
import ar.edu.unaj.am.myapplication.core.network.NetworkFactory
import org.maplibre.android.module.http.HttpRequestUtil

/** Inicialización única del transporte del SDK. La pantalla no modifica configuración global. */
fun configurarMapas(context: Context) {
    MapLibre.getInstance(context.applicationContext)
    HttpRequestUtil.setOkHttpClient(NetworkFactory.createMapClient("BarberApp-UNAJ/1.0 (${context.packageName}; Android)"))
}
