package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = PixelBlue,
    onPrimary = Color.Black,
    primaryContainer = PixelBlueVariant,
    onPrimaryContainer = Color.White,
    secondary = PixelGreen,
    onSecondary = Color.Black,
    tertiary = PixelYellow,
    background = PureBlack,
    onBackground = OnDarkSurface,
    surface = DarkSurface,
    onSurface = OnDarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = OnDarkSurfaceDim,
    error = PixelRed,
    onError = Color.White
)

@Composable
fun PixelShotTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
