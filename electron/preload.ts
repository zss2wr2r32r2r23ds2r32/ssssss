import { contextBridge, ipcRenderer } from "electron";
import type {
  AppDraft,
  AppSettings,
  ConsoleLine,
  PersistedState,
  ProcessMetrics,
  SystemMetrics,
} from "../src/shared/types";

const subscribe = <T>(channel: string, listener: (payload: T) => void) => {
  const wrapped = (_event: Electron.IpcRendererEvent, payload: T) => listener(payload);
  ipcRenderer.on(channel, wrapped);
  return () => ipcRenderer.removeListener(channel, wrapped);
};

contextBridge.exposeInMainWorld("haven", {
  bootstrap: () => ipcRenderer.invoke("app:bootstrap"),
  version: () => ipcRenderer.invoke("app:version"),
  paths: () => ipcRenderer.invoke("app:paths"),
  apps: {
    save: (draft: AppDraft) => ipcRenderer.invoke("apps:save", draft),
    delete: (id: string) => ipcRenderer.invoke("apps:delete", id),
    reorder: (ids: string[]) => ipcRenderer.invoke("apps:reorder", ids),
    pin: (id: string) => ipcRenderer.invoke("apps:pin", id),
  },
  process: {
    start: (id: string, password?: string) => ipcRenderer.invoke("process:start", id, password),
    stop: (id: string) => ipcRenderer.invoke("process:stop", id),
    restart: (id: string, password?: string) =>
      ipcRenderer.invoke("process:restart", id, password),
    command: (id: string, command: string) =>
      ipcRenderer.invoke("process:command", id, command),
    getConsole: (id: string) => ipcRenderer.invoke("process:console:get", id),
    clearConsole: (id: string) => ipcRenderer.invoke("process:console:clear", id),
  },
  secrets: {
    set: (id: string, token: string, password: string) =>
      ipcRenderer.invoke("secret:set", id, token, password),
    remove: (id: string) => ipcRenderer.invoke("secret:remove", id),
  },
  files: {
    list: (id: string, relativePath = "") => ipcRenderer.invoke("files:list", id, relativePath),
    read: (id: string, relativePath: string) =>
      ipcRenderer.invoke("files:read", id, relativePath),
    write: (id: string, relativePath: string, content: string) =>
      ipcRenderer.invoke("files:write", id, relativePath, content),
    mkdir: (id: string, relativeParent: string, name: string) =>
      ipcRenderer.invoke("files:mkdir", id, relativeParent, name),
    rename: (id: string, relativePath: string, name: string) =>
      ipcRenderer.invoke("files:rename", id, relativePath, name),
    delete: (id: string, relativePath: string) =>
      ipcRenderer.invoke("files:delete", id, relativePath),
    search: (id: string, query: string) => ipcRenderer.invoke("files:search", id, query),
    upload: (id: string, relativePath: string) =>
      ipcRenderer.invoke("files:upload", id, relativePath),
    download: (id: string, relativePath: string) =>
      ipcRenderer.invoke("files:download", id, relativePath),
  },
  backups: {
    list: (id: string) => ipcRenderer.invoke("backups:list", id),
    create: (id: string) => ipcRenderer.invoke("backups:create", id),
    restore: (id: string, backupId: string) =>
      ipcRenderer.invoke("backups:restore", id, backupId),
    delete: (id: string, backupId: string) =>
      ipcRenderer.invoke("backups:delete", id, backupId),
  },
  dialog: {
    directory: () => ipcRenderer.invoke("dialog:directory"),
    file: (extensions?: string[]) => ipcRenderer.invoke("dialog:file", extensions),
    background: () => ipcRenderer.invoke("dialog:background"),
  },
  shell: {
    folder: (path: string) => ipcRenderer.invoke("shell:folder", path),
    url: (url: string) => ipcRenderer.invoke("shell:url", url),
  },
  settings: {
    save: (patch: Partial<AppSettings>) => ipcRenderer.invoke("settings:save", patch),
    export: () => ipcRenderer.invoke("config:export"),
    import: () => ipcRenderer.invoke("config:import"),
    reset: () => ipcRenderer.invoke("config:reset"),
  },
  activity: {
    clear: () => ipcRenderer.invoke("activity:clear"),
  },
  events: {
    onState: (listener: (state: PersistedState) => void) =>
      subscribe<PersistedState>("state:changed", listener),
    onMetrics: (listener: (metrics: ProcessMetrics[]) => void) =>
      subscribe<ProcessMetrics[]>("process:metrics", listener),
    onSystem: (listener: (metrics: SystemMetrics) => void) =>
      subscribe<SystemMetrics>("system:metrics", listener),
    onConsole: (listener: (line: ConsoleLine) => void) =>
      subscribe<ConsoleLine>("process:console", listener),
  },
});
