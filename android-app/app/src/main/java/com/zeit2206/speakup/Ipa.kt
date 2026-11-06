package com.zeit2206.speakup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Which side panel is open. Panels are dialogs, so they show above bottom sheets too. */
object Panels {
    /** IPA symbol guide (left), scrolled to this symbol; null = closed. */
    var guide by mutableStateOf<String?>(null)
    /** Search + saved words (right). */
    var words by mutableStateOf(false)
    /** Speaks a word slowly; set by MainActivity. */
    var say: (String) -> Unit = {}
}

// ---------------------------------------------------------------- symbol guide (same content as the PDF IPA Reader extension)

class Sym(val sym: String, val name: String, val examples: List<String>, val tip: String, val tech: String)
class SymSection(val title: String, val items: List<Sym>)

val IPA_GUIDE = listOf(
    SymSection("Vowels", listOf(
        Sym("i", "Long \"ee\"", listOf("see", "meet", "key"), "Like Spanish \"i\", but held a little longer.", "close front unrounded vowel"),
        Sym("ɪ", "Short, relaxed \"i\"", listOf("sit", "ship", "busy"), "Between Spanish \"i\" and \"e\". Not \"i\": ship ≠ sheep.", "near-close near-front unrounded vowel"),
        Sym("ɛ", "Short \"e\"", listOf("bed", "said", "head"), "Close to Spanish \"e\" in \"perro\".", "open-mid front unrounded vowel"),
        Sym("æ", "Open \"a\" with spread lips", listOf("cat", "bad", "man"), "Say Spanish \"a\" while smiling toward \"e\". bad ≠ bed.", "near-open front unrounded vowel"),
        Sym("ɑ", "Open \"ah\"", listOf("father", "hot", "stop"), "Like Spanish \"a\", mouth wide open.", "open back unrounded vowel"),
        Sym("ɔ", "Open \"aw\"", listOf("law", "thought", "all"), "Like Spanish \"o\" with the mouth more open. Many US speakers say ɑ instead.", "open-mid back rounded vowel"),
        Sym("ʊ", "Short, relaxed \"u\"", listOf("book", "put", "good"), "Shorter and looser than Spanish \"u\". full ≠ fool.", "near-close near-back rounded vowel"),
        Sym("u", "Long \"oo\"", listOf("food", "blue", "you"), "Like Spanish \"u\", held longer.", "close back rounded vowel"),
        Sym("ʌ", "Short \"uh\" (stressed)", listOf("cup", "love", "money"), "A short, central \"a\". Don’t say Spanish \"o\" or \"u\".", "open-mid back unrounded vowel (in US English, closer to a central vowel)"),
        Sym("ə", "Schwa: weak \"uh\"", listOf("about", "banana", "the"), "The most common English sound. Unstressed vowels shrink to this.", "mid central vowel (schwa)"),
        Sym("ɝ", "Stressed \"er\"", listOf("bird", "her", "work"), "No vowel before the r: tongue curls back and stays. Not \"beerd\".", "r-coloured open-mid central unrounded vowel (stressed)"),
        Sym("ɚ", "Unstressed \"er\"", listOf("teacher", "better", "doctor"), "A schwa coloured by r. Short and weak.", "r-coloured mid central vowel (unstressed)"),
        Sym("e", "Part of eɪ", listOf("say"), "In this dictionary e appears inside eɪ (see diphthongs).", "close-mid front unrounded vowel"),
        Sym("o", "Part of oʊ", listOf("go"), "In this dictionary o appears inside oʊ (see diphthongs).", "close-mid back rounded vowel"),
        Sym("a", "Part of aɪ / aʊ", listOf("my", "now"), "In this dictionary a appears inside aɪ and aʊ (see diphthongs).", "open front unrounded vowel"),
    )),
    SymSection("Diphthongs (gliding vowels)", listOf(
        Sym("eɪ", "\"ay\"", listOf("say", "day", "great"), "Like Spanish \"ei\" in \"rey\".", "closing diphthong: close-mid front → near-close near-front, unrounded"),
        Sym("aɪ", "\"eye\"", listOf("my", "time", "buy"), "Like Spanish \"ai\" in \"hay\".", "closing diphthong: open front → near-close near-front, unrounded"),
        Sym("ɔɪ", "\"oy\"", listOf("boy", "choice", "noise"), "Like Spanish \"oy\" in \"hoy\".", "closing diphthong: open-mid back rounded → near-close near-front unrounded"),
        Sym("aʊ", "\"ow\"", listOf("now", "house", "out"), "Like Spanish \"au\" in \"auto\".", "closing diphthong: open front unrounded → near-close near-back rounded"),
        Sym("oʊ", "\"oh\"", listOf("go", "home", "know"), "Spanish \"o\" that glides to \"u\". Don’t keep it flat.", "closing diphthong: close-mid back → near-close near-back, rounded"),
    )),
    SymSection("Consonants", listOf(
        Sym("p", "p", listOf("pen", "happy"), "At the start of a word, add a puff of air: \"pʰen\".", "voiceless bilabial plosive"),
        Sym("b", "b", listOf("bad", "baby"), "Always lips closed, even between vowels (Spanish softens it).", "voiced bilabial plosive"),
        Sym("t", "t", listOf("ten", "water"), "Tongue on the ridge behind the teeth, not on the teeth. Between vowels in US English it often sounds like a quick \"r\": water.", "voiceless alveolar plosive"),
        Sym("d", "d", listOf("dog", "ladder"), "Full stop, not the soft Spanish \"d\" of \"cada\".", "voiced alveolar plosive"),
        Sym("k", "k", listOf("cat", "key"), "Puff of air at the start of a word.", "voiceless velar plosive"),
        Sym("ɡ", "hard g", listOf("go", "bag"), "Always hard, as in Spanish \"gato\" — never \"j\".", "voiced velar plosive"),
        Sym("f", "f", listOf("fish", "phone"), "Same as Spanish.", "voiceless labiodental fricative"),
        Sym("v", "v", listOf("very", "love"), "Top teeth on lower lip, with voice. Not \"b\": very ≠ berry.", "voiced labiodental fricative"),
        Sym("θ", "voiceless \"th\"", listOf("think", "three", "month"), "Like Castilian \"z\" in \"zapato\": tongue between the teeth. Not \"t\" or \"s\".", "voiceless dental fricative"),
        Sym("ð", "voiced \"th\"", listOf("this", "mother", "the"), "Like θ with voice. Close to the soft Spanish \"d\" in \"cada\".", "voiced dental fricative"),
        Sym("s", "s", listOf("see", "city"), "Same as Spanish. Never add \"e\" before it: \"school\", not \"eschool\".", "voiceless alveolar fricative"),
        Sym("z", "z (buzzing)", listOf("zoo", "is", "dogs"), "A buzzing \"s\" with voice. Spanish doesn’t have it — feel your throat vibrate.", "voiced alveolar fricative"),
        Sym("ʃ", "\"sh\"", listOf("she", "nation"), "The \"shh\" for silence.", "voiceless postalveolar fricative"),
        Sym("ʒ", "\"zh\"", listOf("vision", "measure"), "Like ʃ with voice. Like the Argentine \"ll\" in \"calle\".", "voiced postalveolar fricative"),
        Sym("tʃ", "\"ch\"", listOf("church", "watch"), "Like Spanish \"ch\".", "voiceless postalveolar affricate"),
        Sym("dʒ", "\"j\" / soft \"g\"", listOf("job", "judge", "age"), "Like tʃ with voice. Not Spanish \"y\" or \"j\".", "voiced postalveolar affricate"),
        Sym("h", "h", listOf("hat", "who"), "Just breath, much softer than Spanish \"j\".", "voiceless glottal fricative"),
        Sym("m", "m", listOf("man", "come"), "Same as Spanish.", "bilabial nasal"),
        Sym("n", "n", listOf("no", "ten"), "Same as Spanish.", "alveolar nasal"),
        Sym("ŋ", "\"ng\"", listOf("sing", "think"), "Back of the tongue up, no hard \"g\" at the end: sing, not \"sing-g\".", "velar nasal"),
        Sym("l", "l", listOf("let", "feel"), "At the end of a word it is \"dark\": tongue back, as in \"feel\".", "alveolar lateral approximant"),
        Sym("ɹ", "English r", listOf("red", "very", "car"), "Tongue curls back and never touches the roof. Never rolled or tapped like Spanish \"r\".", "alveolar approximant"),
        Sym("w", "w", listOf("we", "one"), "Like Spanish \"hu\" in \"huevo\".", "voiced labial-velar approximant"),
        Sym("j", "\"y\"", listOf("yes", "you"), "Like Spanish \"y\" in \"yo\" said softly — not \"ll\" or \"j\".", "palatal approximant"),
    )),
    SymSection("Stress marks", listOf(
        Sym("ˈ", "Primary stress", listOf("photograph", "about"), "The syllable after this mark is the loud, long one.", "primary stress mark (suprasegmental)"),
        Sym("ˌ", "Secondary stress", listOf("photographic", "understand"), "The syllable after this mark gets lighter stress.", "secondary stress mark (suprasegmental)"),
        Sym("*", "Approximate", emptyList(), "Shown after a transcription when the exact word wasn’t found and a related form was used.", "not an IPA symbol: this app’s marker for an approximate match"),
    )),
)

private val SYMBOLS = IPA_GUIDE.flatMap { s -> s.items.map { it.sym } }.toSet()

/** IPA string → symbol ranges (two-letter diphthongs/affricates first); characters not in the guide are skipped. */
fun ipaTokens(ipa: String): List<IntRange> {
    val out = mutableListOf<IntRange>(); var i = 0
    while (i < ipa.length) {
        val two = if (i + 1 < ipa.length) ipa.substring(i, i + 2) else ""
        when {
            two in SYMBOLS -> { out += i..i + 1; i += 2 }
            ipa[i].toString() in SYMBOLS -> { out += i..i; i++ }
            else -> i++
        }
    }
    return out
}

/** "/ipa/" text whose symbols open the guide when tapped (like the extension). */
@Composable
fun IpaText(ipa: String, style: TextStyle, modifier: Modifier = Modifier, slashes: Boolean = true) {
    val text = if (slashes) "/$ipa/" else ipa
    val off = if (slashes) 1 else 0
    val ranges = remember(ipa) { ipaTokens(ipa).map { (it.first + off)..(it.last + off) } }
    val annotated = remember(text, ranges) {
        buildAnnotatedString {
            append(text)
            for (r in ranges) addStyle(SpanStyle(textDecoration = TextDecoration.Underline), r.first, r.last + 1)
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(annotated, modifier.pointerInput(text) {
        detectTapGestures { pos ->
            val o = layout?.getOffsetForPosition(pos) ?: return@detectTapGestures
            // the tap lands on a character or just after it
            val r = ranges.firstOrNull { o in it } ?: ranges.firstOrNull { o - 1 in it } ?: return@detectTapGestures
            Panels.guide = text.substring(r.first, r.last + 1)
        }
    }, style = style, onTextLayout = { layout = it })
}

// ---------------------------------------------------------------- side panels

/** A panel sliding in from the left or right edge; tap outside or swipe it back to close. */
@Composable
fun SidePanel(fromLeft: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        var drag = 0f
        Box(Modifier.fillMaxSize()
            .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(onDragStart = { drag = 0f }, onDragEnd = {
                    if (if (fromLeft) drag < -80.dp.toPx() else drag > 80.dp.toPx()) onDismiss()
                }) { _, d -> drag += d }
            }) {
            AnimatedVisibility(shown, Modifier.align(if (fromLeft) Alignment.CenterStart else Alignment.CenterEnd),
                enter = slideInHorizontally { if (fromLeft) -it else it }) {
                Column(Modifier.fillMaxHeight().fillMaxWidth(0.88f).background(P.bg)
                    .clickable(remember { MutableInteractionSource() }, null) {}.systemBarsPadding(), content = content)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GuidePanel(target: String) {
    val items = remember { IPA_GUIDE.flatMap { s -> listOf<Any>(s.title) + s.items } }
    val list = rememberLazyListState()
    LaunchedEffect(target) { items.indexOfFirst { it is Sym && it.sym == target }.takeIf { it >= 0 }?.let { list.scrollToItem(maxOf(0, it - 1)) } }
    SidePanel(fromLeft = true, onDismiss = { Panels.guide = null }) {
        Row(Modifier.padding(start = 20.dp, top = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("IPA symbols", Modifier.weight(1f), style = tx(18, 700))
            IconBtn(Ic.close, "Close") { Panels.guide = null }
        }
        LazyColumn(Modifier.weight(1f), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items) { it ->
                if (it is String) Label(it, Modifier.padding(top = 10.dp, start = 4.dp))
                else if (it is Sym) {
                    val on = it.sym == target
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (on) P.tealSoft else P.card)
                        .border(if (on) 1.5.dp else 1.dp, if (on) P.teal else P.line, RoundedCornerShape(16.dp)).padding(16.dp, 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(it.sym, style = ipaStyle(30, P.teal)); Spacer(Modifier.width(14.dp))
                            Text(it.name, style = tx(15, 600))
                        }
                        if (it.examples.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (w in it.examples) {
                                    val ipa = remember(w, Dict.ready) { Dict.lookup(w).ipa }
                                    Row(Modifier.clip(RoundedCornerShape(16.dp)).background(P.bg).border(1.dp, P.line, RoundedCornerShape(16.dp))
                                        .clickable { Panels.say(w) }.padding(10.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        androidx.compose.material3.Icon(Ic.speaker, null, Modifier.height(16.dp), tint = P.teal); Spacer(Modifier.width(6.dp))
                                        Text(w, style = tx(14, 500)); if (ipa != null) { Spacer(Modifier.width(6.dp)); Text("/$ipa/", style = ipaStyle(14, P.muted)) }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(it.tip, style = tx(14, 400, lh = 20))
                        Spacer(Modifier.height(4.dp))
                        Text("/${it.sym}/ : ${it.tech}", style = tx(12, 400, P.muted))
                    }
                }
            }
        }
    }
}

@Composable
fun WordsPanel(store: Store) {
    SidePanel(fromLeft = false, onDismiss = { Panels.words = false }) {
        Row(Modifier.padding(start = 20.dp, top = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Words", Modifier.weight(1f), style = tx(18, 700))
            IconBtn(Ic.close, "Close") { Panels.words = false }
        }
        WordsBody(store, Panels.say, Modifier.weight(1f))
    }
}

/** Open the right panel with a right-to-left swipe anywhere on the screen. */
fun Modifier.swipeForWords() = pointerInput(Unit) {
    // Watches in the Initial pass without consuming, so taps and scrolling inside the screen still work.
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            val c = ev.changes.firstOrNull { it.id == down.id } ?: break
            if (!c.pressed) break
            val dx = c.position.x - down.position.x; val dy = c.position.y - down.position.y
            if (dx < -100.dp.toPx() && kotlin.math.abs(dx) > 2 * kotlin.math.abs(dy)) { Panels.words = true; break }
        }
    }
}
