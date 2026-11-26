const fs = require("fs/promises");
const os = require("os");
const path = require("path");
const { spawn } = require("child_process");
const ffmpegPath = require("ffmpeg-static");
const sharp = require("sharp");

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
    "Create one original 35-45 second Hindi YouTube Short script inspired by current India trending topics.",
    "Do not copy, quote, summarize, or reuse any creator's video. Use trend titles only as topic signals.",
    "Choose one useful, factual, non-defamatory topic. Avoid unverified claims, politics, medical advice, and copyrighted characters.",
    "Return ONLY valid JSON with keys title, description, narration, hook, hashtags.",
    "Narration must be natural spoken Hindi in Devanagari, 75-105 words. Title <= 70 characters. Description <= 500 characters. hashtags is an array of 3-5 strings.",
    "Trend signals: " + JSON.stringify(trends.slice(0, 12).map(t => ({ title: t.title, channel: t.channelTitle, views: t.viewCount })))
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
        hashtags: { type: "ARRAY", items: { type: "STRING" } }
      },
      required: ["title", "description", "narration", "hook", "hashtags"]
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
      contents: [{ role: "user", parts: [{ text: "Read this Hindi narration naturally in a lively, clear Indian Hindi presenter voice. " + text }] }],
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
  const isPcm = String(audioResult.mimeType).toLowerCase().startsWith("audio/pcm");
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
    await sharp(Buffer.from(svg)).png().resize(1080, 1920).toFile(bgPath);
    const escapedSrt = srtPath.replace(/\\/g, "/").replace(/:/g, "\\:");
    await runFfmpeg([
      "-y", "-loop", "1", "-i", bgPath, ...(isPcm ? ["-f", "s16le", "-ar", "24000", "-ac", "1"] : []), "-i", audioPath, "-t", "60",
      "-vf", "scale=1080:1920,zoompan=z='min(zoom+0.0005,1.08)':d=1:s=1080x1920:fps=30,subtitles='" + escapedSrt + "':force_style='FontName=Noto Sans Devanagari,FontSize=22,Outline=3,Shadow=1,Alignment=2,MarginV=180'",
      "-r", "30", "-c:v", "libx264", "-preset", "veryfast", "-crf", "25", "-threads", "2",
      "-c:a", "aac", "-b:a", "128k", "-shortest", "-movflags", "+faststart", outPath
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
