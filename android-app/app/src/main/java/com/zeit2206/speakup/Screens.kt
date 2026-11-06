package com.zeit2206.speakup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------- home

@Composable
fun HomeScreen(
    store: Store, topic: Topic, level: String, sessions: List<Session>,
    onLevel: (String) -> Unit, onShuffle: () -> Unit, onStart: (Topic, Boolean) -> Unit,
    onSession: (Session) -> Unit, onSaved: () -> Unit, onSettings: () -> Unit, onTheme: () -> Unit, onExam: (Int) -> Unit, onDelete: (Session) -> Unit,
) {
    // Delete needs two taps: trash icon, then "Delete".
    var deleting by remember { mutableStateOf<Long?>(null) }
    var open by remember { mutableStateOf("") }
    var choose by remember { mutableStateOf(false) }
    if (choose) ExamSheet({ choose = false }) { choose = false; onExam(it) }
    Column(Modifier.fillMaxSize().background(P.bg).systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Speak Up", Modifier.weight(1f), style = tx(30, 700))
            IconBtn(Ic.book, "Saved words", onClick = onSaved)
            IconBtn(Ic.sliders, "Settings", onClick = onSettings)
            IconBtn(if (P.dark) Ic.moon else Ic.sun, "Light or dark theme", onClick = onTheme)
        }
        Text("Hi! Ready for a quick chat?", style = tx(15, 400, P.muted))
        Spacer(Modifier.height(24.dp))
        Label("Your level")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (l in listOf("A2", "B1", "B2", "C1")) {
                val on = l == level
                Box(Modifier.size(70.dp, 40.dp).clip(RoundedCornerShape(20.dp)).background(if (on) P.ink else P.card)
                    .border(1.dp, if (on) P.ink else P.line, RoundedCornerShape(20.dp)).clickable { onLevel(l) }, contentAlignment = Alignment.Center) {
                    Text(l, style = tx(15, 600, if (on) P.bg else P.ink))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(P.teal).padding(22.dp)) {
            Text("TODAY'S TOPIC", style = tx(11, 600, Color(0xFFBFE3DD)))
            Spacer(Modifier.height(6.dp))
            Text(topic.title, style = tx(26, 700, Color.White))
            Spacer(Modifier.height(6.dp))
            Text(topic.blurb, style = tx(14, 400, Color(0xFFDDF0EC), lh = 20))
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("New topic", Ic.shuffle, bg = P.tealDark, height = 44.dp, textSize = 14, onClick = onShuffle)
                Spacer(Modifier.width(14.dp))
                Text("Suggested topic", style = tx(12, 400, Color(0xFFBFE3DD)))
            }
        }
        Spacer(Modifier.height(12.dp))
        val go = { if (open.isNotBlank()) onStart(Topic(open.trim().replaceFirstChar { it.uppercase() }, ""), true) }
        Row(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(P.card).border(1.5.dp, P.teal, RoundedCornerShape(28.dp))
            .padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.chat, null, Modifier.size(22.dp), tint = P.teal)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (open.isEmpty()) Text("Or talk about anything…", style = tx(15, 400, P.muted))
                BasicTextField(open, { open = it }, Modifier.fillMaxWidth(), textStyle = tx(15), singleLine = true, cursorBrush = SolidColor(P.teal),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { go() }))
            }
            Box(Modifier.size(40.dp).clip(CircleShape).background(P.tealSoft).clickable { go() }, contentAlignment = Alignment.Center) {
                Icon(Ic.arrow, "Start open topic", Modifier.size(22.dp), tint = P.teal)
            }
        }
        Spacer(Modifier.height(16.dp))
        Pill("Start conversation", null, Modifier.fillMaxWidth(), bg = P.coral) { onStart(topic, false) }
        Spacer(Modifier.height(12.dp))
        Pill("IELTS Speaking test", Ic.book, Modifier.fillMaxWidth(), bg = P.card, fg = P.teal, border = P.teal) { choose = true }
        if (sessions.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            Label("Recent")
            Spacer(Modifier.height(8.dp))
            for (s in sessions.take(5)) {
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(16.dp)).background(P.card).border(1.dp, P.line, RoundedCornerShape(16.dp))
                    .clickable { onSession(s) }.padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.topic, style = tx(15, 600), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Spacer(Modifier.height(3.dp))
                        Text(listOfNotNull(dayLabel(s.date), "${s.turns} turns", if (s.open) "open topic" else null).joinToString(" · "), style = tx(13, 400, P.muted))
                    }
                    val answered = s.own + s.read
                    if (deleting == s.date) {
                        Text("Delete", Modifier.clip(RoundedCornerShape(14.dp)).background(P.coral).clickable { deleting = null; onDelete(s) }
                            .padding(14.dp, 7.dp), style = tx(13, 600, Color.White))
                        Spacer(Modifier.width(4.dp))
                        IconBtn(Ic.close, "Cancel") { deleting = null }
                    } else {
                        Box(Modifier.size(70.dp, 8.dp).clip(RoundedCornerShape(4.dp)).background(P.line)) {
                            if (answered > 0) Box(Modifier.fillMaxWidth(s.own.toFloat() / answered).height(8.dp).clip(RoundedCornerShape(4.dp)).background(P.teal))
                        }
                        Spacer(Modifier.width(4.dp))
                        IconBtn(Ic.trash, "Delete conversation") { deleting = s.date }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Pick what to practise: full test or one part, each with what you're expected to do. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ExamSheet(onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    androidx.compose.material3.ModalBottomSheet(onDismiss, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = P.card, tonalElevation = 0.dp, scrimColor = P.scrim) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding()) {
            Text("IELTS Speaking", Modifier.padding(start = 4.dp), style = tx(20, 700))
            Text("What do you want to practise?", Modifier.padding(start = 4.dp, bottom = 12.dp), style = tx(14, 400, P.muted))
            for (p in EXAM_PARTS) {
                Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(16.dp)).background(if (p.part == 0) P.tealSoft else P.bg)
                    .border(1.dp, if (p.part == 0) P.teal else P.line, RoundedCornerShape(16.dp)).clickable { onPick(p.part) }.padding(16.dp, 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.title, Modifier.weight(1f), style = tx(16, 700, if (p.part == 0) P.teal else P.ink))
                        Text(p.time, style = tx(12, 500, P.muted))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(p.note, style = tx(14, 400, P.ink, lh = 20))
                }
            }
        }
    }
}

private fun Modifier.navigationBarsPadding() = this.then(Modifier.navigationBarsPadding())

fun dayLabel(t: Long): String {
    val c = Calendar.getInstance(); val d = Calendar.getInstance().apply { timeInMillis = t }
    fun same(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (same(c, d)) return "Today"
    c.add(Calendar.DAY_OF_YEAR, -1)
    if (same(c, d)) return "Yesterday"
    return SimpleDateFormat("MMM d", Locale.US).format(Date(t))
}

// ---------------------------------------------------------------- summary

@Composable
fun SummaryScreen(s: Session, onSay: (String) -> Unit, onNew: () -> Unit, onHome: () -> Unit, onRepeat: (() -> Unit)? = null, onContinue: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize().background(P.bg).systemBarsPadding().padding(horizontal = 24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(28.dp))
            Text(if (s.own + s.read == 0) "Session saved" else "Nice work!", style = tx(30, 700))
            Spacer(Modifier.height(6.dp))
            Text("${s.topic} · ${s.turns} turns · ${s.level}", style = tx(14, 400, P.muted))
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat(s.own, if (s.own == 1) "own answer" else "own answers", P.teal, P.tealSoft, Modifier.weight(1f))
                Stat(s.read, "read aloud", P.coral, P.coralSoft, Modifier.weight(1f))
            }
            if (s.keep.isNotEmpty()) {
                Spacer(Modifier.height(26.dp))
                Label("Phrases to keep")
                Spacer(Modifier.height(8.dp))
                for (p in s.keep) Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(14.dp)).background(P.card)
                    .border(1.dp, P.line, RoundedCornerShape(14.dp)).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(p, Modifier.weight(1f).padding(vertical = 13.dp), style = tx(15, 500))
                    IconBtn(Ic.speaker, "Hear it", P.teal) { onSay(p) }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(when {
                s.own + s.read == 0 -> "Next time: answer at least one question."
                s.read >= s.own -> "Next time: try one more answer on your own."
                else -> "Great — most answers were in your own words."
            }, style = tx(13, 400, P.muted))
        }
        if (onContinue != null) {
            Pill("Continue this conversation", Ic.arrow, Modifier.fillMaxWidth(), onClick = onContinue)
            Spacer(Modifier.height(12.dp))
        }
        if (onRepeat != null) {
            Pill("Repeat this conversation", Ic.repeat, Modifier.fillMaxWidth(), bg = P.card, fg = P.teal, border = P.teal, onClick = onRepeat)
            Spacer(Modifier.height(12.dp))
        }
        Pill(when { !s.exam -> "New topic"; s.part == 0 -> "New IELTS test"; else -> "New Part ${s.part} practice" }, null, Modifier.fillMaxWidth(), bg = P.coral, onClick = onNew)
        Spacer(Modifier.height(12.dp))
        Pill("Home", null, Modifier.fillMaxWidth(), bg = P.bg, fg = P.ink, border = P.line, onClick = onHome)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Stat(n: Int, label: String, fg: Color, bg: Color, modifier: Modifier) =
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(bg).padding(18.dp, 14.dp)) {
        Text("$n", style = tx(34, 700, fg)); Text(label, style = tx(14, 500, fg))
    }

// ---------------------------------------------------------------- saved words

@Composable
fun SavedScreen(store: Store, onSay: (String) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(P.bg).systemBarsPadding().imePadding()) {
        Row(Modifier.padding(start = 4.dp, top = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ic.back, "Back", P.ink, onBack); Text("Saved words", style = tx(18, 700))
        }
        WordsBody(store, onSay, Modifier.weight(1f))
    }
}

/** Search box + saved words: the Saved words screen and the right-hand panel. */
@Composable
fun WordsBody(store: Store, onSay: (String) -> Unit, modifier: Modifier = Modifier) {
    var q by remember { mutableStateOf("") }
    var words by remember { mutableStateOf(store.words()) }
    var tick by remember { mutableStateOf(0) }
    run {
        Column(modifier.imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(P.card).border(1.dp, P.line, RoundedCornerShape(26.dp))
                .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Ic.search, null, Modifier.size(20.dp), tint = P.muted); Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (q.isEmpty()) Text("Look up any word…", style = tx(15, 400, P.muted))
                    BasicTextField(q, { q = it }, Modifier.fillMaxWidth(), textStyle = tx(15), singleLine = true, cursorBrush = SolidColor(P.teal))
                }
            }
            if (q.isNotBlank()) {
                val e = remember(q, Dict.ready, tick) { Dict.lookup(q) }
                Spacer(Modifier.height(12.dp))
                WordRow(e.word, e.ipa, e.es, onSay, if (store.isSaved(e.word)) null else ({ store.save(e); words = store.words(); tick++ }), null)
            }
            Spacer(Modifier.height(18.dp))
            Label(if (words.isEmpty()) "No saved words yet — tap a word in a chat, then Save" else "${words.size} saved")
            Spacer(Modifier.height(8.dp))
            for (w in words) WordRow(w.word, w.ipa, w.es, onSay, null) { store.unsave(w.word); words = store.words() }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun WordRow(word: String, ipa: String?, es: String?, onSay: (String) -> Unit, onSave: (() -> Unit)?, onDelete: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(16.dp)).background(P.card).border(1.dp, P.line, RoundedCornerShape(16.dp))
        .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(word, style = tx(16, 600)); Spacer(Modifier.width(10.dp))
                if (ipa != null) IpaText(ipa, ipaStyle(16))
            }
            if (es != null) Text(es, style = tx(13, 400, P.muted))
        }
        IconBtn(Ic.speaker, "Pronounce", P.teal) { onSay(word) }
        if (onSave != null) IconBtn(Ic.star, "Save", P.amber, onSave)
        if (onDelete != null) IconBtn(Ic.trash, "Remove", P.muted, onDelete)
    }
}

// ---------------------------------------------------------------- settings

@Composable
fun SettingsScreen(store: Store, voice: Voice, onSay: (String, Float) -> Unit, onBack: () -> Unit) {
    var url by remember { mutableStateOf(store.serverUrl) }
    var token by remember { mutableStateOf(store.token) }
    var rate by remember { mutableFloatStateOf(store.rate) }
    var auto by remember { mutableStateOf(store.autoSpeak) }
    var voiceName by remember { mutableStateOf(store.voiceName) }
    val voices = remember { voice.english() }
    var result by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(P.bg).systemBarsPadding().imePadding()) {
        Row(Modifier.padding(start = 4.dp, top = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ic.back, "Back", P.ink) { store.serverUrl = url; store.token = token; onBack() }; Text("Settings", style = tx(18, 700))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(12.dp))
            Label("Your PC (Speak Up server)")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(url, { url = it; store.serverUrl = it }, Modifier.fillMaxWidth(), label = { Text("Server address") }, singleLine = true, textStyle = tx(15))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(token, { token = it; store.token = it }, Modifier.fillMaxWidth(), label = { Text("Token (from server/token.txt)") }, singleLine = true, textStyle = tx(15))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("Test connection", Ic.check, height = 44.dp, textSize = 14) {
                    result = "Testing…"
                    scope.launch { result = try { "Connected · model ${store.server().health()}" } catch (e: ServerError) { e.message } }
                }
                Spacer(Modifier.width(12.dp))
                result?.let { Text(it, Modifier.weight(1f), style = tx(13, 500, if (it.startsWith("Connected")) P.teal else P.coral)) }
            }
            Spacer(Modifier.height(30.dp))
            Label("Voice")
            Spacer(Modifier.height(8.dp))
            Text("Speaking speed: ${"%.1f".format(rate)}×", style = tx(15, 500))
            Slider(rate, { rate = it }, valueRange = 0.6f..1.2f, steps = 5, onValueChangeFinished = { store.rate = rate; onSay("This is how fast I'll speak.", rate) },
                colors = SliderDefaults.colors(thumbColor = P.teal, activeTrackColor = P.teal))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Read each question aloud", Modifier.weight(1f), style = tx(15, 500))
                Switch(auto, { auto = it; store.autoSpeak = it }, colors = SwitchDefaults.colors(checkedTrackColor = P.teal))
            }
            Spacer(Modifier.height(16.dp))
            Text("Voice", style = tx(15, 500))
            Spacer(Modifier.height(6.dp))
            if (voices.isEmpty()) Text("No other voices found. Add English voices in Android Settings → Text-to-speech.", style = tx(13, 400, P.muted))
            for ((id, label) in listOf<Pair<String?, String>>(null to "Default (US)") + voices) {
                val on = id == voiceName
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(12.dp)).background(if (on) P.tealSoft else P.card)
                    .border(1.dp, if (on) P.teal else P.line, RoundedCornerShape(12.dp))
                    .clickable { voiceName = id; store.voiceName = id; voice.use(id); onSay("Hi! This is how I sound.", rate) }
                    .padding(14.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), style = tx(14, if (on) 600 else 400, if (on) P.teal else P.ink))
                    if (on) Icon(Ic.check, null, Modifier.size(18.dp), tint = P.teal)
                }
            }
            Spacer(Modifier.height(30.dp))
            Text("The app sends what you say (as text) to the server on your PC, which asks Claude for the next question, suggestions and a more natural version. The PC must be on, with Tailscale connected.",
                style = tx(13, 400, P.muted, lh = 19))
        }
    }
}
