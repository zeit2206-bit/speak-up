package com.zeit2206.speakup

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

enum class Kind { OWN, READ }

/** One chat bubble. App bubbles have [fromApp]; the learner's carry how they answered. */
data class Msg(
    val fromApp: Boolean,
    val text: String,
    val kind: Kind? = null,
    val read: ReadScore? = null,
    val rephrase: String? = null,
    val repeat: ReadScore? = null,
    /** App messages: the Help answers offered with this question. */
    val sugg: List<String> = emptyList(),
)

/** A recorded answer waiting for Try again / Send. */
data class Draft(val text: String, val kind: Kind, val unsure: Map<Int, String> = emptyMap(), val read: ReadScore? = null)

sealed interface Phase {
    data object Idle : Phase
    data class Listening(val partial: String, val target: String?) : Phase
    data object Thinking : Phase
    data class Failed(val message: String) : Phase
}

/** A running conversation. Lives as long as the conversation screen. */
class Chat(
    private val context: Context,
    private val scope: CoroutineScope,
    private val store: Store,
    private val voice: Voice,
    val topic: String,
    val blurb: String?,
    val open: Boolean,
    val level: String,
    /** IELTS Speaking mock test: the server plays the examiner (Part 1 → 2 → 3). [blurb] is the Part 2 cue card. */
    val exam: Boolean = false,
    /** Repeating a saved conversation: these questions, in order (Claude only rephrases the answers). */
    val script: List<Turn>? = null,
    /** IELTS: 0 = full test, 1/2/3 = only that part. */
    val part: Int = 0,
    /** Continuing a saved conversation: its messages (Claude asks a new question next). */
    val resume: List<Line>? = null,
) {
    val messages = mutableStateListOf<Msg>()
    var suggestions by mutableStateOf<List<String>>(emptyList()); private set
    var phase by mutableStateOf<Phase>(Phase.Idle); private set
    var draft by mutableStateOf<Draft?>(null); private set
    /** Index of the app message a "Repeat it" recording is for. */
    var repeating by mutableStateOf<Int?>(null); private set
    var notice by mutableStateOf<String?>(null)

    var own = 0; private set
    var read = 0; private set
    var skipped = 0; private set
    val keep = mutableStateListOf<String>()
    val turns get() = messages.count { it.fromApp }

    private val recognizer = Recognizer()
    private var retry: (() -> Unit)? = null
    private var listenJob: Job? = null

    /** Fixed id of this conversation in Recent (its start time), so autosaves update one entry. */
    private val started = System.currentTimeMillis()

    /** Saved after every turn, so closing the app doesn't lose the conversation. */
    private fun autosave() { if (own + read + skipped > 0) store.putSession(session()) }

    /** Index in [script] of the question on screen. */
    private var pos = 0

    fun start() {
        val first = script?.firstOrNull()
        if (!resume.isNullOrEmpty()) {
            messages += resume.map { Msg(fromApp = it.app, text = it.text, kind = if (it.app) null else Kind.OWN) }
            request(JSONObject().put("mode", "resume")) { r -> addApp(r) }
        }
        else if (first != null) addApp(Reply(first.q, first.sugg, null, emptyList()))
        else request(JSONObject().put("mode", "start").put("openTopic", open)) { r -> addApp(r) }
    }

    /** Next scripted question, or null after the last one. */
    private fun nextTurn() = script?.getOrNull(pos + 1)

    private fun history() = JSONArray(messages.map { JSONObject().put("role", if (it.fromApp) "app" else "me").put("text", it.text) })

    private fun request(extra: JSONObject, onReply: (Reply) -> Unit) {
        val body = extra.put("topic", if (open) topic else "$topic: ${blurb.orEmpty()}").put("level", level).put("history", history()).put("exam", exam).put("part", part)
        retry = { request(extra, onReply) }
        phase = Phase.Thinking
        scope.launch {
            try {
                val r = store.server().turn(body)
                phase = Phase.Idle; retry = null
                onReply(r)
            } catch (e: ServerError) {
                phase = Phase.Failed(e.message ?: "Something went wrong")
            }
        }
    }

    fun retryLast() { retry?.invoke() }

    private fun addApp(r: Reply) {
        messages += Msg(fromApp = true, text = r.message, sugg = r.suggestions)
        suggestions = r.suggestions
        for (k in r.keep) if (k !in keep) keep += k
        if (store.autoSpeak) voice.say(r.message, store.rate)
        autosave()
    }

    // ------------------------------------------------------------ recording

    val canAnswer get() = phase == Phase.Idle && draft == null && messages.lastOrNull()?.fromApp == true

    /** Mic pressed. [target]: a suggestion being read aloud; null = own answer. */
    fun startListening(target: String?) {
        if (phase != Phase.Idle) return
        voice.stop()
        notice = null
        phase = Phase.Listening("", target)
        listenJob = scope.launch {
            val heard = recognizer.listen(context) { p -> (phase as? Phase.Listening)?.let { phase = it.copy(partial = p) } }
            phase = Phase.Idle
            when {
                heard.error != null -> notice = heard.error
                heard.best.isBlank() -> notice = "I didn't hear anything. Hold the mic while you speak."
                target != null -> draft = Draft(target, Kind.READ, read = scoreReading(target, heard))
                else -> draft = Draft(tidy(heard.best), Kind.OWN, unsureWords(heard))
            }
        }
    }

    /** Mic released. */
    fun stopListening() = recognizer.stop()

    fun discardDraft() { draft = null }

    fun editDraft(text: String) { draft = draft?.copy(text = text.trim(), unsure = emptyMap()) }

    fun sendDraft() {
        val d = draft ?: return
        if (d.text.isBlank()) return
        draft = null
        if (d.kind == Kind.OWN) own++ else read++
        messages += Msg(fromApp = false, text = d.text, kind = d.kind, read = d.read)
        autosave()
        val mine = messages.lastIndex
        val body = JSONObject().put("mode", "answer").put("kind", if (d.kind == Kind.OWN) "own" else "read").put("answer", d.text)
        val next = nextTurn()
        // After the last saved question the conversation just carries on (Claude asks new ones).
        if (next != null) body.put("next", next.q)
        request(body) { r ->
            if (d.kind == Kind.OWN && r.rephrase != null && !sameText(r.rephrase, d.text)) messages[mine] = messages[mine].copy(rephrase = r.rephrase)
            // Repeats: the saved question and Help answers, not Claude's wording.
            if (next != null) { pos++; addApp(r.copy(message = next.q, suggestions = next.sugg)) }
            else { if (script != null) pos = script.size; addApp(r) }
        }
    }

    fun skip() {
        if (!canAnswer) return
        nextTurn()?.let { next -> skipped++; pos++; addApp(Reply(next.q, next.sugg, null, emptyList())); return }
        skipped++
        request(JSONObject().put("mode", "skip")) { r -> addApp(r) }
    }

    /** "Repeat it" on a rephrase: record and score it like a reading. */
    fun repeatRephrase(index: Int) {
        val target = messages.getOrNull(index)?.rephrase ?: return
        if (phase != Phase.Idle) return
        voice.stop()
        repeating = index
        phase = Phase.Listening("", target)
        listenJob = scope.launch {
            val heard = recognizer.listen(context) { p -> (phase as? Phase.Listening)?.let { phase = it.copy(partial = p) } }
            phase = Phase.Idle; repeating = null
            if (heard.best.isBlank()) notice = heard.error ?: "I didn't hear anything. Hold the mic while you speak."
            else messages[index] = messages[index].copy(repeat = scoreReading(target, heard))
        }
    }

    fun say(text: String, slow: Boolean = false) = voice.say(text, if (slow) store.rate * 0.8f else store.rate)

    fun session() = Session(topic, open, level, started, turns, own, read, skipped, keep.toList(), blurb, exam,
        messages.filter { it.fromApp }.map { Turn(it.text, it.sugg) }.ifEmpty { script.orEmpty() }, part,
        messages.map { Line(it.fromApp, it.text) })

    fun close() { listenJob?.cancel(); voice.stop() }
}

private fun sameText(a: String, b: String) =
    a.lowercase().replace(Regex("[^a-z0-9 ]"), "").trim() == b.lowercase().replace(Regex("[^a-z0-9 ]"), "").trim()
