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
  const frames = duration * 24;
  const zoom = style === "natural" ? "1.08" : style === "cinematic" ? "1.22" : "1.15";
  const step = style === "natural" ? "0.000035" : style === "cinematic" ? "0.00009" : "0.00006";
  try {
    await fs.writeFile(input, buffer);
    const filter = "scale=1200:2134:force_original_aspect_ratio=increase,crop=1080:1920,zoompan=z='min(zoom+" + step + "," + zoom + ")':x='iw/2-(iw/zoom/2)+sin(on/90)*18':y='ih/2-(ih/zoom/2)+cos(on/110)*12':d=" + frames + ":s=1080x1920:fps=24,format=yuv420p";
    await run(["-y", "-loop", "1", "-i", input, "-vf", filter, "-t", String(duration), "-an", "-c:v", "libx264", "-preset", "ultrafast", "-crf", "27", "-threads", "2", "-movflags", "+faststart", output]);
    return { buffer: await fs.readFile(output), durationSeconds: duration, style, animation: "free camera pan and zoom; no AI face animation" };
  } finally {
    await fs.rm(dir, { recursive: true, force: true });
  }
}

module.exports = { makePhotoMotionVideo };
