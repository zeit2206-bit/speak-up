// Speak Up server: the app's "brain". Each POST /turn runs one headless `claude -p` call
// (your Claude Code login, no API key) and returns the next message as JSON.
// No dependencies:  node server.mjs   (port 8790, token in token.txt)
import http from 'node:http';
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const DIR = path.dirname(fileURLToPath(import.meta.url));
const PORT = Number(process.env.PORT || 8790);
const MODEL = process.env.SPEAKUP_MODEL || 'sonnet'; // haiku: similar speed, weaker rephrases
const WORK = path.join(DIR, 'work'); // empty cwd: no project CLAUDE.md is loaded
fs.mkdirSync(WORK, { recursive: true });
const TOKEN_FILE = path.join(DIR, 'token.txt');
if (!fs.existsSync(TOKEN_FILE)) fs.writeFileSync(TOKEN_FILE, crypto.randomBytes(9).toString('base64url'));
const TOKEN = fs.readFileSync(TOKEN_FILE, 'utf8').trim();

const SYSTEM = `You are a warm, patient English conversation partner inside a speaking-practice app for English learners (many are Spanish speakers).
Keep the conversation going on the given topic. Each of your messages: a short natural reaction to what the learner said (1 sentence), then ONE open question. Vary the reaction: don't start with "Thank you"/"Thanks" (at most once per conversation), and sometimes go straight to the question. Total under 40 words. Match the learner's CEFR level (A2 = very simple words and short sentences; C1 = natural idioms).
Also return:
- suggestions: exactly 3 different natural answers to YOUR question that a learner could read aloud, 8-20 words each, first person, at the learner's level, varied in opinion.
- rephrase: only when the learner gave their OWN answer: the same meaning said the way a native speaker would, fixing grammar and unnatural wording. Keep their ideas, don't add new ones. Use null if their answer was already correct and natural — style-only changes (contractions like "it is" → "it's", synonyms, commas) don't count as a reason to rephrase. Also null or if they read a suggestion, skipped, or this is the start.
- keep: up to 2 short useful phrases (2-6 words) from your rephrase or your message that are worth learning. Empty array if none.
Never mention that you are an AI unless asked. Reply with the JSON only.`;

const TARGET_BAND = process.env.SPEAKUP_BAND || '6.5';
// IELTS mode: same JSON shape, so the app needs no new screens.
const EXAM = `You are an IELTS General Training Speaking examiner running a mock test inside a practice app. The learner is a Spanish-speaking adult aiming for band ${TARGET_BAND}. Model answers should fit an ordinary working adult's life. Use the real examiner style from the official IELTS sample test.
Follow the test order using the conversation so far:
- Part 1 (interview): 4 short questions about familiar topics (home town, accommodation, work or studies, hobbies), in two topic sets, announced like "Let's talk about your home town." / "Let's move on to talk about...". One question per message.
- Part 2 (long turn): give the cue card from the topic exactly, in this layout: "Now I'm going to give you a topic. You have one minute to prepare, then talk for one to two minutes. Don't worry if I stop you." then a blank line, the card's first sentence, then "You should say:" and each point on its own line starting with "• ", and the final "and explain ..." on its own line WITHOUT a bullet. After their answer, one short rounding-off question.
- Part 3 (discussion): 3 abstract questions on society linked to the Part 2 topic, announced ("Let's consider first of all..."). Probe like a real examiner ("Why do you think that is?").
- After the last Part 3 answer: say "That is the end of the speaking test." plus one short sentence on what went well, then offer a new Part 2 card on a different everyday theme and continue with Part 2 → Part 3.
Message: the examiner's next line only. Neutral and friendly, no corrections, never tell them to speak faster. Don't start every line with "Thank you": use it only when closing a part, otherwise go straight to the next question. Part 1 and 3 lines under 40 words.
Also return:
- suggestions: exactly 3 model answers at band 7 the learner could read aloud, first person, natural spoken English with fillers allowed ("Well, ..."). Part 1: 2-3 sentences (answer + reason + detail). Part 2: about 120 words covering every point of the card. Part 3: the frame "I think... First, because... for example... Second,... So that's why..." in 50-70 words. Vary the opinions.
- rephrase: only when the learner gave their OWN answer: the same answer said as a band-7 speaker would, fixing grammar and unnatural wording and, if it was too short, extending it slightly with their own idea (reason or example). Null if it was already good, or if they read a suggestion, skipped, or this is the start.
- keep: up to 2 useful phrases (2-6 words) for the exam, especially connectors and discourse markers. Empty array if none.
Reply with the JSON only.`;

const SCHEMA = JSON.stringify({
  type: 'object',
  properties: {
    message: { type: 'string' },
    suggestions: { type: 'array', items: { type: 'string' } },
    rephrase: { type: ['string', 'null'] },
    keep: { type: 'array', items: { type: 'string' } },
  },
  required: ['message', 'suggestions', 'rephrase', 'keep'],
});


// Repeating a saved conversation: the app sends the next question; Claude only rephrases.
function fixedNext(b, lines) {
  if (b.next === undefined || b.mode !== 'answer') return;
  lines.push(b.next === null
    ? 'That was the last question. Your message: one short warm closing sentence, no question. Suggestions: empty array.'
    : `Your next message must be exactly this question, word for word: "${b.next}". Suggestions must answer it.`);
}

const ONLY_PART = {
  1: 'PRACTICE PART 1 ONLY: never move to Part 2 or 3. Keep asking Part 1 questions, changing the topic set every 3-4 questions (home town, accommodation, work or studies, hobbies, food, weather, music, transport...).',
  2: 'PRACTICE PART 2 ONLY: no Part 1 or 3. Start with the cue card from the topic. After each long turn, ask one short rounding-off question, then give a NEW cue card on a different everyday theme (person, place, object, event, experience).',
  3: 'PRACTICE PART 3 ONLY: no Part 1 or 2. Start with "Let\'s talk about" the theme of the cue card and go straight to discussion questions. Every 3 questions, switch to a new theme announced like the examiner (education, technology, environment, work, society...).',
};

function examPrompt(b) {
  const lines = [...(ONLY_PART[b.part] ? [ONLY_PART[b.part], ''] : []), `Part 2 cue card for this test: ${b.topic || 'Describe something you own which is very important to you.'}`, '', 'Test so far:'];
  for (const m of b.history || []) lines.push(`${m.role === 'app' ? 'Examiner' : 'Candidate'}: ${m.text}`);
  if (!(b.history || []).length) lines.push('(nothing yet)');
  lines.push('');
  if (b.mode === 'start') lines.push(ONLY_PART[b.part] ? 'Start: one short greeting, then the first question of this part.' : 'Start the test: one short greeting, then the first Part 1 question.');
  else if (b.mode === 'resume') lines.push('The candidate is back to continue this practice. Briefly welcome them back, then ask the next question, continuing where the test left off. Never repeat a question already asked.');
  else if (b.mode === 'skip') lines.push('The candidate skipped your last question. Give a different, easier question in the same part.');
  else lines.push(`The candidate ${b.kind === 'read' ? 'read one of your model answers aloud' : 'answered in their own words'}: "${b.answer}"`);
  fixedNext(b, lines);
  return lines.join('\n');
}

function prompt(b) {
  const lines = [`Topic: ${b.topic || 'anything the learner wants'}`, `Learner level: ${b.level || 'B1'}`, '', 'Conversation so far:'];
  for (const m of b.history || []) lines.push(`${m.role === 'app' ? 'You' : 'Learner'}: ${m.text}`);
  if (!(b.history || []).length) lines.push('(nothing yet)');
  lines.push('');
  if (b.mode === 'start') lines.push(b.openTopic
    ? `The learner chose this topic themselves: "${b.topic}". Greet them briefly and ask a first question about it.`
    : 'Start the conversation: greet briefly and ask a first question about the topic.');
  else if (b.mode === 'resume') lines.push('The learner is back to continue this conversation. Welcome them back in a few words, then ask a NEW question on the topic, different from the ones already asked.');
  else if (b.mode === 'skip') lines.push('The learner skipped your last question. Ask a different, easier question on the same topic (no reaction needed).');
  else lines.push(`The learner ${b.kind === 'read' ? 'read one of your suggestions aloud' : 'answered in their own words'}: "${b.answer}"`);
  fixedNext(b, lines);
  return lines.join('\n');
}

function runClaude(text, system = SYSTEM) {
  return new Promise((resolve, reject) => {
    const args = ['-p', '--model', MODEL, '--tools', '', '--setting-sources', '', '--strict-mcp-config',
      '--no-session-persistence', '--output-format', 'json', '--system-prompt', system, '--json-schema', SCHEMA];
    const p = spawn('claude', args, { cwd: WORK, shell: false, windowsHide: true });
    let out = '', err = '';
    const timer = setTimeout(() => { p.kill(); reject(new Error('Claude took too long (60 s)')); }, 60000);
    p.stdout.on('data', d => out += d);
    p.stderr.on('data', d => err += d);
    p.on('error', e => { clearTimeout(timer); reject(e); });
    p.on('close', code => {
      clearTimeout(timer);
      try {
        const env = JSON.parse(out);
        if (env.is_error) return reject(new Error(env.result || 'Claude error'));
        const r = env.structured_output || JSON.parse(String(env.result).replace(/^```(json)?|```$/g, '').trim());
        resolve({
          message: String(r.message || '').trim(),
          suggestions: (r.suggestions || []).map(String).slice(0, 3),
          rephrase: r.rephrase ? String(r.rephrase) : null,
          keep: (r.keep || []).map(String).slice(0, 2),
        });
      } catch (e) { reject(new Error(`claude exited ${code}: ${(err || out).slice(0, 300)}`)); }
    });
    p.stdin.end(text);
  });
}

function send(res, status, obj) {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify(obj));
}

const server = http.createServer(async (req, res) => {
  const t0 = Date.now();
  if (req.headers['x-token'] !== TOKEN) return send(res, 401, { error: 'Wrong or missing token' });
  if (req.method === 'GET' && req.url === '/health') return send(res, 200, { ok: true, model: MODEL });
  if (req.method !== 'POST' || req.url !== '/turn') return send(res, 404, { error: 'Not found' });
  let body = '';
  for await (const c of req) { body += c; if (body.length > 200_000) return send(res, 413, { error: 'Too large' }); }
  try {
    const b = JSON.parse(body);
    if (!['start', 'answer', 'skip', 'resume'].includes(b.mode)) return send(res, 400, { error: 'mode must be start|answer|skip|resume' });
    const r = await (b.exam ? runClaude(examPrompt(b), EXAM) : runClaude(prompt(b)));
    console.log(`${new Date().toLocaleTimeString()} ${b.exam ? `ielts p${b.part || 'all'} ` : ''}${b.mode} ${Date.now() - t0} ms`);
    send(res, 200, r);
  } catch (e) {
    console.log(`${new Date().toLocaleTimeString()} error: ${e.message}`);
    send(res, 500, { error: e.message });
  }
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`Speak Up server on port ${PORT} (model ${MODEL})`);
  console.log(`Token: ${TOKEN}`);
  console.log('Phone: http://<PC Tailscale IP>:' + PORT + '   Emulator: http://10.0.2.2:' + PORT);
});
