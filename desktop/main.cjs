const { app, BrowserWindow, shell, ipcMain, dialog } = require("electron");
const path = require("path");
const fs = require("fs");
const { pipeline } = require("stream/promises");
const { Readable, Transform } = require("stream");
const { spawn } = require("child_process");

const MODEL_NAME = "qwen2.5-0.5b-instruct-q4_k_m.gguf";
const MODEL_URL = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf?download=true";
const MODEL_BYTES = 491 * 1024 * 1024;
let llamaInstance = null;
let localModel = null;
let chatSession = null;
let modelLoading = false;
let modelDownloading = false;

function modelPath() {
  return path.join(app.getPath("userData"), "models", MODEL_NAME);
}
function modelStatus() {
  const file = modelPath();
  const exists = fs.existsSync(file) && fs.statSync(file).size > 100 * 1024 * 1024;
  return {
    downloaded: exists,
    loaded: Boolean(chatSession),
    loading: modelLoading,
    downloading: modelDownloading,
    model: "Qwen2.5-0.5B-Instruct Q4_K_M",
    approximateSizeMB: 491
  };
}
function sendProgress(event, data) {
  for (const win of BrowserWindow.getAllWindows()) win.webContents.send("offline-ai:progress", { event, ...data });
}
async function downloadModel() {
  if (modelDownloading) throw new Error("Model download already in progress.");
  if (modelStatus().downloaded) return modelStatus();
  modelDownloading = true;
  const target = modelPath();
  const temp = target + ".download";
  try {
    await fs.promises.mkdir(path.dirname(target), { recursive: true });
    sendProgress("start", { percent: 0, message: "Downloading offline AI model…" });
    const response = await fetch(MODEL_URL, { redirect: "follow" });
    if (!response.ok || !response.body) throw new Error("Model download failed: HTTP " + response.status);
    const total = Number(response.headers.get("content-length")) || MODEL_BYTES;
    let received = 0;
    const meter = new Transform({
      transform(chunk, encoding, callback) {
        received += chunk.length;
        const percent = Math.min(99, Math.floor(received / total * 100));
        sendProgress("download", { percent, receivedMB: Math.round(received / 1048576), totalMB: Math.round(total / 1048576) });
        callback(null, chunk);
      }
    });
    await pipeline(Readable.fromWeb(response.body), meter, fs.createWriteStream(temp));
    const stat = await fs.promises.stat(temp);
    if (stat.size < 100 * 1024 * 1024) throw new Error("Downloaded model file is incomplete.");
    await fs.promises.rename(temp, target);
    sendProgress("download", { percent: 100, message: "Offline model downloaded." });
    return modelStatus();
  } catch (error) {
    try { await fs.promises.unlink(temp); } catch {}
    throw error;
  } finally {
    modelDownloading = false;
  }
}
async function loadOfflineModel() {
  if (chatSession) return;
  if (modelLoading) throw new Error("Offline AI is already starting. Please wait.");
  if (!modelStatus().downloaded) throw new Error("Download the offline AI model first.");
  modelLoading = true;
  sendProgress("load", { percent: 0, message: "Starting local AI engine…" });
  try {
    const { getLlama, LlamaChatSession } = await import("node-llama-cpp");
    llamaInstance = await getLlama();
    localModel = await llamaInstance.loadModel({ modelPath: modelPath() });
    const context = await localModel.createContext({ contextSize: 2048 });
    chatSession = new LlamaChatSession({ contextSequence: context.getSequence() });
    sendProgress("load", { percent: 100, message: "Offline AI is ready." });
  } finally {
    modelLoading = false;
  }
}


const UPDATE_REPO = "harman44491-maker/mahadevapp";
const UPDATE_ASSET_PREFIX = "Mahadev-AI-Shorts-Maker-";
async function checkOnlineUpdate() {
  const response = await fetch("https://api.github.com/repos/" + UPDATE_REPO + "/releases/latest", {
    headers: { "Accept": "application/vnd.github+json", "User-Agent": "Mahadev-AI-Shorts-Maker" }
  });
  if (response.status === 404) return { available: false, published: false, message: "Abhi koi official update release publish nahi hui hai." };
  if (!response.ok) throw new Error("Update check failed: HTTP " + response.status);
  const release = await response.json();
  const asset = (release.assets || []).find(a => a.name.toLowerCase().endsWith(".exe") && a.name.startsWith(UPDATE_ASSET_PREFIX));
  if (!asset) return { available: false, published: true, version: release.tag_name, message: "Release mili, lekin Windows EXE asset nahi mila." };
  const current = app.getVersion();
  const latest = String(release.tag_name || "").replace(/^v/, "");
  return { available: latest !== current, published: true, currentVersion: current, latestVersion: latest, name: asset.name, size: asset.size, downloadUrl: asset.browser_download_url, notes: release.body || "" };
}
async function stageUpdate(sourcePath) {
  if (!app.isPackaged) throw new Error("Update installation sirf packaged Windows EXE me available hai.");
  if (process.platform !== "win32") throw new Error("Ye update Windows ke liye hai.");
  const stat = await fs.promises.stat(sourcePath);
  if (!stat.isFile() || stat.size < 10 * 1024 * 1024) throw new Error("Update EXE file invalid ya incomplete hai.");
  const handle = await fs.promises.open(sourcePath, "r");
  const signature = Buffer.alloc(2);
  await handle.read(signature, 0, 2, 0);
  await handle.close();
  if (signature.toString("ascii") !== "MZ") throw new Error("Selected file valid Windows EXE nahi hai.");
  const installExe = process.env.PORTABLE_EXECUTABLE_FILE || process.execPath;
  const stage = path.join(app.getPath("userData"), "updates");
  await fs.promises.mkdir(stage, { recursive: true });
  const stagedExe = path.join(stage, "Mahadev-AI-Shorts-Maker-update.exe");
  await fs.promises.copyFile(sourcePath, stagedExe);
  const ps1 = path.join(stage, "apply-update.ps1");
  const script = [
    "$ErrorActionPreference = 'Stop'",
    "$pidToWait = " + process.pid,
    "$target = " + JSON.stringify(installExe),
    "$source = " + JSON.stringify(stagedExe),
    "try { Wait-Process -Id $pidToWait -Timeout 90 -ErrorAction SilentlyContinue } catch {}",
    "Start-Sleep -Seconds 2",
    "Copy-Item -LiteralPath $source -Destination $target -Force",
    "Start-Process -FilePath $target"
  ].join("\r\n");
  await fs.promises.writeFile(ps1, script, "utf8");
  spawn("powershell.exe", ["-NoProfile", "-ExecutionPolicy", "Bypass", "-File", ps1], { detached: true, stdio: "ignore", windowsHide: true }).unref();
  setTimeout(() => app.quit(), 500);
  return { success: true, message: "Update staged. App close hote hi update apply hoga." };
}
async function downloadOnlineUpdate(url, expectedName) {
  const parsed = new URL(url);
  if (parsed.protocol !== "https:" || parsed.hostname !== "github.com") throw new Error("Untrusted update download URL.");
  if (!/^Mahadev-AI-Shorts-Maker-.*\.exe$/i.test(expectedName)) throw new Error("Unexpected update filename.");
  const response = await fetch(url, { redirect: "follow" });
  if (!response.ok || !response.body) throw new Error("Update download failed: HTTP " + response.status);
  const destination = path.join(app.getPath("userData"), "updates", expectedName);
  await fs.promises.mkdir(path.dirname(destination), { recursive: true });
  const temp = destination + ".download";
  let received = 0;
  const total = Number(response.headers.get("content-length")) || 0;
  const meter = new Transform({ transform(chunk, enc, cb) {
    received += chunk.length;
    for (const win of BrowserWindow.getAllWindows()) win.webContents.send("app-update:progress", { received, total, percent: total ? Math.min(99, Math.floor(received / total * 100)) : null });
    cb(null, chunk);
  }});
  await pipeline(Readable.fromWeb(response.body), meter, fs.createWriteStream(temp));
  const stat = await fs.promises.stat(temp);
  if (stat.size < 10 * 1024 * 1024) { await fs.promises.unlink(temp); throw new Error("Downloaded update is incomplete."); }
  await fs.promises.rename(temp, destination);
  return destination;
}
async function chooseUpdateFile() {
  const result = await dialog.showOpenDialog({ title: "Select Mahadev AI Shorts Maker update EXE", properties: ["openFile"], filters: [{ name: "Windows EXE", extensions: ["exe"] }] });
  if (result.canceled || !result.filePaths[0]) return { canceled: true };
  return stageUpdate(result.filePaths[0]);
}

function createWindow() {
  const win = new BrowserWindow({
    width: 1180, height: 900, minWidth: 900, minHeight: 650,
    title: "Mahadev AI Shorts Maker",
    backgroundColor: "#0b1020",
    webPreferences: { preload: path.join(__dirname, "preload.cjs"), contextIsolation: true, nodeIntegration: false, sandbox: true }
  });
  win.loadFile("index.html");
}

ipcMain.handle("app-update:check", () => checkOnlineUpdate());
ipcMain.handle("app-update:download-install", async (_event, url, name) => { const file = await downloadOnlineUpdate(url, name); return stageUpdate(file); });
ipcMain.handle("app-update:choose-file", () => chooseUpdateFile());
ipcMain.handle("offline-ai:status", () => modelStatus());
ipcMain.handle("offline-ai:download", async () => downloadModel());
ipcMain.handle("offline-ai:load", async () => { await loadOfflineModel(); return modelStatus(); });
ipcMain.handle("offline-ai:prompt", async (_event, prompt) => {
  if (typeof prompt !== "string" || !prompt.trim()) throw new Error("Please enter a question or task.");
  if (prompt.length > 4000) throw new Error("Please keep the prompt under 4,000 characters.");
  await loadOfflineModel();
  return chatSession.prompt("You are Mahadev AI Helper, a concise and helpful assistant. Reply in the same language as the user, especially Hindi or Hinglish when used. Help with writing, ideas, explanations, planning and app tasks. If you are unsure, say so. Do not invent current facts.\n\nUser: " + prompt.trim(), { maxTokens: 512 });
});

app.whenReady().then(() => {
  createWindow();
  app.on("activate", () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
});
app.on("window-all-closed", () => { if (process.platform !== "darwin") app.quit(); });
