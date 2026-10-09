const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('nexa', {
  minimize: () => ipcRenderer.send('window:minimize'),
  maximize: () => ipcRenderer.send('window:maximize'),
  close: () => ipcRenderer.send('window:close'),
});
