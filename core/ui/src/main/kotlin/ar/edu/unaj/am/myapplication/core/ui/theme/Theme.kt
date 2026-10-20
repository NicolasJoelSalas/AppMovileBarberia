package ar.edu.unaj.am.myapplication.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF284B70),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9CEE3),
    onPrimaryContainer = Color(0xFF15324F),
    inversePrimary = Color(0xFFAACBEF),
    secondary = Color(0xFF4D6175),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCAD7E3),
    onSecondaryContainer = Color(0xFF253A4D),
    tertiary = Color(0xFF23675E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB8D8CC),
    onTertiaryContainer = Color(0xFF12483F),
    background = Color(0xFFD8E1EA),
    onBackground = Color(0xFF1D2935),
    surface = Color(0xFFE5EBF1),
    onSurface = Color(0xFF1D2935),
    surfaceVariant = Color(0xFFCAD7E3),
    onSurfaceVariant = Color(0xFF374B5E),
    surfaceDim = Color(0xFFC4D0DD),
    surfaceBright = Color(0xFFEDF2F6),
    surfaceContainerLowest = Color(0xFFEDF2F6),
    surfaceContainerLow = Color(0xFFE1E8EF),
    surfaceContainer = Color(0xFFEDF2F6),
    surfaceContainerHigh = Color(0xFFD2DEE9),
    surfaceContainerHighest = Color(0xFFC4D3E1),
    surfaceTint = Color(0xFF284B70),
    outline = Color(0xFF586D81),
    outlineVariant = Color(0xFF8497AA),
    inverseSurface = Color(0xFF293643),
    inverseOnSurface = Color(0xFFF1F5F9),
    error = Color(0xFFAD302B),
    onError = Color.White,
    errorContainer = Color(0xFFFFE3DF),
    onErrorContainer = Color(0xFF721D19),
    scrim = Color(0xFF101C29)
)

/** Tema compartido: vive en :core:ui para que todas las features se vean igual. */
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme && dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            dynamicDarkColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
