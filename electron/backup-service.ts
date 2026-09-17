import { app } from "electron";
import { promises as fs } from "node:fs";
import path from "node:path";
import type { BackupEntry } from "../src/shared/types";
import type { StorageService } from "./storage";

export class BackupService {
  private readonly root = path.join(app.getPath("userData"), "backups");

  constructor(private readonly storage: StorageService) {}

  async list(appId: string): Promise<BackupEntry[]> {
    const directory = path.join(this.root, appId);
    const entries = await fs.readdir(directory, { withFileTypes: true }).catch(() => []);
    const backups = await Promise.all(
      entries
        .filter((entry) => entry.isDirectory())
        .map(async (entry): Promise<BackupEntry> => {
          const backupPath = path.join(directory, entry.name);
          const stat = await fs.stat(backupPath);
          return {
            id: entry.name,
            appId,
            createdAt: stat.birthtime.toISOString(),
            size: await this.directorySize(backupPath),
            path: backupPath,
          };
        }),
    );
    return backups.sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  }

  async create(appId: string) {
    const config = this.requireApp(appId);
    const source = path.resolve(config.directory);
    const sourceStat = await fs.stat(source).catch(() => undefined);
    if (!sourceStat?.isDirectory()) throw new Error("The application directory does not exist.");
    const id = new Date().toISOString().replaceAll(":", "-").replaceAll(".", "-");
    const destination = path.join(this.root, appId, id);
    if (destination.startsWith(`${source}${path.sep}`)) {
      throw new Error("Move the Process Manager data folder outside this application directory.");
    }
    await fs.mkdir(path.dirname(destination), { recursive: true });
    await fs.cp(source, destination, {
      recursive: true,
      preserveTimestamps: true,
      filter: (candidate) => !candidate.includes(`${path.sep}node_modules${path.sep}.cache`),
    });
    await this.enforceLimit(appId, config.advanced.maxBackups ?? 5);
    return (await this.list(appId)).find((backup) => backup.id === id);
  }

  async restore(appId: string, backupId: string) {
    const config = this.requireApp(appId);
    const destination = path.resolve(config.directory);
    if (destination === path.parse(destination).root) {
      throw new Error("Restoring into a drive root is not allowed.");
    }
    const source = this.resolveBackup(appId, backupId);
    const entries = await fs.readdir(destination);
    await Promise.all(entries.map((entry) => fs.rm(path.join(destination, entry), { recursive: true })));
    await fs.cp(source, destination, { recursive: true, preserveTimestamps: true });
  }

  async delete(appId: string, backupId: string) {
    await fs.rm(this.resolveBackup(appId, backupId), { recursive: true, force: false });
  }

  private requireApp(appId: string) {
    const config = this.storage.getApp(appId);
    if (!config?.directory) throw new Error("Choose an application directory first.");
    return config;
  }

  private resolveBackup(appId: string, backupId: string) {
    if (!/^[\w.-]+$/.test(backupId)) throw new Error("Invalid backup.");
    return path.join(this.root, appId, backupId);
  }

  private async enforceLimit(appId: string, maxCount: number) {
    const backups = await this.list(appId);
    await Promise.all(
      backups
        .slice(Math.max(1, maxCount))
        .map((backup) => fs.rm(backup.path, { recursive: true, force: true })),
    );
  }

  private async directorySize(directory: string): Promise<number> {
    const entries = await fs.readdir(directory, { withFileTypes: true });
    let total = 0;
    for (const entry of entries) {
      const fullPath = path.join(directory, entry.name);
      total += entry.isDirectory() ? await this.directorySize(fullPath) : (await fs.stat(fullPath)).size;
    }
    return total;
  }
}
