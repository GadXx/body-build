package ru.bodybuild.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val ElectricLime = Color(0xFFC8F560)
val Ink = Color(0xFF11140F)
val DeepSurface = Color(0xFF1A1E17)
val SoftSurface = Color(0xFFF2F4EB)
val Muted = Color(0xFF687061)

private val DarkColors = darkColorScheme(
    primary = ElectricLime,
    onPrimary = Ink,
    background = Ink,
    onBackground = Color(0xFFF6F7F1),
    surface = DeepSurface,
    onSurface = Color(0xFFF6F7F1),
    surfaceVariant = Color(0xFF272C23),
    onSurfaceVariant = Color(0xFFC7CDC0),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF4D6D00),
    onPrimary = Color.White,
    background = Color(0xFFFAFCF5),
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = SoftSurface,
    onSurfaceVariant = Muted,
)

private val BodyTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 34.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 26.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 23.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 14.sp),
)

object BodyBuildShapes {
    val card = RoundedCornerShape(24.dp)
    val control = RoundedCornerShape(14.dp)
}

@Composable
fun BodyBuildTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = BodyTypography,
        shapes = MaterialTheme.shapes.copy(
            medium = BodyBuildShapes.control,
            large = BodyBuildShapes.card,
        ),
        content = content,
    )
}
