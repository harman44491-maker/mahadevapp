const { contextBridge, shell } = require("electron");
contextBridge.exposeInMainWorld("desktop", {
  openExternal: (url) => {
    if (typeof url === "string" && /^https:\/\//i.test(url)) shell.openExternal(url);
  }
});
