const { contextBridge, ipcRenderer } = require('electron');

const port = process.env.PORT || '4177';

contextBridge.exposeInMainWorld('nexa', {
  minimize: () => ipcRenderer.send('window:minimize'),
  maximize: () => ipcRenderer.send('window:maximize'),
  close: () => ipcRenderer.send('window:close'),
  apiBase: `http://127.0.0.1:${port}`,
});
