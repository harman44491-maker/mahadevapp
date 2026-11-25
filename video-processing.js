const fs = require("fs/promises");
const os = require("os");
const path = require("path");
const { spawn } = require("child_process");
const ffmpegPath = require("ffmpeg-static");
const { GoogleAuth } = require("google-auth-library");

function runFfmpeg(args) {
  return new Promise((resolve, reject) => {
    const child = spawn(ffmpegPath, args, { stdio: ["ignore", "ignore", "pipe"] });
    let stderr = "";
    child.stderr.on("data", chunk => { stderr += chunk.toString(); if (stderr.length > 12000) stderr = stderr.slice(-12000); });
    child.on("error", reject);
    child.on("close", code => code === 0 ? resolve() : reject(new Error("FFmpeg failed: " + stderr.slice(-2500))));
  });
}

function toSrtTime(seconds) {
  const ms = Math.max(0, Math.round(seconds * 1000));
  const hh = String(Math.floor(ms / 3600000)).padStart(2, "0");
  const mm = String(Math.floor((ms % 3600000) / 60000)).padStart(2, "0");
  const ss = String(Math.floor((ms % 60000) / 1000)).padStart(2, "0");
  return hh + ":" + mm + ":" + ss + "," + String(ms % 1000).padStart(3, "0");
}

function buildSrt(results) {
  const words = [];
  for (const result of results || []) {
    for (const word of result.alternatives?.[0]?.words || []) {
      const start = Number(word.startTime?.replace("s", "") || 0);
      const end = Number(word.endTime?.replace("s", "") || start + 0.5);
      words.push({ text: word.word, start, end });
    }
  }
  if (!words.length) {
    const lines = (results || []).map(r => r.alternatives?.[0]?.transcript).filter(Boolean);
    return lines.map((line, i) => (i + 1) + "\n00:00:00,000 --> 00:00:59,000\n" + line).join("\n\n");
  }
  const chunks = [];
  for (let i = 0; i < words.length; i += 5) {
    const group = words.slice(i, i + 5);
    chunks.push((chunks.length + 1) + "\n" + toSrtTime(group[0].start) + " --> " + toSrtTime(group[group.length - 1].end) + "\n" + group.map(w => w.text).join(" "));
  }
  return chunks.join("\n\n");
}

async function transcribeHindi(audioPath) {
  const raw = process.env.GOOGLE_SERVICE_ACCOUNT_JSON || "";
  if (!raw) throw new Error("Hindi auto-captions need GOOGLE_SERVICE_ACCOUNT_JSON. Add a Google Cloud service-account JSON (base64 encoded) to Render.");
  let credentials;
  try { credentials = JSON.parse(raw); }
  catch (_) { credentials = JSON.parse(Buffer.from(raw, "base64").toString("utf8")); }
  const auth = new GoogleAuth({ credentials, scopes: ["https://www.googleapis.com/auth/cloud-platform"] });
  const client = await auth.getClient();
  const tokenResult = await client.getAccessToken();
  const audio = await fs.readFile(audioPath);
  const response = await fetch("https://speech.googleapis.com/v1/speech:recognize", {
    method: "POST",
    headers: { Authorization: "Bearer " + tokenResult.token, "Content-Type": "application/json" },
    body: JSON.stringify({
      config: { encoding: "FLAC", sampleRateHertz: 16000, languageCode: "hi-IN", enableAutomaticPunctuation: true, enableWordTimeOffsets: true, model: "latest_long" },
      audio: { content: audio.toString("base64") }
    })
  });
  const data = await response.json();
  if (!response.ok) throw new Error("Google Speech-to-Text: " + (data.error?.message || response.status));
  const srt = buildSrt(data.results);
  if (!srt.trim()) throw new Error("No Hindi speech was detected in this clip.");
  return srt;
}

async function makeHindiShort(inputBuffer, options = {}) {
  const duration = Math.min(60, Math.max(3, Number(options.durationSeconds || 30)));
  const start = Math.max(0, Number(options.startSeconds || 0));
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), "mahadev-short-"));
  const input = path.join(dir, "source.mp4");
  const audio = path.join(dir, "voice.flac");
  const subtitles = path.join(dir, "captions.srt");
  const output = path.join(dir, "short.mp4");
  try {
    await fs.writeFile(input, inputBuffer);
    await runFfmpeg(["-y", "-ss", String(start), "-i", input, "-t", String(duration), "-vn", "-ac", "1", "-ar", "16000", "-c:a", "flac", audio]);
    const srt = await transcribeHindi(audio);
    await fs.writeFile(subtitles, srt, "utf8");
    const escaped = subtitles.replace(/\\/g, "/").replace(/:/g, "\\:");
    await runFfmpeg([
      "-y", "-ss", String(start), "-i", input, "-t", String(duration),
      "-vf", "scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920,subtitles='" + escaped + "':force_style='FontName=Noto Sans Devanagari,FontSize=18,Outline=2,Shadow=1,Alignment=2,MarginV=110'",
      "-r", "30", "-c:v", "libx264", "-preset", "veryfast", "-crf", "23",
      "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart", output
    ]);
    return { buffer: await fs.readFile(output), durationSeconds: duration, subtitleFormat: "Hindi SRT burned into video" };
  } finally {
    await fs.rm(dir, { recursive: true, force: true });
  }
}

module.exports = { makeHindiShort };
