package kz.kkm.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ─── Brand Colors ───────────────────────────────────────────
val KkmBlue        = Color(0xFF1A5276)
val KkmBlueDark    = Color(0xFF0E2F44)
val KkmBlueLight   = Color(0xFF2E86C1)
val KkmTeal        = Color(0xFF148F77)
val KkmGreen       = Color(0xFF1E8449)
val KkmAmber       = Color(0xFFF39C12)
val KkmRed         = Color(0xFFC0392B)
val KkmGray        = Color(0xFF7F8C8D)
val KkmSurface     = Color(0xFFF4F6F7)
val KkmSurfaceDark = Color(0xFF1C2833)

private val LightColorScheme = lightColorScheme(
    primary          = KkmBlue,
    onPrimary        = Color.White,
    primaryContainer = Color(0xFFD6EAF8),
    secondary        = KkmTeal,
    onSecondary      = Color.White,
    tertiary         = KkmAmber,
    background       = KkmSurface,
    surface          = Color.White,
    surfaceVariant   = Color(0xFFEBF5FB),
    error            = KkmRed,
    onSurface        = Color(0xFF1A252F),
    onBackground     = Color(0xFF1A252F),
    outline          = Color(0xFFBFC9CA)
)

private val DarkColorScheme = darkColorScheme(
    primary          = KkmBlueLight,
    onPrimary        = Color.White,
    primaryContainer = KkmBlueDark,
    secondary        = Color(0xFF52BE95),
    background       = KkmSurfaceDark,
    surface          = Color(0xFF212F3D),
    surfaceVariant   = Color(0xFF1A2535),
    error            = Color(0xFFE74C3C),
    onSurface        = Color(0xFFECF0F1),
    onBackground     = Color(0xFFECF0F1)
)

@Composable
fun KkmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = KkmTypography,
        content     = content
    )
}

val KkmTypography = Typography(
    headlineLarge  = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold,   letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall  = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleLarge     = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium    = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall     = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge      = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
    bodyMedium     = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodySmall      = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
    labelLarge     = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium    = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall     = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
)
