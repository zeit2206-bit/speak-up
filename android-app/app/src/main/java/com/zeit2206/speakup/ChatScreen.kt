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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** [ask]: checks the mic permission; returns false (and asks) when not granted yet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(chat: Chat, store: Store, ask: () -> Boolean, onEnd: () -> Unit) {
    var help by remember { mutableStateOf(false) }
    var word by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val phase = chat.phase
    val draft = chat.draft
    val lookup: (String) -> Unit = { word = it }
    val itemCount = chat.messages.size + 1

    LaunchedEffect(chat.messages.size, draft, phase, chat.messages.lastOrNull()) {
        delay(50); list.animateScrollToItem(maxOf(0, itemCount - 1))
    }
    LaunchedEffect(draft) { if (draft != null) help = false }

    Column(Modifier.fillMaxSize().background(P.bg).statusBarsPadding()) {
        // top bar
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ic.back, "End", P.ink, onEnd)
            Text(chat.topic, Modifier.weight(1f), style = tx(16, 600), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text("Turn ${maxOf(1, chat.turns)}", style = tx(13, 500, P.muted))
            Spacer(Modifier.width(12.dp))
            Box(Modifier.clip(RoundedCornerShape(16.dp)).background(P.card).border(1.dp, P.line, RoundedCornerShape(16.dp))
                .clickable(onClick = onEnd).padding(horizontal = 16.dp, vertical = 6.dp)) { Text("End", style = tx(14, 600, P.coral)) }
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp, 12.dp, 20.dp, 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(chat.messages) { i, m ->
                if (m.fromApp) AppBubble(m.text, lookup) { chat.say(m.text) }
                else MyBubble(m, i, chat, ask, lookup)
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        phase is Phase.Listening && chat.repeating == null -> ListeningBubble(phase.partial, phase.target)
                        draft != null -> DraftView(draft, lookup, longTurn(chat))
                        phase == Phase.Thinking -> Typing()
                        phase is Phase.Failed -> ErrorCard(phase.message) { chat.retryLast() }
                    }
                    chat.notice?.let { Text(it, style = tx(13, 500, P.coral)) }
                }
            }
        }

        // bottom bar
        Column(Modifier.fillMaxWidth().background(P.card).navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(P.line))
            if (draft != null) DraftBar(draft, onRetry = { chat.discardDraft() }, onSend = { chat.sendDraft() }, onEdit = { editing = true })
            else AnswerBar(chat, ask, onHelp = { help = true })
        }
    }

    if (help) HelpSheet(chat, ask, lookup) { help = false }
    word?.let { WordSheet(it, store, onSay = { w -> chat.say(w, slow = true) }) { word = null } }
    if (editing && draft != null) EditDialog(draft.text, onDone = { chat.editDraft(it); editing = false }) { editing = false }
}

@Composable
private fun AppBubble(text: String, onWord: (String) -> Unit, onReplay: () -> Unit) {
    Column(Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(20.dp)).background(P.card).border(1.dp, P.line, RoundedCornerShape(20.dp)).padding(16.dp, 14.dp)) {
        WordText(text, tx(16, 400, lh = 23), onWord = onWord)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onReplay).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.speaker, null, Modifier.size(18.dp), tint = P.teal); Spacer(Modifier.width(6.dp)); Text("Replay", style = tx(12, 500, P.teal))
        }
    }
}

@Composable
private fun Mine(content: @Composable () -> Unit) = Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { content() }

private fun missMarks(s: ReadScore) = s.words.withIndex().filter { it.value != Status.GOOD }.associate { it.index to missMark }

@Composable
private fun MyBubble(m: Msg, index: Int, chat: Chat, ask: () -> Boolean, onWord: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (m.kind == Kind.READ && m.read != null) {
            Box(Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(20.dp)).background(P.card).border(1.5.dp, P.teal, RoundedCornerShape(20.dp)).padding(16.dp, 14.dp)) {
                WordText(m.text, tx(16, 400, lh = 23), marks = missMarks(m.read), onWord = onWord)
            }
            ScoreChip(m.read, "Read aloud")
        } else {
            Box(Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(20.dp)).background(P.teal).padding(16.dp, 14.dp)) {
                WordText(m.text, tx(16, 400, Color.White, lh = 23), onWord = onWord)
            }
        }
        m.rephrase?.let { RephraseCard(it, m.repeat, index, chat, ask, onWord) }
    }
}

@Composable
private fun RephraseCard(text: String, repeat: ReadScore?, index: Int, chat: Chat, ask: () -> Boolean, onWord: (String) -> Unit) {
    val listening = chat.repeating == index
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(P.amberSoft).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.bulb, null, Modifier.size(20.dp), tint = P.amber); Spacer(Modifier.width(8.dp)); Label("More natural", color = P.amberInk)
        }
        Spacer(Modifier.height(8.dp))
        WordText(text, tx(16, 500, lh = 23), marks = repeat?.let(::missMarks).orEmpty(), onWord = onWord)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill("Listen", Ic.speaker, bg = P.card, fg = P.teal, height = 40.dp, textSize = 14) { chat.say(text) }
            Row(
                Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(if (listening) P.coral else P.teal)
                    .holdToTalk(index, onDown = { if (chat.phase == Phase.Idle && ask()) { chat.repeatRephrase(index); true } else false }, onUp = { chat.stopListening() })
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (listening) Ic.mic else Ic.repeat, null, Modifier.size(18.dp), tint = Color.White); Spacer(Modifier.width(8.dp))
                Text(if (listening) "Listening…" else "Hold to repeat", style = tx(14, 600, Color.White))
            }
        }
        repeat?.let { Spacer(Modifier.height(12.dp)); ScoreChip(it, "Repeated") }
    }
}

@Composable
private fun ListeningBubble(partial: String, target: String?) = Mine {
    Column(Modifier.widthIn(min = 160.dp, max = 296.dp).clip(RoundedCornerShape(20.dp)).background(P.card).dashedBorder(P.coral, 20.dp).padding(16.dp, 12.dp)) {
        Label(if (target != null) "Listening · read it aloud" else "Listening…", color = P.coral)
        Spacer(Modifier.height(6.dp))
        Text(partial.ifEmpty { target ?: "…" }, style = tx(16, 400, if (partial.isEmpty()) P.muted else P.ink, lh = 23))
    }
}

@Composable
/** IELTS Part 2 cue card on screen: the answer should be a 2-minute talk. */
private fun longTurn(chat: Chat) = chat.exam && chat.messages.lastOrNull { it.fromApp }?.text?.contains("You should say") == true

@Composable
private fun DraftView(d: Draft, onWord: (String) -> Unit, longTurn: Boolean = false) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val marks = if (d.read != null) missMarks(d.read) else d.unsure.keys.associateWith { unsureMark }
        Column(Modifier.widthIn(max = 296.dp).clip(RoundedCornerShape(20.dp)).background(P.card).dashedBorder(P.teal, 20.dp).padding(16.dp, 12.dp)) {
            Label(if (d.kind == Kind.READ) "Reading check · not sent" else "Draft · not sent")
            Spacer(Modifier.height(6.dp))
            WordText(d.text, tx(16, 400, lh = 23), marks = marks, onWord = onWord)
        }
        if (longTurn && d.kind == Kind.OWN) {
            val n = wordsOf(d.text).size
            Tip(if (n >= 180) "$n words · about 2 minutes. Well done!"
                else "$n words · aim for about 200 (2 minutes). Next time keep going: add a story, a reason or an example for each point.", onWord)
        }
        val words = wordsOf(d.text)
        if (d.read != null) {
            ScoreChip(d.read, "Read aloud")
            val miss = d.read.firstMiss?.let { words.getOrNull(it) }?.trim { !it.isLetterOrDigit() }
            Tip(if (miss == null) "All words recognized. Send it!"
                else Dict.lookup(miss).ipa.let { ipa -> "“$miss” wasn't clear" + (if (ipa != null) " — /$ipa/" else "") + ". Tap it to hear it, then try again." }, onWord)
        } else {
            d.unsure.entries.firstOrNull()?.let { (i, alt) ->
                val w = words.getOrNull(i)?.trim { !it.isLetterOrDigit() } ?: return@let
                Tip("Not sure I heard “$w”. Did you say “$alt”? Tap the word to hear it.", onWord)
            }
            Column(Modifier.fillMaxWidth()) {
                Text("Is this what you said?", style = tx(15, 600))
                Spacer(Modifier.height(4.dp))
                Text("If not, try again — saying it clearly is the practice.", style = tx(13, 400, P.muted))
            }
        }
    }
}

@Composable
private fun Tip(text: String, onWord: (String) -> Unit) = Box(Modifier.widthIn(max = 296.dp).clip(RoundedCornerShape(16.dp)).background(P.amberSoft).padding(14.dp, 10.dp)) {
    // Words are tappable like everywhere else (the IPA in it is skipped: no letters to look up).
    WordText(text, tx(13, 400, P.amberInk, lh = 19), onWord = { w -> if (w.all { it in 'a'..'z' || it in 'A'..'Z' || it == '\'' || it == '’' }) onWord(w) })
}

@Composable
private fun Typing() {
    var n by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(350); n = (n + 1) % 4 } }
    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(P.card).border(1.dp, P.line, RoundedCornerShape(20.dp)).padding(18.dp, 14.dp)) {
        Text("Thinking" + ".".repeat(n), style = tx(15, 500, P.muted))
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(P.coralSoft).padding(16.dp)) {
        Text(message, style = tx(15, 500, P.ink, lh = 21))
        Spacer(Modifier.height(12.dp))
        Pill("Retry", Ic.repeat, bg = P.coral, height = 40.dp, textSize = 14, onClick = onRetry)
    }
}

@Composable
private fun AnswerBar(chat: Chat, ask: () -> Boolean, onHelp: () -> Unit) {
    val listening = chat.phase is Phase.Listening && chat.repeating == null
    val enabled = chat.canAnswer || listening
    Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(when {
            listening -> "Listening… release to stop"
            chat.phase == Phase.Thinking -> "Claude is thinking…"
            // IELTS Part 2: the cue card is on screen
            chat.exam && chat.messages.lastOrNull()?.let { it.fromApp && "You should say" in it.text } == true ->
                "Part 2: 1 min to prepare, then talk for 2 min (~200 words)"
            else -> "Hold to answer in your own words"
        }, style = tx(13, 400, P.muted))
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                Pill("Help", Ic.bulb, bg = P.amberSoft, fg = P.amberInk, height = 44.dp, textSize = 15) { if (chat.canAnswer && chat.suggestions.isNotEmpty()) onHelp() }
            }
            MicButton(76.dp, listening, enabled, Modifier.holdToTalk(enabled, onDown = {
                if (chat.canAnswer && ask()) { chat.startListening(null); true } else false
            }, onUp = { chat.stopListening() }))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                Text("Skip", Modifier.clip(RoundedCornerShape(8.dp)).clickable { chat.skip() }.padding(12.dp), style = tx(14, 500, P.muted))
            }
        }
    }
}

@Composable
private fun DraftBar(d: Draft, onRetry: () -> Unit, onSend: () -> Unit, onEdit: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(20.dp, 24.dp, 20.dp, 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Pill("Try again", Ic.repeat, Modifier.weight(1f), bg = P.bg, fg = P.ink, border = P.line, onClick = onRetry)
            Pill("Send", Ic.send, Modifier.weight(1f), onClick = onSend)
        }
        Spacer(Modifier.height(6.dp))
        if (d.kind == Kind.OWN) Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onEdit).padding(12.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.pen, null, Modifier.size(18.dp), tint = P.muted); Spacer(Modifier.width(8.dp)); Text("Edit text", style = tx(14, 500, P.muted))
        } else Spacer(Modifier.height(36.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HelpSheet(chat: Chat, ask: () -> Boolean, onWord: (String) -> Unit, onDismiss: () -> Unit) {
    var sel by remember { mutableIntStateOf(0) }
    val listening = chat.phase is Phase.Listening
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = P.card, tonalElevation = 0.dp, scrimColor = P.scrim) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pick one and read it aloud", Modifier.weight(1f).padding(start = 4.dp), style = tx(18, 700))
                IconBtn(Ic.close, "Close", onClick = onDismiss)
            }
            Text("Or close this and try your own answer — it counts more!", Modifier.padding(start = 4.dp, end = 4.dp), style = tx(13, 400, P.muted))
            Spacer(Modifier.height(14.dp))
            // Long answers (IELTS Part 2) need room: the list scrolls, the mic stays visible.
            Column(Modifier.weight(1f, fill = false).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
            chat.suggestions.forEachIndexed { i, s ->
                val on = i == sel
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(16.dp)).background(if (on) P.tealSoft else P.bg)
                        .border(if (on) 1.5.dp else 1.dp, if (on) P.teal else P.line, RoundedCornerShape(16.dp))
                        .clickable { sel = i }.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Words are tappable once the card is selected (first tap selects).
                    WordText(s, tx(15, 400, lh = 21), Modifier.weight(1f), onWord = if (on) onWord else null)
                    IconBtn(Ic.speaker, "Hear it", P.teal) { sel = i; chat.say(s) }
                }
            }
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().padding(bottom = 16.dp), contentAlignment = Alignment.Center) {
                Text(if (listening) "Listening…" else "Hold & read", Modifier.align(Alignment.CenterStart).padding(start = 4.dp), style = tx(13, 500, if (listening) P.coral else P.muted))
                MicButton(64.dp, listening, true, Modifier.holdToTalk(sel, onDown = {
                    val t = chat.suggestions.getOrNull(sel)
                    if (t != null && chat.canAnswer && ask()) { chat.startListening(t); true } else false
                }, onUp = { chat.stopListening() }))
            }
        }
    }
}

@Composable
private fun EditDialog(text: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var t by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(text, androidx.compose.ui.text.TextRange(text.length))) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { delay(100); focus.requestFocus() }
    AlertDialog(onDismiss, containerColor = P.card, tonalElevation = 0.dp,
        title = { Text("Fix what it misheard", style = tx(18, 700)) },
        text = { OutlinedTextField(t, { t = it }, Modifier.fillMaxWidth().focusRequester(focus), textStyle = tx(16)) },
        confirmButton = { TextButton({ onDone(t.text) }) { Text("Done", style = tx(15, 600, P.teal)) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", style = tx(15, 500, P.muted)) } })
}
