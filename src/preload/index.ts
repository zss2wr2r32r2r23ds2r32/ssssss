import { contextBridge, ipcRenderer } from 'electron'
import { IPC_CHANNELS, IPC_EVENTS, isIpcChannel } from '../shared/ipc'

const api = {
  invoke: (channel: string, payload?: unknown) => {
    if (!isIpcChannel(channel)) {
      return Promise.reject(new Error('Blocked IPC channel'))
    }
    return ipcRenderer.invoke(channel, payload)
  },
  on: (channel: string, listener: (payload: unknown) => void): (() => void) => {
    if (!(IPC_EVENTS as readonly string[]).includes(channel)) {
      return () => {
        /* blocked */
      }
    }
    const wrapped = (_event: Electron.IpcRendererEvent, payload: unknown) => listener(payload)
    ipcRenderer.on(channel, wrapped)
    return () => {
      ipcRenderer.removeListener(channel, wrapped)
    }
  },
  channels: IPC_CHANNELS,
  events: IPC_EVENTS
}

contextBridge.exposeInMainWorld('nautical', api)

export type NauticalPreloadApi = typeof api
