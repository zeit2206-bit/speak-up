# Speak Up

Conversation practice for English learners. The app asks a question (spoken aloud); you answer in
your own words, or tap **Help** and read a suggested answer aloud. Claude (through a small server
on your PC) keeps the conversation going and shows a **More natural** version of your answers.

## Parts

| Folder | What |
|---|---|
| `server/` | Node server (no dependencies). Each turn runs one headless `claude -p` call with your Claude Code login. Port 8790, token in `token.txt`. |
| `android-app/` | Kotlin + Jetpack Compose app. |
| `SpeakUp.apk` | Release build (signed with the debug key, installs directly). |

## Use it

1. **On the PC:** double-click `server/start-server.cmd` (keep the window open). The PC must be awake, with Tailscale connected.
2. **On the phone:** install `SpeakUp.apk`, with Tailscale on. Set the server address in Settings; the token is built in from `server/token.txt` at build time; check with Settings → Test connection.
3. Pick a level, then **Start conversation** (suggested topic) or type your own topic in **Or talk about anything…**.

| Action | How |
|---|---|
| Answer in your own words | Hold the mic, speak, release → a **draft** appears. **Try again**, **Send**, or **Edit text** if it misheard a word you said right |
| Read a suggestion | **Help** → pick one → hold the mic and read it → missed words are shown in coral |
| Better version of your answer | The **More natural** card → Listen / Hold to repeat (scored) |
| Look up a word | Tap any word in a bubble (or in a selected suggestion) → US IPA, Spanish, Pronounce, Save |
| Saved words | Book icon on Home; the search box looks up any word |
| End | **End** at any time → summary (own vs read answers, phrases to keep), saved under Recent |

Word data (offline, bundled) is the same as the PDF IPA Reader extension: CMU Pronouncing Dictionary
(IPA) and FreeDict English–Spanish.

## Server notes

- Model: Sonnet (`SPEAKUP_MODEL=haiku node server.mjs` to switch; Haiku is about the same speed but gives weaker rephrases). About 3–5 s per turn.
- Runs `claude -p` with no tools, no settings/hooks and no MCP, in the empty `server/work/` folder.
- The server sends no data anywhere except through Claude Code. It needs the token for every request and is reachable on your tailnet only if you keep your firewall as it is (not open to the internet).
- To use the real Claude API later: replace the `Server` class in `android-app/.../Data.kt` (one `turn()` call) or swap `runClaude()` in `server.mjs`.

## Build

From the subst drive (a non-ASCII path breaks AGP): `cd P:/speak-up/android-app && ./gradlew assembleRelease`.
The token from `server/token.txt` is baked into the build. If you delete `token.txt`, a new one is made and you must rebuild (or type it in Settings).

Debug builds accept test hooks: `adb shell am start -n com.zeit2206.speakup/.MainActivity --es server http://10.0.2.2:8790 --es fake "\"what it hears\""` (or `--es wav file.wav` from `files/test-audio`).

## Tested on the emulator

Suggested and open topics; TTS question playback; own answer → draft (with unsure-word tip) → send → More natural card;
Hold to repeat scoring; Help → read a suggestion → reading check → send; Skip; End → summary → Recent;
word lookup + Save + Saved words search; Edit text; server down → clear error → Retry; Settings → Test connection;
the real on-device speech recognizer (synthesized WAV streamed in); the release APK over the Tailscale address.

**Not provable on the emulator:** your real voice and accent, the hold-and-release feel on a real mic,
and how often your phone's recognizer returns alternative guesses (the emulator's on-device
recognizer returned only one, so amber "unsure" words may be rare).
