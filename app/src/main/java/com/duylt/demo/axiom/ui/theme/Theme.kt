package com.duylt.demo.axiom.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightScheme =
    lightColorScheme(
        primary = Color(0xFF3B5BA9),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFDCE1FF),
        onPrimaryContainer = Color(0xFF001550),
        secondary = Color(0xFF5A5D72),
        secondaryContainer = Color(0xFFDFE1F9),
        tertiary = Color(0xFF75546F),
        tertiaryContainer = Color(0xFFFFD7F5),
        error = Color(0xFFBA1A1A),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        surface = Color(0xFFFBF8FF),
        surfaceVariant = Color(0xFFE2E1EC),
        onSurfaceVariant = Color(0xFF45464F),
    )

private val DarkScheme =
    darkColorScheme(
        primary = Color(0xFFB5C4FF),
        onPrimary = Color(0xFF00287F),
        primaryContainer = Color(0xFF1F4190),
        onPrimaryContainer = Color(0xFFDCE1FF),
        secondary = Color(0xFFC3C5DD),
        secondaryContainer = Color(0xFF424659),
        tertiary = Color(0xFFE4BADA),
        tertiaryContainer = Color(0xFF5C3D57),
        error = Color(0xFFFFB4AB),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        surface = Color(0xFF131318),
        surfaceVariant = Color(0xFF45464F),
        onSurfaceVariant = Color(0xFFC6C5D0),
    )

/** Material 3 with dynamic colour where the platform has it (Android 12+), the static scheme elsewhere. */
@Composable
fun DemoAxiomTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkScheme
            else -> LightScheme
        }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
