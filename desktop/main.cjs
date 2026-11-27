const { app, BrowserWindow, shell, ipcMain } = require("electron");
const path = require("path");
const fs = require("fs");
const { pipeline } = require("stream/promises");
const { Readable, Transform } = require("stream");

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

function createWindow() {
  const win = new BrowserWindow({
    width: 1180, height: 900, minWidth: 900, minHeight: 650,
    title: "Mahadev AI Shorts Maker",
    backgroundColor: "#0b1020",
    webPreferences: { preload: path.join(__dirname, "preload.cjs"), contextIsolation: true, nodeIntegration: false, sandbox: true }
  });
  win.loadFile("index.html");
}

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
