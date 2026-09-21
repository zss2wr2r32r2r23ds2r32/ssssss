const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('avix', {
  isElectron: true,
  getStore: () => ipcRenderer.invoke('store:get'),
  setStore: (data) => ipcRenderer.invoke('store:set', data),
  copyText: (text) => ipcRenderer.invoke('clipboard:write', text),
  completeAi: (payload) => ipcRenderer.invoke('ai:complete', payload),
});
