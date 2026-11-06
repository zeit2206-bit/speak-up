package com.zeit2206.speakup

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

sealed interface Screen {
    data object Home : Screen
    data class Talk(val chat: Chat) : Screen
    data class Summary(val session: Session) : Screen
    data object Saved : Screen
    data object Settings : Screen
}

class MainActivity : ComponentActivity() {
    private lateinit var store: Store
    private lateinit var voice: Voice

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = Store(this)
        voice = Voice(this, store.voiceName)
        P.dark = store.dark
        Beeps.restore(this) // in case the app was closed while recording
        Panels.say = { voice.say(it, store.rate * 0.8f) }
        debugIntent(intent)
        val topics = loadTopics(this)
        lifecycleScope.launch(Dispatchers.IO) { Dict.load(applicationContext) }

        setContent {
            AppTheme { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.swipeForWords()) {
                // Status bar icons follow the app theme (light icons on the dark theme).
                val dark = P.dark
                androidx.compose.runtime.SideEffect {
                    androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark
                    }
                }
                val scope = rememberCoroutineScope()
                var screen by remember { mutableStateOf<Screen>(Screen.Home) }
                var topic by remember { mutableStateOf(topics.random()) }
                var level by remember { mutableStateOf(store.level) }
                var sessions by remember { mutableStateOf(store.sessions()) }
                val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
                val ask: () -> Boolean = {
                    val ok = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    if (!ok) perm.launch(Manifest.permission.RECORD_AUDIO)
                    ok
                }
                fun shuffle() { topic = topics.filter { it != topic }.random() }
                fun start(t: Topic, open: Boolean) {
                    val c = Chat(applicationContext, scope, store, voice, t.title, t.blurb.ifEmpty { null }, open, level)
                    screen = Screen.Talk(c); c.start()
                }
                fun startExam(part: Int) {
                    val title = if (part == 0) "IELTS Speaking" else "IELTS · Part $part"
                    val c = Chat(applicationContext, scope, store, voice, title, IELTS_CARDS.random(), false, level, exam = true, part = part)
                    screen = Screen.Talk(c); c.start()
                }
                fun repeat(s: Session) {
                    val c = Chat(applicationContext, scope, store, voice, s.topic, s.blurb, s.open, s.level, s.exam, s.script, s.part)
                    screen = Screen.Talk(c); c.start()
                }
                fun continueChat(s: Session) {
                    val c = Chat(applicationContext, scope, store, voice, s.topic, s.blurb, s.open, s.level, s.exam, null, s.part, s.lines)
                    screen = Screen.Talk(c); c.start()
                }
                fun end(c: Chat) {
                    c.close()
                    if (c.own + c.read + c.skipped == 0) { screen = Screen.Home; return }
                    val s = c.session(); store.putSession(s); sessions = store.sessions()
                    screen = Screen.Summary(s)
                }

                when (val s = screen) {
                    Screen.Home -> HomeScreen(store, topic, level, sessions,
                        onLevel = { level = it; store.level = it }, onShuffle = ::shuffle, onStart = ::start,
                        onSession = { screen = Screen.Summary(it) }, onSaved = { screen = Screen.Saved }, onSettings = { screen = Screen.Settings },
                        onTheme = { P.dark = !P.dark; store.dark = P.dark }, onExam = ::startExam,
                        onDelete = { store.removeSession(it.date); sessions = store.sessions() })
                    is Screen.Talk -> {
                        BackHandler { end(s.chat) }
                        DisposableEffect(s.chat) { onDispose { s.chat.close() } }
                        ChatScreen(s.chat, store, ask) { end(s.chat) }
                    }
                    is Screen.Summary -> {
                        BackHandler { screen = Screen.Home }
                        SummaryScreen(s.session, onSay = { voice.say(it, store.rate) }, onNew = { if (s.session.exam) startExam(s.session.part) else { shuffle(); start(topic, false) } }, onHome = { screen = Screen.Home },
                            onRepeat = if (s.session.script.isNotEmpty()) ({ repeat(s.session) }) else null,
                            onContinue = if (s.session.lines.isNotEmpty()) ({ continueChat(s.session) }) else null)
                    }
                    Screen.Saved -> {
                        BackHandler { screen = Screen.Home }
                        SavedScreen(store, onSay = { voice.say(it, store.rate * 0.8f) }) { screen = Screen.Home }
                    }
                    Screen.Settings -> {
                        BackHandler { screen = Screen.Home }
                        SettingsScreen(store, voice, onSay = { t, r -> voice.say(t, r) }) { screen = Screen.Home }
                    }
                }
                if (Panels.words) WordsPanel(store)
                Panels.guide?.let { GuidePanel(it) }
            }
            }
        }
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        debugIntent(intent)
    }

    /** Debug builds: --es server URL --es token T configure the server; --es fake / --es wav feed the next recording. */
    private fun debugIntent(i: Intent?) {
        if (!BuildConfig.DEBUG || i == null) return
        i.getStringExtra("server")?.let { store.serverUrl = it }
        i.getStringExtra("token")?.let { store.token = it }
        i.getStringExtra("autospeak")?.let { store.autoSpeak = it == "1" }
        Debug.fromIntent(this, i)
    }

    override fun onDestroy() { voice.shutdown(); super.onDestroy() }
}
