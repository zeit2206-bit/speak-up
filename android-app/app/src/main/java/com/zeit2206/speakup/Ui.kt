package com.zeit2206.speakup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val missMark = SpanStyle(color = P.coral, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)
val unsureMark = SpanStyle(color = P.amber, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)

/** The tapped word (owner WordText, word index); highlighted until its lookup sheet closes. */
var picked by mutableStateOf<Pair<Any, Int>?>(null)

/** Text whose words can be tapped (→ [onWord] with the bare word). [marks]: style per word index (split on spaces). */
@Composable
fun WordText(text: String, style: TextStyle, modifier: Modifier = Modifier, marks: Map<Int, SpanStyle> = emptyMap(), onWord: ((String) -> Unit)?) {
    val ranges = remember(text) { Regex("\\S+").findAll(text).map { it.range }.toList() }
    val me = remember { Any() }
    val pickedHere = picked?.takeIf { it.first === me }?.second
    val pickBg = P.pick
    val annotated = remember(text, marks, pickedHere, pickBg) {
        buildAnnotatedString {
            append(text)
            ranges.forEachIndexed { i, r ->
                val st = marks[i] ?: return@forEachIndexed
                // mark the letters only, not the trailing comma/period
                var a = r.first; var b = r.last + 1
                while (a < b && !text[a].isLetterOrDigit()) a++
                while (b > a && !text[b - 1].isLetterOrDigit()) b--
                if (a < b) addStyle(st, a, b)
            }
            pickedHere?.let { ranges.getOrNull(it) }?.let { r ->
                var a = r.first; var b = r.last + 1
                while (a < b && !text[a].isLetterOrDigit()) a++
                while (b > a && !text[b - 1].isLetterOrDigit()) b--
                if (a < b) addStyle(SpanStyle(background = pickBg), a, b)
            }
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val tap = if (onWord == null) Modifier else Modifier.pointerInput(text) {
        detectTapGestures { pos ->
            val l = layout ?: return@detectTapGestures
            val o = l.getOffsetForPosition(pos)
            val i = ranges.indexOfFirst { o in it.first..it.last }
            ranges.getOrNull(i)?.let { r ->
                val w = text.substring(r.first, r.last + 1).trim { !it.isLetterOrDigit() }
                if (w.isNotEmpty()) { picked = me to i; onWord(w) }
            }
        }
    }
    Text(annotated, modifier.then(tap), style = style, onTextLayout = { layout = it })
}

fun Modifier.dashedBorder(color: Color, radius: Dp) = drawBehind {
    val r = radius.toPx(); val w = 1.5.dp.toPx()
    drawRoundRect(color, cornerRadius = CornerRadius(r), style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
        topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2), size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w))
}

/** Press-and-hold: [onDown] when pressed (return false to refuse), [onUp] when released. */
fun Modifier.holdToTalk(key: Any?, onDown: () -> Boolean, onUp: () -> Unit) = pointerInput(key) {
    detectTapGestures(onPress = {
        if (onDown()) { tryAwaitRelease(); onUp() }
    })
}

@Composable
fun Pill(label: String, icon: ImageVector?, modifier: Modifier = Modifier, bg: Color = P.teal, fg: Color = Color.White,
         border: Color? = null, height: Dp = 52.dp, textSize: Int = 16, onClick: () -> Unit) {
    Row(
        modifier.height(height).clip(RoundedCornerShape(height / 2)).background(bg)
            .then(if (border != null) Modifier.border(1.dp, border, RoundedCornerShape(height / 2)) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(if (height < 44.dp) 18.dp else 22.dp), tint = fg); Spacer(Modifier.width(8.dp)) }
        Text(label, style = tx(textSize, 600, fg))
    }
}

@Composable
fun IconBtn(icon: ImageVector, desc: String, tint: Color = P.muted, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, Modifier.size(24.dp), tint = tint)
    }
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = P.muted) = Text(text.uppercase(), modifier, style = tx(11, 600, color))

@Composable
fun MicButton(size: Dp, active: Boolean, enabled: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape).background(if (!enabled) P.line else if (active) P.coral else P.teal),
        contentAlignment = Alignment.Center,
    ) { Icon(Ic.mic, "Hold to talk", Modifier.size(size * 0.42f), tint = Color.White) }
}

@Composable
fun ScoreChip(s: ReadScore, label: String, modifier: Modifier = Modifier) {
    val good = s.perfect
    Row(
        modifier.clip(RoundedCornerShape(20.dp)).background(if (good) P.tealSoft else P.coralSoft).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (good) Ic.check else Ic.repeat, null, Modifier.size(18.dp), tint = if (good) P.teal else P.coral)
        Spacer(Modifier.width(8.dp))
        Text("$label · ${s.good}/${s.total} words", style = tx(13, 600, if (good) P.teal else P.coral))
    }
}

/** Word lookup sheet: IPA (US), Spanish, Pronounce, Save — like the PDF IPA Reader popup. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordSheet(word: String, store: Store, onSay: (String) -> Unit, onDismiss: () -> Unit) {
    val e = remember(word, Dict.ready) { Dict.lookup(word) }
    var saved by remember(word) { mutableStateOf(store.isSaved(e.word)) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { picked = null } }
    // Speak the word as soon as the sheet opens; Pronounce plays it again.
    androidx.compose.runtime.LaunchedEffect(word) { onSay(e.word) }
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = P.card, tonalElevation = 0.dp, scrimColor = P.scrim) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 20.dp).navigationBarsPadding()) {
            Text(e.word, style = tx(30, 700))
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (Dict.ready && e.ipa != null) IpaText(e.ipa, ipaStyle(21))
                else Text(if (!Dict.ready) "Loading dictionary…" else "No IPA for this word", style = ipaStyle(21, P.muted))
                if (e.ipa != null) { Spacer(Modifier.width(10.dp)); Text("US", style = tx(11, 600, P.muted)) }
            }
            Spacer(Modifier.height(20.dp))
            Label("Español")
            Spacer(Modifier.height(6.dp))
            Text(e.es ?: "—", style = tx(16))
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Pill("Pronounce", Ic.speaker, height = 48.dp, textSize = 15) { onSay(e.word) }
                Pill(if (saved) "Saved" else "Save", if (saved) Ic.starFull else Ic.star, height = 48.dp, textSize = 15, bg = P.amberSoft, fg = P.amberInk) {
                    if (saved) store.unsave(e.word) else store.save(e); saved = !saved
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("Tap any word in the chat or suggestions · tap an IPA symbol to learn it", style = tx(13, 400, P.muted), textAlign = TextAlign.Start)
        }
    }
}
