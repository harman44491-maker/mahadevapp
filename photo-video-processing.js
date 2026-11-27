const fs = require("fs/promises");
const os = require("os");
const path = require("path");
const { spawn } = require("child_process");
const ffmpegPath = require("ffmpeg-static");

function run(args) {
  return new Promise((resolve, reject) => {
    const child = spawn(ffmpegPath, args, { stdio: ["ignore", "ignore", "pipe"] });
    let stderr = "";
    child.stderr.on("data", chunk => { stderr += chunk.toString(); if (stderr.length > 8000) stderr = stderr.slice(-8000); });
    child.on("error", reject);
    child.on("close", code => code === 0 ? resolve() : reject(new Error("Photo video render failed: " + stderr.slice(-1800))));
  });
}

async function makePhotoMotionVideo(buffer, options = {}) {
  const duration = Math.min(60, Math.max(10, Math.round(Number(options.durationSeconds) || 10)));
  const style = ["natural", "cinematic", "both"].includes(options.style) ? options.style : "both";
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), "mahadev-photo-video-"));
  const input = path.join(dir, "photo.jpg");
  const output = path.join(dir, "photo-motion.mp4");
  const audio = options.narrationAudio ? path.join(dir, "narration.wav") : null;
  const subtitles = options.narrationText ? path.join(dir, "narration.srt") : null;
  const frames = duration * 24;
  const zoom = style === "natural" ? "1.08" : style === "cinematic" ? "1.22" : "1.15";
  const step = style === "natural" ? "0.000035" : style === "cinematic" ? "0.00009" : "0.00006";
  try {
    await fs.writeFile(input, buffer);
    if (audio) await fs.writeFile(audio, options.narrationAudio);
    if (subtitles) {
      const clean = String(options.narrationText).replace(/[\\r\\n]+/g, " ").replace(/-->/g, " ").trim();
      const words = clean.split(/\\s+/).filter(Boolean);
      const chunks = [];
      for (let i = 0; i < words.length; i += 7) chunks.push(words.slice(i, i + 7).join(" "));
      const stamp = seconds => { const ms=Math.max(0,Math.round(seconds*1000)); const h=String(Math.floor(ms/3600000)).padStart(2,"0"); const m=String(Math.floor(ms%3600000/60000)).padStart(2,"0"); const s=String(Math.floor(ms%60000/1000)).padStart(2,"0"); const z=String(ms%1000).padStart(3,"0"); return h+":"+m+":"+s+","+z; };
      const block = duration / Math.max(1,chunks.length);
      const srt = chunks.map((line,i)=>(i+1)+"\\n"+stamp(i*block)+" --> "+stamp(Math.min(duration,(i+1)*block))+"\\n"+line).join("\\n\\n");
      await fs.writeFile(subtitles, srt, "utf8");
    }
    const filter = "scale=1200:2134:force_original_aspect_ratio=increase,crop=1080:1920,zoompan=z='min(zoom+" + step + "," + zoom + ")':x='iw/2-(iw/zoom/2)+sin(on/90)*18':y='ih/2-(ih/zoom/2)+cos(on/110)*12':d=" + frames + ":s=1080x1920:fps=24,format=yuv420p";
    const videoFilter = filter + (subtitles ? ",subtitles=" + subtitles.replace(/\\\\/g, "/").replace(/:/g, "\\\\:") : "");
    const args = ["-y", "-loop", "1", "-i", input];
    if (audio) args.push("-i", audio);
    args.push("-vf", videoFilter, "-t", String(duration), "-map", "0:v:0");
    if (audio) args.push("-map", "1:a:0", "-c:a", "aac", "-b:a", "128k", "-af", "apad");
    else args.push("-an");
    args.push("-c:v", "libx264", "-preset", "ultrafast", "-crf", "27", "-threads", "2", "-movflags", "+faststart", output);
    await run(args);
    return { buffer: await fs.readFile(output), durationSeconds: duration, style, animation: "free camera pan and zoom; no AI face animation", narration: Boolean(audio), subtitles: Boolean(subtitles) };
  } finally {
    await fs.rm(dir, { recursive: true, force: true });
  }
}

module.exports = { makePhotoMotionVideo };
