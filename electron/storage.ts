import { app } from "electron";
import { promises as fs } from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import type {
  ActivityEvent,
  AppConfig,
  AppDraft,
  AppSettings,
  PersistedState,
} from "../src/shared/types";

const DEFAULT_SETTINGS: AppSettings = {
  theme: "dark",
  accent: "#ff6678",
  startWithWindows: false,
  minimizeToTray: true,
  confirmBeforeStop: true,
  confirmBeforeDelete: true,
  notifications: true,
  debugMode: false,
};

const EMPTY_STATE: PersistedState = {
  apps: [],
  settings: DEFAULT_SETTINGS,
  activity: [],
};

export class StorageService {
  private readonly statePath = path.join(app.getPath("userData"), "state.json");
  private state: PersistedState = structuredClone(EMPTY_STATE);
  private writeQueue: Promise<void> = Promise.resolve();

  async initialize() {
    await fs.mkdir(app.getPath("userData"), { recursive: true });
    try {
      const raw = await fs.readFile(this.statePath, "utf8");
      this.state = validatePersistedState(JSON.parse(raw));
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code !== "ENOENT") {
        await fs.rename(
          this.statePath,
          `${this.statePath}.invalid-${Date.now()}`,
        ).catch(() => undefined);
        this.state = {
          ...structuredClone(EMPTY_STATE),
          activity: [
            {
              id: crypto.randomUUID(),
              level: "warning",
              title: "Configuration recovered",
              message: "An invalid local state file was preserved and Haven reset to safe defaults.",
              timestamp: new Date().toISOString(),
            },
          ],
        };
      }
      await this.persist();
    }
  }

  getState(): PersistedState {
    return structuredClone(this.state);
  }

  getApp(id: string) {
    return this.state.apps.find((item) => item.id === id);
  }

  async saveApp(draft: AppDraft): Promise<AppConfig> {
    validateDraft(draft);
    if (
      draft.type === "discord" &&
      draft.env.some((item) => /(^|_)TOKEN($|_)/i.test(item.key.trim()))
    ) {
      throw new Error(
        "Discord tokens cannot be stored as plain environment variables. Use Protected Bot Token instead.",
      );
    }
    const existing = draft.id ? this.getApp(draft.id) : undefined;
    const saved: AppConfig = {
      ...draft,
      id: existing?.id ?? crypto.randomUUID(),
      order: existing?.order ?? this.state.apps.length,
      hasSecret: existing?.hasSecret ?? false,
      createdAt: existing?.createdAt ?? new Date().toISOString(),
    };
    if (existing) {
      this.state.apps = this.state.apps.map((item) => (item.id === saved.id ? saved : item));
    } else {
      this.state.apps.push(saved);
    }
    await this.persist();
    return structuredClone(saved);
  }

  async removeApp(id: string) {
    this.state.apps = this.state.apps.filter((item) => item.id !== id);
    this.state.apps.forEach((item, index) => {
      item.order = index;
    });
    await this.persist();
  }

  async reorderApps(ids: string[]) {
    const order = new Map(ids.map((id, index) => [id, index]));
    this.state.apps.forEach((item) => {
      item.order = order.get(item.id) ?? item.order;
    });
    this.state.apps.sort((a, b) => a.order - b.order);
    await this.persist();
  }

  async togglePinned(id: string) {
    const target = this.getApp(id);
    if (!target) throw new Error("Application not found.");
    target.pinned = !target.pinned;
    await this.persist();
    return target.pinned;
  }

  async setHasSecret(id: string, hasSecret: boolean) {
    const target = this.getApp(id);
    if (!target) throw new Error("Application not found.");
    target.hasSecret = hasSecret;
    await this.persist();
  }

  async saveSettings(settings: Partial<AppSettings>) {
    this.state.settings = validatePersistedState({
      ...this.state,
      settings: { ...this.state.settings, ...settings },
    }).settings;
    await this.persist();
    return structuredClone(this.state.settings);
  }

  async addActivity(event: Omit<ActivityEvent, "id" | "timestamp">) {
    this.state.activity.unshift({
      ...event,
      id: crypto.randomUUID(),
      timestamp: new Date().toISOString(),
    });
    this.state.activity = this.state.activity.slice(0, 250);
    await this.persist();
  }

  async clearActivity() {
    this.state.activity = [];
    await this.persist();
  }

  async exportState(destination: string) {
    await fs.writeFile(destination, JSON.stringify(this.state, null, 2), "utf8");
  }

  async importState(source: string) {
    this.state = validatePersistedState(JSON.parse(await fs.readFile(source, "utf8")));
    await this.persist();
  }

  async reset() {
    this.state = structuredClone(EMPTY_STATE);
    await this.persist();
  }

  private async persist() {
    const serialized = JSON.stringify(this.state, null, 2);
    const write = this.writeQueue.then(async () => {
      const temporaryPath = `${this.statePath}.${process.pid}.tmp`;
      await fs.writeFile(temporaryPath, serialized, "utf8");
      await fs.rename(temporaryPath, this.statePath);
    });
    this.writeQueue = write.catch(() => undefined);
    await write;
  }
}

const APP_TYPES = new Set(["minecraft", "discord", "website", "custom"]);
const STATUSES = new Set(["info", "success", "warning", "error"]);
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function validateDraft(draft: AppDraft) {
  if (!draft || typeof draft !== "object") throw new Error("Invalid application configuration.");
  if (!APP_TYPES.has(draft.type)) throw new Error("Choose a supported application type.");
  if (typeof draft.name !== "string" || !draft.name.trim() || draft.name.length > 120) {
    throw new Error("Application name must be between 1 and 120 characters.");
  }
  for (const value of [draft.icon, draft.directory, draft.startCommand, draft.stopCommand]) {
    if (typeof value !== "string") throw new Error("Application configuration contains invalid text.");
  }
  if (draft.port !== undefined && (!Number.isInteger(draft.port) || draft.port < 1 || draft.port > 65_535)) {
    throw new Error("Port must be a whole number between 1 and 65535.");
  }
  if (
    draft.ramLimitMb !== undefined &&
    (!Number.isFinite(draft.ramLimitMb) || draft.ramLimitMb < 128 || draft.ramLimitMb > 262_144)
  ) {
    throw new Error("RAM limit must be between 128 MB and 256 GB.");
  }
  if (!Number.isFinite(draft.restartDelaySeconds) || draft.restartDelaySeconds < 1 || draft.restartDelaySeconds > 86_400) {
    throw new Error("Restart delay must be between 1 second and 24 hours.");
  }
  if (![draft.autoStart, draft.autoRestart, draft.pinned].every((value) => typeof value === "boolean")) {
    throw new Error("Application switches contain invalid values.");
  }
  if (
    !Array.isArray(draft.env) ||
    draft.env.some(
      (item) =>
        !item ||
        typeof item.key !== "string" ||
        typeof item.value !== "string" ||
        item.key.length > 200 ||
        item.value.length > 32_768,
    )
  ) {
    throw new Error("Environment variables are invalid.");
  }
  if (!draft.advanced || typeof draft.advanced !== "object" || Array.isArray(draft.advanced)) {
    throw new Error("Advanced configuration is invalid.");
  }
  if (
    draft.advanced.maxBackups !== undefined &&
    (!Number.isInteger(draft.advanced.maxBackups) ||
      draft.advanced.maxBackups < 1 ||
      draft.advanced.maxBackups > 50)
  ) {
    throw new Error("Maximum backups must be between 1 and 50.");
  }
  if (
    draft.advanced.scheduledRestartHours !== undefined &&
    (!Number.isFinite(draft.advanced.scheduledRestartHours) ||
      draft.advanced.scheduledRestartHours <= 0 ||
      draft.advanced.scheduledRestartHours > 8_760)
  ) {
    throw new Error("Scheduled restart must be between 1 hour and 1 year.");
  }
}

function validatePersistedState(value: unknown): PersistedState {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new Error("That file is not a valid Haven configuration.");
  }
  const candidate = value as Partial<PersistedState>;
  if (!Array.isArray(candidate.apps) || !candidate.settings || typeof candidate.settings !== "object") {
    throw new Error("That file is not a valid Haven configuration.");
  }
  const ids = new Set<string>();
  const apps = candidate.apps.map((application, index) => {
    validateDraft(application);
    if (!UUID.test(application.id) || ids.has(application.id)) {
      throw new Error("Application IDs in this configuration are invalid.");
    }
    ids.add(application.id);
    if (
      typeof application.createdAt !== "string" ||
      typeof application.order !== "number" ||
      typeof application.hasSecret !== "boolean"
    ) {
      throw new Error("An application record is incomplete.");
    }
    return { ...application, order: index };
  });
  const settings = candidate.settings as Partial<AppSettings>;
  if (
    settings.theme !== "dark" &&
    settings.theme !== "light"
  ) {
    throw new Error("Theme setting is invalid.");
  }
  if (typeof settings.accent !== "string" || !/^#[0-9a-f]{6}$/i.test(settings.accent)) {
    throw new Error("Accent colour is invalid.");
  }
  for (const key of [
    "startWithWindows",
    "minimizeToTray",
    "confirmBeforeStop",
    "confirmBeforeDelete",
    "notifications",
    "debugMode",
  ] as const) {
    if (typeof settings[key] !== "boolean") throw new Error(`Setting ${key} is invalid.`);
  }
  if (
    settings.customBackground !== undefined &&
    (typeof settings.customBackground !== "string" ||
      !/^data:image\/(png|jpeg|webp);base64,/.test(settings.customBackground))
  ) {
    throw new Error("Custom background is invalid.");
  }
  const activity = (candidate.activity ?? []).map((event) => {
    if (
      !event ||
      !UUID.test(event.id) ||
      (event.appId !== undefined && !UUID.test(event.appId)) ||
      !STATUSES.has(event.level) ||
      typeof event.title !== "string" ||
      typeof event.message !== "string" ||
      typeof event.timestamp !== "string"
    ) {
      throw new Error("Activity history contains an invalid record.");
    }
    return event;
  });
  return {
    apps,
    settings: {
      theme: settings.theme,
      accent: settings.accent,
      customBackground: settings.customBackground,
      startWithWindows: settings.startWithWindows as boolean,
      minimizeToTray: settings.minimizeToTray as boolean,
      confirmBeforeStop: settings.confirmBeforeStop as boolean,
      confirmBeforeDelete: settings.confirmBeforeDelete as boolean,
      notifications: settings.notifications as boolean,
      debugMode: settings.debugMode as boolean,
    },
    activity,
  };
}
