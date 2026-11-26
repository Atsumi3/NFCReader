package info.nukoneko.android.nfcreader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF9F2D32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD9),
    onPrimaryContainer = Color(0xFF410006),
    inversePrimary = Color(0xFF8E242C),
    secondary = Color(0xFF5C6268),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5E1E5),
    onSecondaryContainer = Color(0xFF191A1D),
    tertiary = Color(0xFF665E44),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEFE2BB),
    onTertiaryContainer = Color(0xFF211B07),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF201A19),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF201A19),
    surfaceTint = Color(0xFF9F2D32),
    surfaceVariant = Color(0xFFF2E3E1),
    onSurfaceVariant = Color(0xFF514342),
    inverseSurface = Color(0xFF362F2E),
    inverseOnSurface = Color(0xFFFBEEEC),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF827372),
    outlineVariant = Color(0xFFD4C7C5),
    scrim = Color.Black,
    surfaceDim = Color(0xFFE8DAD8),
    surfaceBright = Color(0xFFFFF8F6),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBF1EF),
    surfaceContainer = Color(0xFFF4E8E6),
    surfaceContainerHigh = Color(0xFFEEE0DE),
    surfaceContainerHighest = Color(0xFFE8DAD8),
    primaryFixed = Color(0xFFFFDAD9),
    primaryFixedDim = Color(0xFFFFB3B2),
    onPrimaryFixed = Color(0xFF410006),
    onPrimaryFixedVariant = Color(0xFF733038),
    secondaryFixed = Color(0xFFE5E1E5),
    secondaryFixedDim = Color(0xFFC8C5C9),
    onSecondaryFixed = Color(0xFF191A1D),
    onSecondaryFixedVariant = Color(0xFF45464A),
    tertiaryFixed = Color(0xFFEFE2BB),
    tertiaryFixedDim = Color(0xFFD2C69E),
    onTertiaryFixed = Color(0xFF211B07),
    onTertiaryFixedVariant = Color(0xFF4D462D),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB3B2),
    onPrimary = Color(0xFF65000E),
    primaryContainer = Color(0xFF861E27),
    onPrimaryContainer = Color(0xFFFFDAD9),
    inversePrimary = Color(0xFF9F2D32),
    secondary = Color(0xFFC2C7CE),
    onSecondary = Color(0xFF2C3136),
    secondaryContainer = Color(0xFF4A4244),
    onSecondaryContainer = Color(0xFFE9E0E2),
    tertiary = Color(0xFFD2C69E),
    onTertiary = Color(0xFF363018),
    tertiaryContainer = Color(0xFF4D462D),
    onTertiaryContainer = Color(0xFFEFE2BB),
    background = Color(0xFF171211),
    onBackground = Color(0xFFEDE0DE),
    surface = Color(0xFF171211),
    onSurface = Color(0xFFEDE0DE),
    surfaceTint = Color(0xFFFFB3B2),
    surfaceVariant = Color(0xFF514342),
    onSurfaceVariant = Color(0xFFD8C2C0),
    inverseSurface = Color(0xFFEDE0DE),
    inverseOnSurface = Color(0xFF201A19),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF9F8D8A),
    outlineVariant = Color(0xFF514342),
    scrim = Color.Black,
    surfaceDim = Color(0xFF171211),
    surfaceBright = Color(0xFF3F3736),
    surfaceContainerLowest = Color(0xFF120D0D),
    surfaceContainerLow = Color(0xFF1F1918),
    surfaceContainer = Color(0xFF231D1C),
    surfaceContainerHigh = Color(0xFF2D2625),
    surfaceContainerHighest = Color(0xFF382F2E),
    primaryFixed = Color(0xFFFFDAD9),
    primaryFixedDim = Color(0xFFFFB3B2),
    onPrimaryFixed = Color(0xFF410006),
    onPrimaryFixedVariant = Color(0xFF733038),
    secondaryFixed = Color(0xFFE5E1E5),
    secondaryFixedDim = Color(0xFFC8C5C9),
    onSecondaryFixed = Color(0xFF191A1D),
    onSecondaryFixedVariant = Color(0xFF45464A),
    tertiaryFixed = Color(0xFFEFE2BB),
    tertiaryFixedDim = Color(0xFFD2C69E),
    onTertiaryFixed = Color(0xFF211B07),
    onTertiaryFixedVariant = Color(0xFF4D462D),
)

private val ReaderTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.Medium),
    )
}

private val ReaderShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun NfcReaderTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = ReaderTypography,
        shapes = ReaderShapes,
        content = content,
    )
}
