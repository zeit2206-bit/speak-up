package com.zeit2206.speakup

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioManager
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.coroutines.resume

// ---------------------------------------------------------------- text-to-speech

class Voice(context: Context, private var wanted: String? = null) {
    private var ready = false
    private val tts = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) { setUs(); wanted?.let(::use) }
    }
    private fun setUs() { tts.language = Locale.US }

    /** Installed English voices (US first) as (id, label). Empty until the engine is ready. */
    fun english(): List<Pair<String, String>> {
        if (!ready) return emptyList()
        val vs = tts.voices.orEmpty().filter { it.locale.language == "en" && "notInstalled" !in it.features.orEmpty() }
        return vs.sortedWith(compareBy({ it.locale.country != "US" }, { it.isNetworkConnectionRequired }, { it.locale.country }, { it.name }))
            .map { v ->
                val place = v.locale.displayCountry.ifEmpty { "English" }
                val id = v.name.substringAfter("-x-").substringBefore('-')
                v.name to "$place · $id" + if (v.isNetworkConnectionRequired) " (online)" else ""
            }
    }
    /** Switch voice by id; null = the engine's default US voice. */
    fun use(name: String?) {
        wanted = name
        if (!ready) return
        val v = tts.voices.orEmpty().firstOrNull { it.name == name }
        if (v != null) tts.voice = v else setUs()
    }

    fun say(text: String, rate: Float) {
        if (!ready) return
        tts.setSpeechRate(rate)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "say")
    }
    fun stop() = tts.stop()
    fun shutdown() = tts.shutdown()
}

// ---------------------------------------------------------------- recognizer beeps

/**
 * The recognizer beeps at every start/stop, and it restarts at each pause while the mic is held.
 * Mute the sound streams it can use while recording; only the ones we muted are restored
 * (also on next app start, in case the app was closed mid-recording).
 */
object Beeps {
    private val STREAMS = listOf(AudioManager.STREAM_MUSIC, AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_SYSTEM)
    private fun prefs(c: Context) = c.getSharedPreferences("speakup", Context.MODE_PRIVATE)
    private fun am(c: Context) = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun mute(c: Context) {
        val am = am(c)
        val muted = STREAMS.filter { s ->
            !am.isStreamMute(s) && try { am.adjustStreamVolume(s, AudioManager.ADJUST_MUTE, 0); true } catch (_: Exception) { false }
        }
        prefs(c).edit().putString("mutedStreams", muted.joinToString(",")).apply()
    }

    fun restore(c: Context) {
        val list = prefs(c).getString("mutedStreams", "").orEmpty().split(',').mapNotNull { it.toIntOrNull() }
        if (list.isEmpty()) return
        val am = am(c)
        for (s in list) try { am.adjustStreamVolume(s, AudioManager.ADJUST_UNMUTE, 0) } catch (_: Exception) {}
        prefs(c).edit().remove("mutedStreams").apply()
    }
}

// ---------------------------------------------------------------- what the recognizer heard

/** Recognizer hypotheses, best first. */
data class Heard(val hypotheses: List<String>, val error: String? = null) {
    val best get() = hypotheses.firstOrNull().orEmpty()
}

/**
 * Test input for the emulator (debug builds), set through intent extras:
 *   --es fake "text"   next recording "hears" this ("a|b" = two hypotheses, best first)
 *   --es wav name.wav   next recording streams files/test-audio/name.wav to the real recognizer
 */
object Debug {
    @Volatile var nextWav: File? = null
    @Volatile var nextFake: String? = null

    fun fromIntent(context: Context, intent: Intent?) {
        if (!BuildConfig.DEBUG || intent == null) return
        intent.getStringExtra("wav")?.let { name ->
            val dir = context.getExternalFilesDir("test-audio")
            nextWav = File(dir, name).takeIf { it.exists() } ?: File(dir, "$name.wav")
        }
        intent.getStringExtra("fake")?.let { nextFake = it }
    }
}

class Recognizer {
    private var recognizer: SpeechRecognizer? = null
    /** True while a test WAV is streaming: releasing the mic must not cut it short. */
    @Volatile var streamingTest = false; private set

    /** True while the mic is held. */
    @Volatile private var holding = false

    /**
     * Records until the mic is released. The recognizer ends a session by itself after a pause,
     * so while the button is still held a new session starts and the pieces are joined.
     */
    suspend fun listen(context: Context, onPartial: (String) -> Unit): Heard {
        Debug.nextFake?.let { Debug.nextFake = null; return Heard(it.split('|')) }
        val wav = Debug.nextWav?.also { Debug.nextWav = null }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return Heard(emptyList(), "No speech recognizer on this device")
        if (wav != null) return once(context, wav, onPartial)
        Beeps.mute(context)
        try { return holdLoop(context, onPartial) }
        finally {
            // the recognizer's closing beep comes just after the session ends
            withContext(NonCancellable) { delay(500); Beeps.restore(context) }
        }
    }

    private suspend fun holdLoop(context: Context, onPartial: (String) -> Unit): Heard {
        holding = true
        val segs = mutableListOf<List<String>>()
        var error: String? = null
        while (true) {
            val prefix = segs.joinToString(" ") { it.first() }
            val h = once(context, null) { p -> onPartial("$prefix $p".trim()) }
            if (h.hypotheses.isNotEmpty()) segs += h.hypotheses
            if (h.error != null) { if (segs.isEmpty()) error = h.error; break }
            if (!holding) break
        }
        holding = false
        if (segs.isEmpty()) return Heard(emptyList(), error)
        return Heard(joinSegments(segs))
    }

    private suspend fun once(context: Context, wav: File?, onPartial: (String) -> Unit): Heard {
        return suspendCancellableCoroutine { cont ->
            val r = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = r
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                // On-device: works offline, and the online path can answer first with an empty result.
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                // Hold-to-talk: the user decides when to stop, so tolerate thinking pauses.
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
                // Android 13+: one session that doesn't end at pauses; text arrives in segments until we stop it.
                if (wav == null && Build.VERSION.SDK_INT >= 33) {
                    putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 600_000L)
                }
            }
            var pipe: Array<ParcelFileDescriptor>? = null
            if (wav != null && Build.VERSION.SDK_INT >= 33) {
                val (pcm, rate) = try { readWav(wav) } catch (e: Exception) {
                    r.destroy(); recognizer = null
                    cont.resume(Heard(emptyList(), "Test audio not found: ${wav.name}")); return@suspendCancellableCoroutine
                }
                pipe = ParcelFileDescriptor.createPipe()
                val out = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
                streamingTest = true
                // Real-time pacing like a mic: written all at once, the service ends the session before decoding.
                thread(name = "wav-feed") {
                    try {
                        out.use { o ->
                            val chunk = rate * 2 / 10
                            var p = 0
                            while (p < pcm.size) { val n = minOf(chunk, pcm.size - p); o.write(pcm, p, n); o.flush(); p += n; Thread.sleep(100) }
                            // a second of silence so the recognizer finalizes
                            repeat(10) { o.write(ByteArray(chunk)); o.flush(); Thread.sleep(100) }
                        }
                    } catch (_: Exception) {}
                }
                intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0])
                intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, rate)
            }
            fun finish(h: Heard) {
                if (!cont.isActive) return
                streamingTest = false
                r.destroy(); recognizer = null
                pipe?.get(0)?.close()
                cont.resume(h)
            }
            var lastPartial = emptyList<String>()
            val segs = mutableListOf<List<String>>()
            fun soFar() = (segs + listOfNotNull(lastPartial.ifEmpty { null }))
            r.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    // Some on-device services return an empty final bundle: fall back to the last partial.
                    val list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty().filter { it.isNotBlank() }.ifEmpty { lastPartial }
                    android.util.Log.i("SpeakUp", "recognizer results: $list")
                    finish(Heard(list))
                }
                override fun onPartialResults(partial: Bundle) {
                    val list = partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty().filter { it.isNotBlank() }
                    if (list.isNotEmpty()) { lastPartial = list; onPartial(joinSegments(soFar()).first()) }
                }
                override fun onError(error: Int) {
                    android.util.Log.i("SpeakUp", "recognizer error $error")
                    finish(when {
                        segs.isNotEmpty() || lastPartial.isNotEmpty() -> Heard(joinSegments(soFar()))
                        error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Heard(emptyList())
                        else -> Heard(emptyList(), "Speech recognizer error $error")
                    })
                }
                // Released while this session was starting: finish it now.
                override fun onReadyForSpeech(params: Bundle?) { if (!holding && !streamingTest) r.stopListening() }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onSegmentResults(segmentResults: Bundle) {
                    val list = segmentResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty().filter { it.isNotBlank() }
                    android.util.Log.i("SpeakUp", "segment: $list")
                    if (list.isNotEmpty()) segs += list
                    lastPartial = emptyList()
                    if (segs.isNotEmpty()) onPartial(joinSegments(segs).first())
                }
                override fun onEndOfSegmentedSession() {
                    android.util.Log.i("SpeakUp", "segmented session ended")
                    finish(Heard(if (soFar().isEmpty()) emptyList() else joinSegments(soFar())))
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            cont.invokeOnCancellation { r.cancel(); r.destroy(); recognizer = null; streamingTest = false; holding = false }
            r.startListening(intent)
        }
    }

    /** Mic released: finish and deliver what was heard. */
    fun stop() { holding = false; if (!streamingTest) recognizer?.stopListening() }
}

/** Pieces of one answer → hypotheses: k-th = each piece's k-th guess (or its best), so "unsure word" hints still work. */
private fun joinSegments(segs: List<List<String>>): List<String> {
    val n = segs.maxOfOrNull { it.size } ?: return emptyList()
    return (0 until n).map { k -> segs.joinToString(" ") { s -> s.getOrElse(k) { s.first() } } }
}

/** 16-bit PCM WAV → (samples, sample rate). Mono expected. */
private fun readWav(f: File): Pair<ByteArray, Int> {
    RandomAccessFile(f, "r").use { raf ->
        val all = ByteArray(raf.length().toInt()); raf.readFully(all)
        var p = 12; var rate = 16000
        while (p + 8 <= all.size) {
            val id = String(all, p, 4); val size = le32(all, p + 4)
            if (id == "fmt ") rate = le32(all, p + 12)
            if (id == "data") return all.copyOfRange(p + 8, minOf(all.size, p + 8 + size)) to rate
            p += 8 + size + (size and 1)
        }
        return ByteArray(0) to rate
    }
}

private fun le32(b: ByteArray, o: Int) = (b[o].toInt() and 255) or ((b[o + 1].toInt() and 255) shl 8) or
    ((b[o + 2].toInt() and 255) shl 16) or ((b[o + 3].toInt() and 255) shl 24)

// ---------------------------------------------------------------- alignment & scoring

enum class Status { GOOD, WEAK, MISS }

/** Display words of a text (split on spaces) and their lookup keys ("" for punctuation-only tokens). */
fun wordsOf(text: String): List<String> = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

private fun keysOf(text: String) = wordsOf(text).flatMap { it.split('-', '–', '—', '/') }.map(::normalizeToken).filter { it.isNotEmpty() }

private fun edit(a: String, b: String): Int {
    val d = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        var prev = d[0]; d[0] = i
        for (j in 1..b.length) {
            val tmp = d[j]
            d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
            prev = tmp
        }
    }
    return d[b.length]
}

private fun near(a: String, b: String): Boolean {
    val e = edit(a, b)
    return e <= 1 && a.length >= 4 || e <= 2 && a.length >= 7
}

/** Join adjacent heard tokens that together equal an expected one ("every one" → "everyone"). */
private fun mergeCompounds(expected: List<String>, heard: List<String>): List<String> {
    val want = expected.toSet(); val out = mutableListOf<String>(); var j = 0
    while (j < heard.size) {
        if (j + 1 < heard.size && heard[j] !in want && (heard[j] + heard[j + 1]) in want) { out += heard[j] + heard[j + 1]; j += 2 }
        else { out += heard[j]; j++ }
    }
    return out
}

/** Per expected token: GOOD (exact), WEAK (close spelling), MISS — plus the heard token it was matched to, if different. */
fun alignTokens(expected: List<String>, heardRaw: List<String>): List<Pair<Status, String?>> {
    val heard = mergeCompounds(expected, heardRaw)
    val n = expected.size; val m = heard.size
    val cost = Array(n + 1) { DoubleArray(m + 1) }
    for (i in 0..n) cost[i][0] = i.toDouble()
    for (j in 0..m) cost[0][j] = j * 0.8
    fun sub(i: Int, j: Int) = when { expected[i] == heard[j] -> 0.0; near(expected[i], heard[j]) -> 0.5; else -> 1.0 }
    for (i in 1..n) for (j in 1..m) cost[i][j] = minOf(cost[i - 1][j - 1] + sub(i - 1, j - 1), cost[i - 1][j] + 1, cost[i][j - 1] + 0.8)
    val out = Array<Pair<Status, String?>>(n) { Status.MISS to null }
    var i = n; var j = m
    while (i > 0 && j > 0) {
        val s = sub(i - 1, j - 1)
        when {
            cost[i][j] == cost[i - 1][j - 1] + s -> { out[i - 1] = (if (s == 0.0) Status.GOOD else if (s == 0.5) Status.WEAK else Status.MISS) to heard[j - 1].takeIf { s > 0 }; i--; j-- }
            cost[i][j] == cost[i - 1][j] + 1 -> { out[i - 1] = Status.MISS to null; i-- }
            else -> j--
        }
    }
    return out.toList()
}

/** Reading a known text aloud: per display word GOOD/WEAK/MISS. */
data class ReadScore(val words: List<Status>, val good: Int, val total: Int, val firstMiss: Int?) {
    val perfect get() = good == total
}

fun scoreReading(target: String, heard: Heard): ReadScore {
    val display = wordsOf(target)
    val keys = display.map { w -> w.split('-', '–', '—', '/').map(::normalizeToken).filter { it.isNotEmpty() } }
    val expected = keys.flatten()
    val owner = keys.flatMapIndexed { i, k -> List(k.size) { i } }
    val hyps = heard.hypotheses.map(::keysOf)
    val aligned = alignTokens(expected, hyps.firstOrNull().orEmpty()).map { it.first }.toMutableList()
    val others = hyps.drop(1).flatten().toSet()
    for (k in aligned.indices) if (aligned[k] == Status.MISS && expected[k] in others) aligned[k] = Status.WEAK
    val per = display.indices.map { wi ->
        val st = aligned.filterIndexed { k, _ -> owner[k] == wi }
        when { st.isEmpty() -> Status.GOOD; Status.MISS in st -> Status.MISS; Status.WEAK in st -> Status.WEAK; else -> Status.GOOD }
    }
    val graded = display.indices.filter { keys[it].isNotEmpty() && !keys[it].first().any(Char::isDigit) }
    val good = graded.count { per[it] == Status.GOOD }
    return ReadScore(per, good, graded.size, graded.firstOrNull { per[it] != Status.GOOD })
}

/**
 * Own answer: words of the best hypothesis that the other hypotheses heard differently
 * (word index in the draft text → the alternative guess).
 */
fun unsureWords(heard: Heard): Map<Int, String> {
    val display = wordsOf(heard.best)
    val keys = display.map(::normalizeToken)
    val idx = keys.indices.filter { keys[it].isNotEmpty() }
    val expected = idx.map { keys[it] }
    val out = linkedMapOf<Int, String>()
    for (alt in heard.hypotheses.drop(1).take(2)) {
        val a = alignTokens(expected, keysOf(alt))
        a.forEachIndexed { k, (st, as_) -> if (st != Status.GOOD && as_ != null && idx[k] !in out) out[idx[k]] = as_ }
    }
    return out
}

/** "i like movies" → "I like movies." */
fun tidy(s: String): String {
    val t = s.trim().replace(Regex("\\bi\\b"), "I").replace(Regex("\\bi'"), "I'")
    if (t.isEmpty()) return t
    val c = t.replaceFirstChar { it.uppercase() }
    return if (c.last() in ".?!") c else "$c."
}
