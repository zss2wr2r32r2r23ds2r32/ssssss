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

  async initialize() {
    await fs.mkdir(app.getPath("userData"), { recursive: true });
    try {
      const raw = await fs.readFile(this.statePath, "utf8");
      const parsed = JSON.parse(raw) as Partial<PersistedState>;
      this.state = {
        apps: parsed.apps ?? [],
        settings: { ...DEFAULT_SETTINGS, ...(parsed.settings ?? {}) },
        activity: parsed.activity ?? [],
      };
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code !== "ENOENT") throw error;
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
    this.state.settings = { ...this.state.settings, ...settings };
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
    const parsed = JSON.parse(await fs.readFile(source, "utf8")) as PersistedState;
    if (!Array.isArray(parsed.apps) || !parsed.settings) {
      throw new Error("That file is not a valid process manager configuration.");
    }
    this.state = {
      apps: parsed.apps,
      settings: { ...DEFAULT_SETTINGS, ...parsed.settings },
      activity: Array.isArray(parsed.activity) ? parsed.activity : [],
    };
    await this.persist();
  }

  async reset() {
    this.state = structuredClone(EMPTY_STATE);
    await this.persist();
  }

  private async persist() {
    const temporaryPath = `${this.statePath}.tmp`;
    await fs.writeFile(temporaryPath, JSON.stringify(this.state, null, 2), "utf8");
    await fs.rename(temporaryPath, this.statePath);
  }
}
