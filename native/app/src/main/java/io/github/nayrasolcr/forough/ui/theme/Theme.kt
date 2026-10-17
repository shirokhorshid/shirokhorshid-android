package io.github.nayrasolcr.forough.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Forough theme — a faithful mapping of the Linux client's libadwaita dark
 * style (the desktop app uses standard Adwaita widgets with the default
 * accent) plus the Forough brand gold from the logo.
 *
 * libadwaita dark palette:
 *   window_bg #242428 · shade #1d1d20 · card #2e2e32 (rgba white .08)
 *   fg #ffffff · dim fg ~#dedede
 *   accent/suggested #3584e4 · destructive #e01b24
 * Forough brand: gold #fab649 on deep navy #0c2d5e/#052351.
 */

val AdwWindowBg = Color(0xFF242428)
val AdwShade = Color(0xFF1D1D20)
val AdwCard = Color(0xFF2E2E32)
val AdwCardHigher = Color(0xFF36363A)
val AdwFg = Color(0xFFFFFFFF)
val AdwDimFg = Color(0xFFDEDEDE)
val AdwOutline = Color(0xFF4E4E54)
val AdwAccent = Color(0xFF3584E4)
val AdwAccentDim = Color(0xFF1B5FA8)
val AdwDestructive = Color(0xFFE01B24)
val ForoughGold = Color(0xFFFAB649)
val ForoughNavy = Color(0xFF052351)
val GraphRx = Color(0xFF338CF0)   // desktop draw color rgb(0.20, 0.55, 0.95)
val GraphTx = Color(0xFF26BF59)   // desktop draw color rgb(0.15, 0.75, 0.35)

private val ForoughDarkColors: ColorScheme = darkColorScheme(
    primary = AdwAccent,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = AdwAccentDim,
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = ForoughGold,
    onSecondary = Color(0xFF1D1D20),
    secondaryContainer = Color(0xFF3A3A40),
    onSecondaryContainer = ForoughGold,
    tertiary = ForoughGold,
    error = AdwDestructive,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF5A1015),
    onErrorContainer = Color(0xFFFFB4AB),
    background = AdwWindowBg,
    onBackground = AdwFg,
    surface = AdwWindowBg,
    onSurface = AdwFg,
    surfaceVariant = AdwCard,
    onSurfaceVariant = AdwDimFg,
    surfaceContainerLowest = AdwShade,
    surfaceContainerLow = Color(0xFF2A2A2E),
    surfaceContainer = AdwCard,
    surfaceContainerHigh = AdwCardHigher,
    surfaceContainerHighest = Color(0xFF3E3E44),
    outline = AdwOutline,
    outlineVariant = Color(0xFF3E3E44),
    inverseSurface = Color(0xFFE6E6E6),
    inverseOnSurface = Color(0xFF1D1D20)
)

/**
 * The Forough client always presents the libadwaita-style dark look —
 * same as the desktop client's dark style preference.
 */
@Composable
fun ForoughTheme(content: @Composable () -> Unit) {
    // Dark is the Forough look on both platforms; keep MaterialTheme API
    // surface for future light-variant support.
    MaterialTheme(
        colorScheme = ForoughDarkColors,
        content = content
    )
}
