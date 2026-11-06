package com.zeit2206.speakup

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// ---------------------------------------------------------------- topics

data class Topic(val title: String, val blurb: String)

/** IELTS Speaking practice choices: part (0 = full test), title, time, what the learner should do. */
data class ExamPart(val part: Int, val title: String, val time: String, val note: String)

val EXAM_PARTS = listOf(
    ExamPart(0, "Full test", "11–14 min", "All three parts in order, like the real exam."),
    ExamPart(1, "Part 1 · Interview", "4–5 min", "Short questions about you: home, work, hobbies. Answer + reason + detail, 2–3 sentences. Never just one word."),
    ExamPart(2, "Part 2 · Long turn", "3–4 min", "A cue card. 1 minute to prepare, then talk alone for 2 minutes and cover every point. Keep going until you're stopped."),
    ExamPart(3, "Part 3 · Discussion", "4–5 min", "Bigger questions about society. Give your opinion and two reasons with examples: I think… First… Second… So that's why…"),
)

/** IELTS Speaking Part 2 cue cards: official sample (ielts.org) + ones reported from recent exams (Hund/eng-test prompts). */
val IELTS_CARDS = listOf(
    "Describe something you own which is very important to you. You should say: where you got it from, how long you have had it, what you use it for, and explain why it is important to you.",
    "Describe a person whose job is important to society. You should say: who this person is, what their job is, how you know about them, and explain why their job is important.",
    "Describe an important river in your country. You should say: where it is, how big it is, what people use it for, and explain why it is important.",
    "Describe a product you got for free. You should say: what it was, where you got it, why it was free, and explain how you felt about it.",
    "Describe a famous product from your area. You should say: what it is, how it is made, who buys it, and explain why it is famous.",
    "Describe a person who inspired you to do something. You should say: who this person is, how you know them, what they inspired you to do, and explain why they inspired you.",
    "Describe an old person you admire. You should say: who this person is, how you know them, what they are like, and explain why you admire them.",
    "Describe a person you know who speaks a foreign language well. You should say: who this person is, what language they speak, how they learned it, and explain how you feel about their ability.",
    "Describe a family member you spend the most time with. You should say: who this person is, what you do together, how often you see each other, and explain why you spend so much time with them.",
)

fun loadTopics(context: Context): List<Topic> {
    val a = JSONArray(context.assets.open("topics.json").use { it.readBytes() }.decodeToString())
    return (0 until a.length()).map { a.getJSONObject(it).let { o -> Topic(o.getString("title"), o.getString("blurb")) } }
}

// ---------------------------------------------------------------- server

data class Reply(val message: String, val suggestions: List<String>, val rephrase: String?, val keep: List<String>)

class ServerError(msg: String) : Exception(msg)

/** The app's only link to Claude. Swap this class for a direct API client later; nothing else changes. */
class Server(private val url: String, private val token: String) {
    private fun open(path: String, timeout: Int) = (URL(url.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
        connectTimeout = 6000; readTimeout = timeout
        setRequestProperty("x-token", token)
    }

    private fun read(c: HttpURLConnection): JSONObject {
        val code = c.responseCode
        val body = (if (code < 400) c.inputStream else c.errorStream)?.use { it.readBytes().decodeToString() }.orEmpty()
        val o = try { JSONObject(body) } catch (e: Exception) { JSONObject() }
        if (code == 401) throw ServerError("The server refused the token. Check it in Settings.")
        if (code >= 400) throw ServerError(o.optString("error").ifEmpty { "Server error $code" })
        return o
    }

    private fun <T> call(block: () -> T): T = try { block() } catch (e: ServerError) { throw e } catch (e: Exception) {
        android.util.Log.i("SpeakUp", "server: $e")
        throw ServerError("Can't reach your PC. Check Tailscale and that the server is running.")
    }

    suspend fun health(): String = withContext(Dispatchers.IO) {
        call { read(open("/health", 8000)).optString("model", "ok") }
    }

    suspend fun turn(body: JSONObject): Reply = withContext(Dispatchers.IO) {
        call {
            val c = open("/turn", 75_000)
            c.requestMethod = "POST"; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val o = read(c)
            Reply(
                o.getString("message"),
                o.optJSONArray("suggestions").strings(),
                o.optString("rephrase").takeIf { !o.isNull("rephrase") && it.isNotBlank() },
                o.optJSONArray("keep").strings(),
            )
        }
    }
}

fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { getString(it) }

// ---------------------------------------------------------------- saved settings, sessions, words

/** One chat bubble of a saved conversation, so it can be continued. */
data class Line(val app: Boolean, val text: String)

/** One question of a conversation and its Help answers, so the conversation can be repeated. */
data class Turn(val q: String, val sugg: List<String>)

data class Session(
    val topic: String, val open: Boolean, val level: String, val date: Long,
    val turns: Int, val own: Int, val read: Int, val skipped: Int, val keep: List<String>,
    val blurb: String? = null, val exam: Boolean = false, val script: List<Turn> = emptyList(), val part: Int = 0,
    val lines: List<Line> = emptyList(),
) {
    fun json() = JSONObject().put("topic", topic).put("open", open).put("level", level).put("date", date)
        .put("turns", turns).put("own", own).put("read", read).put("skipped", skipped).put("keep", JSONArray(keep))
        .put("blurb", blurb ?: "").put("exam", exam).put("part", part)
        .put("lines", JSONArray(lines.map { JSONObject().put("a", it.app).put("t", it.text) }))
        .put("script", JSONArray(script.map { JSONObject().put("q", it.q).put("s", JSONArray(it.sugg)) }))

    companion object {
        fun from(o: JSONObject) = Session(o.getString("topic"), o.optBoolean("open"), o.optString("level", "B1"), o.getLong("date"),
            o.optInt("turns"), o.optInt("own"), o.optInt("read"), o.optInt("skipped"), o.optJSONArray("keep").strings(),
            o.optString("blurb").ifEmpty { null }, o.optBoolean("exam"),
            o.optJSONArray("script")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).let { t -> Turn(t.getString("q"), t.optJSONArray("s").strings()) } } }.orEmpty(),
            o.optInt("part"),
            o.optJSONArray("lines")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).let { l -> Line(l.getBoolean("a"), l.getString("t")) } } }.orEmpty())
    }
}

data class SavedWord(val word: String, val ipa: String?, val es: String?, val date: Long)

class Store(context: Context) {
    private val p = context.getSharedPreferences("speakup", Context.MODE_PRIVATE)

    var serverUrl: String get() = p.getString("url", "http://192.168.1.10:8790")!!; set(v) = p.edit().putString("url", v.trim()).apply()
    var token: String get() = p.getString("token", BuildConfig.SERVER_TOKEN)!!; set(v) = p.edit().putString("token", v.trim()).apply()
    var level: String get() = p.getString("level", "B1")!!; set(v) = p.edit().putString("level", v).apply()
    var rate: Float get() = p.getFloat("rate", 0.9f); set(v) = p.edit().putFloat("rate", v).apply()
    var autoSpeak: Boolean get() = p.getBoolean("autoSpeak", true); set(v) = p.edit().putBoolean("autoSpeak", v).apply()

    var dark: Boolean get() = p.getBoolean("dark", false); set(v) = p.edit().putBoolean("dark", v).apply()
    var voiceName: String? get() = p.getString("voice", null); set(v) = p.edit().putString("voice", v).apply()

    fun server() = Server(serverUrl, token)

    fun sessions(): List<Session> = JSONArray(p.getString("sessions", "[]")).let { a -> (0 until a.length()).map { Session.from(a.getJSONObject(it)) } }
    fun addSession(s: Session) {
        val all = listOf(s) + sessions()
        p.edit().putString("sessions", JSONArray(all.take(100).map { it.json() }).toString()).apply()
    }

    /** Add or update (same start time) a conversation, newest first. */
    fun putSession(s: Session) {
        val rest = sessions().filter { it.date != s.date }
        p.edit().putString("sessions", JSONArray((listOf(s) + rest).take(100).map { it.json() }).toString()).apply()
    }

    fun removeSession(date: Long) =
        p.edit().putString("sessions", JSONArray(sessions().filter { it.date != date }.map { it.json() }).toString()).apply()

    fun words(): List<SavedWord> = JSONArray(p.getString("words", "[]")).let { a ->
        (0 until a.length()).map { a.getJSONObject(it).let { o -> SavedWord(o.getString("w"), o.optString("ipa").ifEmpty { null }, o.optString("es").ifEmpty { null }, o.optLong("d")) } }
    }
    private fun putWords(list: List<SavedWord>) = p.edit().putString("words", JSONArray(list.map {
        JSONObject().put("w", it.word).put("ipa", it.ipa ?: "").put("es", it.es ?: "").put("d", it.date)
    }).toString()).apply()

    fun isSaved(word: String) = words().any { it.word == word }
    fun save(e: Dict.Entry) { if (!isSaved(e.word)) putWords(listOf(SavedWord(e.word, e.ipa, e.es, System.currentTimeMillis())) + words()) }
    fun unsave(word: String) = putWords(words().filter { it.word != word })
}
