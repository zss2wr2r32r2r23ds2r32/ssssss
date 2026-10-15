import { contextBridge, ipcRenderer } from 'electron'

contextBridge.exposeInMainWorld('nauticalOverlay', {
  onSettings: (listener: (settings: unknown) => void) => {
    const wrapped = (_event: Electron.IpcRendererEvent, settings: unknown) => listener(settings)
    ipcRenderer.on('overlay:settings', wrapped)
    return () => ipcRenderer.removeListener('overlay:settings', wrapped)
  }
})
