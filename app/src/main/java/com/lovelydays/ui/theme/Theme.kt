package com.lovelydays.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.lovelydays.domain.Mark

private val LightColors = lightColorScheme(
    primary = Color(0xFF7C3E4A),
    onPrimary = Color(0xFFFFF8F6),
    primaryContainer = Color(0xFFF3D5D0),
    onPrimaryContainer = Color(0xFF3F1C23),
    secondary = Color(0xFF5E6A5F),
    onSecondary = Color(0xFFF4F7F4),
    secondaryContainer = Color(0xFFDCE5DC),
    onSecondaryContainer = Color(0xFF1E2A20),
    tertiary = Color(0xFF6E5A7E),
    onTertiary = Color(0xFFFFF7FF),
    tertiaryContainer = Color(0xFFE8DDF0),
    onTertiaryContainer = Color(0xFF2C2233),
    background = Color(0xFFF7F1EA),
    onBackground = Color(0xFF2C2422),
    surface = Color(0xFFFFFBF7),
    onSurface = Color(0xFF2C2422),
    surfaceVariant = Color(0xFFF0E4DC),
    onSurfaceVariant = Color(0xFF5C514C),
    outline = Color(0xFFD9CBC3),
    error = Color(0xFF9F3A32),
    onError = Color(0xFFFFF7F6),
    errorContainer = Color(0xFFF8D7D2),
    onErrorContainer = Color(0xFF3F100C),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF0B7C0),
    onPrimary = Color(0xFF4A222A),
    primaryContainer = Color(0xFF5C3038),
    onPrimaryContainer = Color(0xFFF8E0E3),
    secondary = Color(0xFFC5D1C4),
    onSecondary = Color(0xFF243028),
    secondaryContainer = Color(0xFF3A4A3E),
    onSecondaryContainer = Color(0xFFE0EBDF),
    tertiary = Color(0xFFDCC7EA),
    onTertiary = Color(0xFF3A2C44),
    tertiaryContainer = Color(0xFF51425C),
    onTertiaryContainer = Color(0xFFF3E7F8),
    background = Color(0xFF1C1614),
    onBackground = Color(0xFFF3EBE6),
    surface = Color(0xFF261F1C),
    onSurface = Color(0xFFF3EBE6),
    surfaceVariant = Color(0xFF3A312C),
    onSurfaceVariant = Color(0xFFD9CBC3),
    outline = Color(0xFF5A4E48),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF7A2E28),
    onErrorContainer = Color(0xFFFFDAD6),
)

data class MarkPalette(
    val period: Color,
    val ovulation: Color,
    val fertile: Color,
    val mood: Color,
    val skin: Color,
) {
    fun color(mark: Mark): Color = when (mark) {
        Mark.PERIOD -> period
        Mark.OVULATION -> ovulation
        Mark.FERTILE -> fertile
        Mark.MOOD -> mood
        Mark.SKIN -> skin
    }
}

private val LightMarks = MarkPalette(
    period = Color(0xFFB23A4A),
    ovulation = Color(0xFFB8862E),
    fertile = Color(0xFFD08A5A),
    mood = Color(0xFF74608F),
    skin = Color(0xFFA86448),
)

private val DarkMarks = MarkPalette(
    period = Color(0xFFFF8D9A),
    ovulation = Color(0xFFF0C27A),
    fertile = Color(0xFFFFBE90),
    mood = Color(0xFFD2C0EA),
    skin = Color(0xFFFFC2A8),
)

val LocalMarkColors = staticCompositionLocalOf { LightMarks }

private val LovelyType = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 34.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
)

private val LovelyShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun LovelyTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalMarkColors provides if (dark) DarkMarks else LightMarks) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = LovelyType,
            shapes = LovelyShapes,
            content = content,
        )
    }
}
