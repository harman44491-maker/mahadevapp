const fs = require("fs/promises");
const os = require("os");
const path = require("path");
const { spawn } = require("child_process");
const ffmpegPath = require("ffmpeg-static");
const sharp = require("sharp");
sharp.concurrency(1);
sharp.cache(false);

const GEMINI_KEY = process.env.GEMINI_API_KEY || "";
const GEMINI_TEXT_MODEL = process.env.GEMINI_TEXT_MODEL || "gemini-3.8-flash";
const GEMINI_TEXT_FALLBACK_MODEL = process.env.GEMINI_TEXT_FALLBACK_MODEL || "gemini-3.5-flash-lite";
const GEMINI_TTS_MODEL = process.env.GEMINI_TTS_MODEL || "gemini-3.8-flash-tts";

async function geminiGenerate(contents, generationConfig = {}) {
  if (!GEMINI_KEY) throw new Error("GEMINI_API_KEY_MISSING");
  const models = [...new Set([GEMINI_TEXT_MODEL, GEMINI_TEXT_FALLBACK_MODEL].filter(Boolean))];
  let lastError;
  for (const model of models) {
    for (let attempt = 0; attempt < 3; attempt++) {
      try {
        const url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + encodeURIComponent(GEMINI_KEY);
        const response = await fetch(url, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ contents, generationConfig })
        });
        const data = await response.json();
        if (response.ok) return (data.candidates?.[0]?.content?.parts || []).map(p => p.text || "").join("").trim();
        const message = data.error?.message || response.status;
        lastError = new Error("GEMINI_TEXT_ERROR (" + model + ", HTTP " + response.status + "): " + message);
        if (![429, 500, 502, 503, 504].includes(response.status)) throw lastError;
      } catch (error) {
        lastError = error;
        if (!/fetch failed|network|timeout|GEMINI_TEXT_ERROR/.test(String(error.message))) throw error;
      }
      if (attempt < 2) await new Promise(resolve => setTimeout(resolve, 1000 * (2 ** attempt)));
    }
  }
  throw lastError || new Error("GEMINI_TEXT_ERROR: all configured models unavailable");
}

async function createScript(trends) {
  const prompt = [
    "Create one original 35-45 second YouTube Short for an Indian audience, based on current India trending signals.",
    "IMPORTANT: Include Hindi and Punjabi comedy in the mixed-topic rotation whenever comedy, funny, meme, stand-up, viral dialogue, or Punjabi/Hindi entertainment signals appear in the trends. Prefer an original relatable comedy skit with a strong hook, setup, and punchline for those signals. Rotate Hindi comedy and Punjabi-flavoured comedy so both are represented over time.",
    "For Punjabi comedy, write natural spoken Punjabi vocabulary using Devanagari transliteration so the configured Hindi TTS and captions can read it clearly. For Hindi comedy, use natural conversational Hindi in Devanagari. Choose language based on the trend and vary it across Shorts.",
    "If no comedy-related trend signal is present, choose another relevant trending topic instead. Do not force a fake claim that something is trending.",
    "Do not copy, quote, imitate, or reuse any creator's video, script, joke, character, or dialogue. Trend titles are inspiration signals only; invent a fresh situation and punchline.",
    "Keep comedy family-friendly, non-abusive, non-defamatory, and avoid targeting protected groups or real private people. Avoid unverified claims, politics, medical advice, and copyrighted characters.",
    "Return ONLY valid JSON with keys title, description, narration, hook, hashtags, language, style.",
    "Narration must be natural spoken Hindi or Punjabi-flavoured Hindi in Devanagari, 75-105 words. Title <= 70 characters. Description <= 500 characters. hashtags is an array of 3-5 strings. language must be Hindi or Punjabi. style must be Comedy or Trending.",
    "Use both YouTube India trending signals and connected Facebook Page engagement signals when available. Facebook signals describe the user own Page posts, not all-Facebook global trends. Prefer topics supported by both sources; when Facebook signals are sparse, use YouTube signals without inventing Facebook trends. Create a fresh original scenario and never reuse source footage, dialogue, jokes, or creator identity.",
    "Trend signals: " + JSON.stringify(trends.slice(0, 25).map(t => ({ title: t.title, channel: t.channelTitle, engagement: t.viewCount, source: t.source || "youtube_india_trending" })))
  ].join("\n");
  const raw = await geminiGenerate([{ role: "user", parts: [{ text: prompt }] }], {
    responseMimeType: "application/json",
    responseSchema: {
      type: "OBJECT",
      properties: {
        title: { type: "STRING" },
        description: { type: "STRING" },
        narration: { type: "STRING" },
        hook: { type: "STRING" },
        hashtags: { type: "ARRAY", items: { type: "STRING" } },
        language: { type: "STRING", enum: ["Hindi", "Punjabi"] },
        style: { type: "STRING", enum: ["Comedy", "Trending"] }
      },
      required: ["title", "description", "narration", "hook", "hashtags", "language", "style"]
    }
  });
  let script;
  try { script = JSON.parse(raw); } catch (_) { throw new Error("GEMINI_SCRIPT_INVALID_JSON"); }
  if (!script.title || !script.narration || script.narration.length < 100) throw new Error("GEMINI_SCRIPT_INCOMPLETE");
  return script;
}

async function createHindiVoice(text) {
  if (!GEMINI_KEY) throw new Error("GEMINI_API_KEY_MISSING");
  const url = "https://generativelanguage.googleapis.com/v1beta/models/" + GEMINI_TTS_MODEL + ":generateContent?key=" + encodeURIComponent(GEMINI_KEY);
  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      contents: [{ role: "user", parts: [{ text: "Read this narration naturally in a lively, clear Indian voice. It may contain Punjabi vocabulary written in Devanagari; pronounce it as colloquial Punjabi rather than translating it. " + text }] }],
      generationConfig: {
        responseModalities: ["AUDIO"],
        speechConfig: { voiceConfig: { prebuiltVoiceConfig: { voiceName: "Kore" } } }
      }
    })
  });
  const data = await response.json();
  if (!response.ok) throw new Error("GEMINI_TTS_ERROR: " + (data.error?.message || response.status));
  const audio = data.candidates?.[0]?.content?.parts?.find(p => p.inlineData?.data)?.inlineData?.data;
  if (!audio) throw new Error("GEMINI_TTS_NO_AUDIO");
  return { buffer: Buffer.from(audio, "base64"), mimeType: data.candidates?.[0]?.content?.parts?.find(p => p.inlineData?.data)?.inlineData?.mimeType || "audio/wav" };
}

function runFfmpeg(args) {
  return new Promise((resolve, reject) => {
    const child = spawn(ffmpegPath, args, { stdio: ["ignore", "ignore", "pipe"] });
    let stderr = "";
    child.stderr.on("data", d => { stderr += d.toString(); if (stderr.length > 10000) stderr = stderr.slice(-10000); });
    child.on("error", reject);
    child.on("close", code => code === 0 ? resolve() : reject(new Error("FFMPEG_RENDER_FAILED: " + stderr.slice(-1800))));
  });
}
function escSrt(s) { return String(s).replace(/\{/g, "(").replace(/\}/g, ")").replace(/\n/g, " "); }
function toSrtTime(seconds) {
  const ms = Math.max(0, Math.round(seconds * 1000));
  return [Math.floor(ms / 3600000), Math.floor((ms % 3600000) / 60000), Math.floor((ms % 60000) / 1000)].map(n => String(n).padStart(2, "0")).join(":") + "," + String(ms % 1000).padStart(3, "0");
}
function makeCaptions(narration, duration) {
  const words = narration.split(/\s+/).filter(Boolean);
  const groupSize = 5;
  const count = Math.ceil(words.length / groupSize);
  const span = duration / count;
  const entries = [];
  for (let i = 0; i < words.length; i += groupSize) {
    const n = entries.length;
    entries.push((n + 1) + "\n" + toSrtTime(n * span) + " --> " + toSrtTime(Math.min(duration, (n + 1) * span)) + "\n" + escSrt(words.slice(i, i + groupSize).join(" ")));
  }
  return entries.join("\n\n");
}
async function renderVideo(script, audioResult) {
  const audioBuffer = audioResult.buffer;
  const audioMimeType = String(audioResult.mimeType || "").toLowerCase();
  // Gemini TTS commonly returns raw 24 kHz PCM as audio/L16, not only audio/pcm.
  const isPcm = audioMimeType.startsWith("audio/pcm") || audioMimeType.startsWith("audio/l16") || audioMimeType.startsWith("audio/x-raw");
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), "rohitsworld-auto-"));
  const audioPath = path.join(dir, isPcm ? "voice.pcm" : "voice.wav");
  const svgPath = path.join(dir, "background.svg");
  const bgPath = path.join(dir, "background.png");
  const srtPath = path.join(dir, "captions.srt");
  const outPath = path.join(dir, "rohitsworld-short.mp4");
  try {
    await fs.writeFile(audioPath, audioBuffer);
    const safeTitle = String(script.hook || script.title).replace(/[<>&"]/g, " ").slice(0, 90);
    const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="1080" height="1920"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#10153d"/><stop offset="0.55" stop-color="#542c78"/><stop offset="1" stop-color="#0c5965"/></linearGradient><radialGradient id="r"><stop stop-color="#ffcf70" stop-opacity=".45"/><stop offset="1" stop-color="#ffcf70" stop-opacity="0"/></radialGradient></defs><rect width="1080" height="1920" fill="url(#g)"/><circle cx="860" cy="340" r="560" fill="url(#r)"/><circle cx="140" cy="1480" r="500" fill="#25c7bb" opacity=".10"/><rect x="70" y="390" width="940" height="1140" rx="48" fill="#090d26" opacity=".28" stroke="#ffffff" stroke-opacity=".3" stroke-width="3"/><text x="540" y="700" text-anchor="middle" fill="#ffd36e" font-family="sans-serif" font-size="48" font-weight="bold">ROHITSWORLD</text><text x="540" y="870" text-anchor="middle" fill="#ffffff" font-family="sans-serif" font-size="68" font-weight="bold">' + safeTitle + '</text><text x="540" y="1430" text-anchor="middle" fill="#ffffff" opacity=".8" font-family="sans-serif" font-size="34">ऐसी और जानकारी के लिए जुड़े रहें</text></svg>';
    await fs.writeFile(svgPath, svg, "utf8");
    const audioDuration = isPcm ? audioBuffer.length / 48000 : (audioBuffer.length > 44 ? audioBuffer.readUInt32LE(40) / 48000 : 42);
    const duration = Math.max(3, Math.min(60, audioDuration || 42));
    await fs.writeFile(srtPath, makeCaptions(script.narration, duration), "utf8");
    await sharp(Buffer.from(svg), { limitInputPixels: 3_000_000 }).png({ compressionLevel: 9 }).resize(720, 1280, { fit: "fill", kernel: "linear" }).toFile(bgPath);
    const escapedSrt = srtPath.replace(/\\/g, "/").replace(/:/g, "\\:");
    await runFfmpeg([
      "-y", "-filter_threads", "1", "-threads", "1",
      "-loop", "1", "-framerate", "30", "-i", bgPath,
      ...(isPcm ? ["-f", "s16le", "-ar", "24000", "-ac", "1"] : []), "-i", audioPath,
      "-t", String(duration),
      "-vf", "scale=720:1280:flags=fast_bilinear,subtitles='" + escapedSrt + "':force_style='FontName=Noto Sans Devanagari,FontSize=22,Outline=3,Shadow=1,Alignment=2,MarginV=120'",
      "-r", "30", "-pix_fmt", "yuv420p", "-c:v", "libx264", "-preset", "ultrafast", "-tune", "stillimage", "-crf", "28", "-threads", "1",
      "-c:a", "aac", "-b:a", "96k", "-shortest", "-movflags", "+faststart", outPath
    ]);
    const buffer = await fs.readFile(outPath);
    return { buffer, durationSeconds: Math.round(duration) };
  } finally {
    await fs.rm(dir, { recursive: true, force: true });
  }
}

async function createOriginalShort(trends) {
  const script = await createScript(trends);
  const audio = await createHindiVoice(script.narration);
  const rendered = await renderVideo(script, audio);
  return { ...rendered, script };
}
module.exports = { createOriginalShort };
