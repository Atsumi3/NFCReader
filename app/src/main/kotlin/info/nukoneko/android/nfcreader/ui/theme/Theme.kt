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
    primary = Color(0xFF181818),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5E5E5),
    onPrimaryContainer = Color(0xFF171717),
    inversePrimary = Color(0xFF555555),
    secondary = Color(0xFF555555),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEEEEEE),
    onSecondaryContainer = Color(0xFF202020),
    tertiary = Color(0xFF5C5C5C),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE7E7E7),
    onTertiaryContainer = Color(0xFF202020),
    background = Color.White,
    onBackground = Color(0xFF171717),
    surface = Color.White,
    onSurface = Color(0xFF171717),
    surfaceTint = Color(0xFF181818),
    surfaceVariant = Color(0xFFF1F1F1),
    onSurfaceVariant = Color(0xFF4A4A4A),
    inverseSurface = Color(0xFF303030),
    inverseOnSurface = Color(0xFFF5F5F5),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF767676),
    outlineVariant = Color(0xFFD3D3D3),
    scrim = Color.Black,
    surfaceDim = Color(0xFFE5E5E5),
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainer = Color(0xFFF4F4F4),
    surfaceContainerHigh = Color(0xFFEEEEEE),
    surfaceContainerHighest = Color(0xFFE8E8E8),
    primaryFixed = Color(0xFFE5E5E5),
    primaryFixedDim = Color(0xFFD0D0D0),
    onPrimaryFixed = Color(0xFF171717),
    onPrimaryFixedVariant = Color(0xFF404040),
    secondaryFixed = Color(0xFFECECEC),
    secondaryFixedDim = Color(0xFFD3D3D3),
    onSecondaryFixed = Color(0xFF1E1E1E),
    onSecondaryFixedVariant = Color(0xFF454545),
    tertiaryFixed = Color(0xFFE8E8E8),
    tertiaryFixedDim = Color(0xFFD0D0D0),
    onTertiaryFixed = Color(0xFF1E1E1E),
    onTertiaryFixedVariant = Color(0xFF454545),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE5E5E5),
    onPrimary = Color(0xFF171717),
    primaryContainer = Color(0xFF3B3B3B),
    onPrimaryContainer = Color(0xFFF0F0F0),
    inversePrimary = Color(0xFF555555),
    secondary = Color(0xFFC5C5C5),
    onSecondary = Color(0xFF202020),
    secondaryContainer = Color(0xFF424242),
    onSecondaryContainer = Color(0xFFE8E8E8),
    tertiary = Color(0xFFD0D0D0),
    onTertiary = Color(0xFF202020),
    tertiaryContainer = Color(0xFF3A3A3A),
    onTertiaryContainer = Color(0xFFEEEEEE),
    background = Color(0xFF101010),
    onBackground = Color(0xFFF1F1F1),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFF1F1F1),
    surfaceTint = Color(0xFFE5E5E5),
    surfaceVariant = Color(0xFF414141),
    onSurfaceVariant = Color(0xFFD0D0D0),
    inverseSurface = Color(0xFFF1F1F1),
    inverseOnSurface = Color(0xFF222222),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF9A9A9A),
    outlineVariant = Color(0xFF4A4A4A),
    scrim = Color.Black,
    surfaceDim = Color(0xFF101010),
    surfaceBright = Color(0xFF3F3F3F),
    surfaceContainerLowest = Color(0xFF101010),
    surfaceContainerLow = Color(0xFF1C1C1C),
    surfaceContainer = Color(0xFF222222),
    surfaceContainerHigh = Color(0xFF2C2C2C),
    surfaceContainerHighest = Color(0xFF373737),
    primaryFixed = Color(0xFFE5E5E5),
    primaryFixedDim = Color(0xFFD0D0D0),
    onPrimaryFixed = Color(0xFF171717),
    onPrimaryFixedVariant = Color(0xFF404040),
    secondaryFixed = Color(0xFFECECEC),
    secondaryFixedDim = Color(0xFFD3D3D3),
    onSecondaryFixed = Color(0xFF1E1E1E),
    onSecondaryFixedVariant = Color(0xFF454545),
    tertiaryFixed = Color(0xFFE8E8E8),
    tertiaryFixedDim = Color(0xFFD0D0D0),
    onTertiaryFixed = Color(0xFF1E1E1E),
    onTertiaryFixedVariant = Color(0xFF454545),
)

private val ReaderTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.Medium),
    )
}

private val ReaderShapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
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
