package com.zeit2206.speakup

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Palette from the Figma board "Speak Up — Android (Claude design)". */
object P {
    /** Dark theme on/off. Compose state, so every screen recolors when it changes. */
    var dark by mutableStateOf(false)
    private fun c(light: Long, dark: Long) = if (P.dark) Color(dark) else Color(light)

    val bg get() = c(0xFFFBF7F1, 0xFF15181E)
    val card get() = c(0xFFFFFFFF, 0xFF1F232B)
    val ink get() = c(0xFF1F2430, 0xFFECEAE6)
    val muted get() = c(0xFF6B7280, 0xFF9AA0AA)
    val line get() = c(0xFFECE6DC, 0xFF2F343D)
    val teal get() = c(0xFF127C71, 0xFF2A9D90)
    val tealDark get() = c(0xFF0E665D, 0xFF1D7A70)
    val tealSoft get() = c(0xFFE3F1EE, 0xFF1C3532)
    val tealPale get() = c(0xFF9BB9B4, 0xFF4F6E69)
    val coral = Color(0xFFE8603F)
    val coralSoft get() = c(0xFFFCE9E3, 0xFF3A2520)
    val amber get() = c(0xFF9A6A0B, 0xFFE0A93A)
    val amberInk get() = c(0xFF7A5508, 0xFFF0C46A)
    val amberSoft get() = c(0xFFFDF3DC, 0xFF3A3020)
    val scrim = Color(0x591F2430)
    /** Background behind a tapped word while its lookup card is open. */
    val pick get() = c(0xFFFDE68A, 0xFF5C4A12)
}

@OptIn(ExperimentalTextApi::class)
private fun inter(w: Int) = Font(R.font.inter, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

val Inter = FontFamily(inter(400), inter(500), inter(600), inter(700))
val Charis = FontFamily(Font(R.font.charis_regular, FontWeight.Normal))

fun tx(size: Int, weight: Int = 400, color: Color = P.ink, lh: Int = 0) =
    TextStyle(fontFamily = Inter, fontSize = size.sp, fontWeight = FontWeight(weight), color = color,
        lineHeight = if (lh > 0) lh.sp else androidx.compose.ui.unit.TextUnit.Unspecified)

fun ipaStyle(size: Int, color: Color = P.teal) = TextStyle(fontFamily = Charis, fontSize = size.sp, color = color)

@Composable
fun AppTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = if (P.dark) darkColorScheme(primary = P.teal, background = P.bg, surface = P.card, onSurface = P.ink)
        else lightColorScheme(primary = P.teal, background = P.bg, surface = P.card, onSurface = P.ink),
    content = content,
)

/** Stroke icons from the same SVG paths as the Figma mockups (24×24 grid). */
object Ic {
    private fun icon(name: String, vararg paths: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        for (d in paths) addPath(addPathNodes(d), stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }.build()

    private fun filled(name: String, solid: String, vararg strokes: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        addPath(addPathNodes(solid), fill = SolidColor(Color.Black))
        for (d in strokes) addPath(addPathNodes(d), stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round)
    }.build()

    val mic = filled("mic", "M12 3a3 3 0 0 1 3 3v5a3 3 0 0 1 -6 0V6a3 3 0 0 1 3 -3z", "M5.5 11a6.5 6.5 0 0 0 13 0M12 17.5V21M8.5 21h7")
    val speaker = filled("speaker", "M4 9h4l5-4v14l-5-4H4z", "M16.5 8.5a5 5 0 0 1 0 7M19 6a8.5 8.5 0 0 1 0 12")
    val bulb = icon("bulb", "M9 18h6M10 21h4M12 3a6 6 0 0 0 -3.5 10.9c0.6 0.5 1 1.2 1 2V16h5v-0.1c0 -0.8 0.4 -1.5 1 -2A6 6 0 0 0 12 3z")
    val back = icon("back", "M15 5l-7 7 7 7")
    val gear = icon("gear", "M12 8.8a3.2 3.2 0 1 1 0 6.4a3.2 3.2 0 1 1 0 -6.4z",
        "M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.3 5.3l2.1 2.1M16.6 16.6l2.1 2.1M5.3 18.7l2.1 -2.1M16.6 7.4l2.1 -2.1")
    val sun = icon("sun", "M12 8a4 4 0 1 1 0 8a4 4 0 1 1 0 -8z",
        "M12 2v2M12 20v2M2 12h2M20 12h2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M4.9 19.1l1.4 -1.4M17.7 6.3l1.4 -1.4")
    val moon = icon("moon", "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z")
    val sliders = icon("sliders", "M4 7h10M18 7h2M4 17h4M12 17h8", "M16 5v4M10 15v4")
    val shuffle = icon("shuffle", "M4 7h3.5c2 0 3 1 4.5 3.5S14.5 17 16.5 17H20M4 17h3.5c1.3 0 2.2 -0.5 3 -1.4M13.5 8.4c0.8 -0.9 1.7 -1.4 3 -1.4H20M17 4l3 3 -3 3M17 14l3 3 -3 3")
    val check = icon("check", "M5 12.5l4.5 4.5L19 7.5")
    val repeat = icon("repeat", "M4 12a8 8 0 0 1 13.7 -5.6L20 8.5M20 4v4.5h-4.5M20 12a8 8 0 0 1 -13.7 5.6L4 15.5M4 20v-4.5h4.5")
    val close = icon("close", "M6 6l12 12M18 6L6 18")
    val chat = icon("chat", "M4 5h16v11H9l-5 4z")
    val arrow = icon("arrow", "M5 12h13M13 6l6 6 -6 6")
    val star = icon("star", "M12 3.5l2.6 5.4 5.9 0.8 -4.3 4.1 1 5.8L12 16.8l-5.2 2.8 1 -5.8 -4.3 -4.1 5.9 -0.8z")
    val starFull = filled("starFull", "M12 3.5l2.6 5.4 5.9 0.8 -4.3 4.1 1 5.8L12 16.8l-5.2 2.8 1 -5.8 -4.3 -4.1 5.9 -0.8z")
    val book = icon("book", "M5 4h11a3 3 0 0 1 3 3v13H8a3 3 0 0 1 -3 -3z", "M5 17a3 3 0 0 1 3 -3h11")
    val send = icon("send", "M4 12l16 -8 -6 16 -2.5 -6.5z")
    val pen = icon("pen", "M4 20h4L19 9l-4 -4L4 16z")
    val trash = icon("trash", "M5 7h14M10 7V4h4v3M7 7l1 13h8l1 -13")
    val search = icon("search", "M10.5 4a6.5 6.5 0 1 1 0 13a6.5 6.5 0 1 1 0 -13z", "M15.5 15.5L20 20")
}
