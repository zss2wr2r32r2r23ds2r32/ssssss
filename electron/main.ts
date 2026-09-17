import {
  app,
  BrowserWindow,
  dialog,
  ipcMain,
  Menu,
  nativeImage,
  shell,
  Tray,
} from "electron";
import { promises as fs } from "node:fs";
import path from "node:path";
import { StorageService } from "./storage";
import { SecretVault } from "./security";
import { ProcessManager } from "./process-manager";
import { FileService } from "./file-service";
import { BackupService } from "./backup-service";
import { SystemService } from "./system-service";
import type { AppDraft, AppSettings } from "../src/shared/types";

let mainWindow: BrowserWindow | undefined;
let tray: Tray | undefined;
let quitting = false;
let systemTimer: NodeJS.Timeout | undefined;

const storage = new StorageService();
const vault = new SecretVault();
const files = new FileService(storage);
const backups = new BackupService(storage);
const system = new SystemService();
const processes = new ProcessManager(storage, vault, (channel, payload) => {
  if (!mainWindow?.isDestroyed()) mainWindow?.webContents.send(channel, payload);
});

const emitState = () => {
  const window = mainWindow;
  if (window && !window.isDestroyed()) window.webContents.send("state:changed", storage.getState());
};

async function quitApplication() {
  if (quitting) return;
  quitting = true;
  await processes.stopAll();
  app.quit();
}

async function createWindow() {
  const isDevelopment = Boolean(process.env.VITE_DEV_SERVER_URL);
  mainWindow = new BrowserWindow({
    width: 1440,
    height: 900,
    minWidth: 1060,
    minHeight: 680,
    show: false,
    titleBarStyle: "hidden",
    titleBarOverlay: {
      color: "#11141ce6",
      symbolColor: "#e8eaf1",
      height: 40,
    },
    backgroundColor: "#080b12",
    ...(process.platform === "win32" ? { backgroundMaterial: "mica" as const } : {}),
    webPreferences: {
      preload: path.join(__dirname, "preload.js"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });
  mainWindow.setMenuBarVisibility(false);
  mainWindow.once("ready-to-show", () => mainWindow?.show());
  mainWindow.webContents.on("will-navigate", (event) => event.preventDefault());
  mainWindow.webContents.setWindowOpenHandler(() => ({ action: "deny" }));
  mainWindow.webContents.session.setPermissionRequestHandler((_contents, _permission, callback) =>
    callback(false),
  );
  mainWindow.on("close", (event) => {
    if (!quitting && storage.getState().settings.minimizeToTray) {
      event.preventDefault();
      mainWindow?.hide();
    } else if (!quitting) {
      event.preventDefault();
      void quitApplication();
    }
  });
  mainWindow.on("closed", () => {
    mainWindow = undefined;
  });
  if (isDevelopment) {
    await mainWindow.loadURL(process.env.VITE_DEV_SERVER_URL!);
  } else {
    await mainWindow.loadFile(path.join(__dirname, "../dist/index.html"));
  }
}

function createTray() {
  const icon = nativeImage
    .createFromDataURL(
      "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAATUlEQVR42mP8z8Dwn4ECwESJ5lEDRg0YNYDE////Gf4zMDAwMDIyMvzHqAakGaYGSFOMmkGkGaYGSFOMmkGkGaYGSFOMmkGkGaYGSCMAAKuiH+H5y11HAAAAAElFTkSuQmCC",
    )
    .resize({ width: 16, height: 16 });
  tray = new Tray(icon);
  tray.setToolTip("Haven Process Manager");
  tray.setContextMenu(
    Menu.buildFromTemplate([
      {
        label: "Open Haven",
        click: () => (mainWindow ? mainWindow.show() : void createWindow()),
      },
      { type: "separator" },
      {
        label: "Quit",
        click: () => {
          void quitApplication();
        },
      },
    ]),
  );
  tray.on("double-click", () => mainWindow?.show());
}

function registerIpc() {
  ipcMain.handle("app:bootstrap", async () => ({
    state: storage.getState(),
    metrics: processes.getMetrics(),
    system: await system.metrics(),
    runtimes: await system.runtimes(),
  }));
  ipcMain.handle("app:version", () => app.getVersion());
  ipcMain.handle("app:paths", () => ({
    data: app.getPath("userData"),
    logs: path.join(app.getPath("userData"), "logs"),
  }));

  ipcMain.handle("apps:save", async (_event, draft: AppDraft) => {
    const saved = await storage.saveApp(draft);
    emitState();
    return saved;
  });
  ipcMain.handle("apps:delete", async (_event, id: string) => {
    await processes.stop(id);
    processes.forget(id);
    await storage.removeApp(id);
    await vault.remove(id);
    emitState();
  });
  ipcMain.handle("apps:reorder", async (_event, ids: string[]) => {
    await storage.reorderApps(ids);
    emitState();
  });
  ipcMain.handle("apps:pin", async (_event, id: string) => {
    const pinned = await storage.togglePinned(id);
    emitState();
    return pinned;
  });

  ipcMain.handle("process:start", (_event, id: string, password?: string) =>
    processes.start(id, password),
  );
  ipcMain.handle("process:stop", (_event, id: string) => processes.stop(id));
  ipcMain.handle("process:restart", (_event, id: string, password?: string) =>
    processes.restart(id, password),
  );
  ipcMain.handle("process:command", (_event, id: string, command: string) =>
    processes.sendCommand(id, command),
  );
  ipcMain.handle("process:console:get", (_event, id: string) => processes.getConsole(id));
  ipcMain.handle("process:console:clear", (_event, id: string) => processes.clearConsole(id));

  ipcMain.handle(
    "secret:set",
    async (_event, id: string, token: string, password: string) => {
      try {
        await vault.set(id, token, password);
        await storage.setHasSecret(id, true);
      } catch (error) {
        await vault.remove(id).catch(() => undefined);
        throw error;
      }
      emitState();
    },
  );
  ipcMain.handle("secret:remove", async (_event, id: string) => {
    await storage.setHasSecret(id, false);
    await vault.remove(id);
    emitState();
  });

  ipcMain.handle("files:list", (_event, id: string, relativePath?: string) =>
    files.list(id, relativePath),
  );
  ipcMain.handle("files:read", (_event, id: string, relativePath: string) =>
    files.readText(id, relativePath),
  );
  ipcMain.handle(
    "files:write",
    (_event, id: string, relativePath: string, content: string) =>
      files.writeText(id, relativePath, content),
  );
  ipcMain.handle(
    "files:mkdir",
    (_event, id: string, relativeParent: string, name: string) =>
      files.createFolder(id, relativeParent, name),
  );
  ipcMain.handle("files:rename", (_event, id: string, relativePath: string, name: string) =>
    files.rename(id, relativePath, name),
  );
  ipcMain.handle("files:delete", (_event, id: string, relativePath: string) =>
    files.delete(id, relativePath),
  );
  ipcMain.handle("files:search", (_event, id: string, query: string) =>
    files.search(id, query),
  );
  ipcMain.handle(
    "files:upload",
    async (_event, id: string, relativeDirectory: string) => {
      const result = await dialog.showOpenDialog(mainWindow!, {
        title: "Add files",
        properties: ["openFile", "multiSelections"],
      });
      if (!result.canceled) await files.copyInto(id, relativeDirectory, result.filePaths);
      return !result.canceled;
    },
  );
  ipcMain.handle("files:download", async (_event, id: string, relativePath: string) => {
    const result = await dialog.showSaveDialog(mainWindow!, {
      defaultPath: path.basename(relativePath),
    });
    if (!result.canceled && result.filePath) await files.copyOut(id, relativePath, result.filePath);
    return !result.canceled;
  });

  ipcMain.handle("backups:list", (_event, id: string) => backups.list(id));
  ipcMain.handle("backups:create", async (_event, id: string) => {
    const metric = processes.getMetrics().find((item) => item.appId === id);
    if (metric && !["offline", "crashed"].includes(metric.status)) {
      throw new Error("Stop the application before creating a consistent backup.");
    }
    return backups.create(id);
  });
  ipcMain.handle("backups:restore", async (_event, id: string, backupId: string) => {
    const metric = processes.getMetrics().find((item) => item.appId === id);
    if (metric && !["offline", "crashed"].includes(metric.status)) {
      throw new Error("Stop the application before restoring a backup.");
    }
    await backups.restore(id, backupId);
  });
  ipcMain.handle("backups:delete", (_event, id: string, backupId: string) =>
    backups.delete(id, backupId),
  );

  ipcMain.handle("dialog:directory", async () => {
    const result = await dialog.showOpenDialog(mainWindow!, {
      properties: ["openDirectory", "createDirectory"],
    });
    return result.canceled ? undefined : result.filePaths[0];
  });
  ipcMain.handle("dialog:file", async (_event, extensions?: string[]) => {
    const result = await dialog.showOpenDialog(mainWindow!, {
      properties: ["openFile"],
      filters: extensions?.length ? [{ name: "Supported files", extensions }] : undefined,
    });
    return result.canceled ? undefined : result.filePaths[0];
  });
  ipcMain.handle("dialog:background", async () => {
    const result = await dialog.showOpenDialog(mainWindow!, {
      title: "Choose background image",
      properties: ["openFile"],
      filters: [{ name: "Images", extensions: ["png", "jpg", "jpeg", "webp"] }],
    });
    if (result.canceled) return undefined;
    const selected = result.filePaths[0];
    const stat = await fs.stat(selected);
    if (stat.size > 12 * 1024 * 1024) throw new Error("Choose an image smaller than 12 MB.");
    const mime = path.extname(selected).toLowerCase() === ".png" ? "image/png"
      : path.extname(selected).toLowerCase() === ".webp" ? "image/webp"
        : "image/jpeg";
    return `data:${mime};base64,${(await fs.readFile(selected)).toString("base64")}`;
  });
  ipcMain.handle("shell:folder", (_event, target: string) => shell.openPath(target));
  ipcMain.handle("shell:url", (_event, target: string) => {
    const url = new URL(target);
    if (!["http:", "https:"].includes(url.protocol)) throw new Error("Unsupported URL.");
    return shell.openExternal(url.toString());
  });

  ipcMain.handle("settings:save", async (_event, patch: Partial<AppSettings>) => {
    const settings = await storage.saveSettings(patch);
    if ("startWithWindows" in patch) {
      app.setLoginItemSettings({ openAtLogin: settings.startWithWindows });
    }
    emitState();
    return settings;
  });
  ipcMain.handle("config:export", async () => {
    const result = await dialog.showSaveDialog(mainWindow!, {
      title: "Export configuration",
      defaultPath: "haven-config.json",
      filters: [{ name: "JSON", extensions: ["json"] }],
    });
    if (!result.canceled && result.filePath) await storage.exportState(result.filePath);
    return !result.canceled;
  });
  ipcMain.handle("config:import", async () => {
    const result = await dialog.showOpenDialog(mainWindow!, {
      title: "Import configuration",
      properties: ["openFile"],
      filters: [{ name: "JSON", extensions: ["json"] }],
    });
    if (!result.canceled) {
      await processes.stopAll();
      await storage.importState(result.filePaths[0]);
      await vault.retain(storage.getState().apps.filter((item) => item.hasSecret).map((item) => item.id));
      app.setLoginItemSettings({ openAtLogin: storage.getState().settings.startWithWindows });
      emitState();
    }
    return !result.canceled;
  });
  ipcMain.handle("config:reset", async () => {
    await processes.stopAll();
    await storage.reset();
    await vault.clear();
    app.setLoginItemSettings({ openAtLogin: false });
    emitState();
  });
  ipcMain.handle("activity:clear", async () => {
    await storage.clearActivity();
    emitState();
  });
}

const hasSingleInstanceLock = app.requestSingleInstanceLock();
if (!hasSingleInstanceLock) {
  app.quit();
} else {
  app.on("second-instance", () => {
    if (mainWindow) {
      if (mainWindow.isMinimized()) mainWindow.restore();
      mainWindow.show();
      mainWindow.focus();
    } else {
      void createWindow();
    }
  });

  app.whenReady().then(async () => {
    await storage.initialize();
    await vault.initialize();
    app.setLoginItemSettings({ openAtLogin: storage.getState().settings.startWithWindows });
    registerIpc();
    await createWindow();
    createTray();
    processes.startMonitoring();
    systemTimer = setInterval(() => {
      void system
        .metrics()
        .then((metrics) => {
          const window = mainWindow;
          if (window && !window.isDestroyed()) window.webContents.send("system:metrics", metrics);
        })
        .catch(() => undefined);
    }, 2_000);

    for (const config of storage.getState().apps.filter((item) => item.autoStart && !item.hasSecret)) {
      void processes.start(config.id).catch(() => undefined);
    }
  });
}

app.on("activate", () => {
  if (!mainWindow) void createWindow();
  else mainWindow.show();
});

app.on("before-quit", () => {
  quitting = true;
  if (systemTimer) clearInterval(systemTimer);
  processes.dispose();
});

app.on("window-all-closed", () => {
  if (process.platform !== "darwin" && quitting) app.quit();
});
