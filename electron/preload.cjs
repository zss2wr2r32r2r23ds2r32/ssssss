const { contextBridge, ipcRenderer, webUtils } = require('electron');

const port = process.env.PORT || '4177';

contextBridge.exposeInMainWorld('nexa', {
  minimize: () => ipcRenderer.send('window:minimize'),
  maximize: () => ipcRenderer.send('window:maximize'),
  close: () => ipcRenderer.send('window:close'),
  apiBase: `http://127.0.0.1:${port}`,
  filePath: (file) => {
    try {
      return webUtils.getPathForFile(file);
    } catch {
      return '';
    }
  },
  openExternal: (url) => ipcRenderer.invoke('shell:open-external', url),
});
