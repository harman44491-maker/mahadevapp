const { contextBridge, shell, ipcRenderer } = require("electron");
contextBridge.exposeInMainWorld("desktop", {
  openExternal: (url) => {
    if (typeof url === "string" && /^https:\/\//i.test(url)) shell.openExternal(url);
  }
});
contextBridge.exposeInMainWorld("appUpdater", {
  check: () => ipcRenderer.invoke("app-update:check"),
  downloadAndInstall: (url, name) => ipcRenderer.invoke("app-update:download-install", url, name),
  chooseFile: () => ipcRenderer.invoke("app-update:choose-file"),
  onProgress: (callback) => { if (typeof callback !== "function") return; const listener = (_event, data) => callback(data); ipcRenderer.on("app-update:progress", listener); return () => ipcRenderer.removeListener("app-update:progress", listener); }
});
contextBridge.exposeInMainWorld("offlineAI", {
  status: () => ipcRenderer.invoke("offline-ai:status"),
  download: () => ipcRenderer.invoke("offline-ai:download"),
  load: () => ipcRenderer.invoke("offline-ai:load"),
  prompt: (text) => ipcRenderer.invoke("offline-ai:prompt", text),
  onProgress: (callback) => {
    if (typeof callback !== "function") return;
    const listener = (_event, data) => callback(data);
    ipcRenderer.on("offline-ai:progress", listener);
    return () => ipcRenderer.removeListener("offline-ai:progress", listener);
  }
});
