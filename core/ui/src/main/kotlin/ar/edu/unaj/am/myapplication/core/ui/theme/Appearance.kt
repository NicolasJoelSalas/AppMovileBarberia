package ar.edu.unaj.am.myapplication.core.ui.theme

import androidx.compose.runtime.compositionLocalOf

/** Preferencia de apariencia compartida por las pantallas de la aplicación. */
data class Appearance(
    val darkTheme: Boolean = false,
    val onDarkThemeChange: (Boolean) -> Unit = {}
)

val LocalAppearance = compositionLocalOf { Appearance() }
