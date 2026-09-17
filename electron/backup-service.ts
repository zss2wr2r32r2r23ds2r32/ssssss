import { app } from "electron";
import { promises as fs } from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import type { BackupEntry } from "../src/shared/types";
import type { StorageService } from "./storage";

export class BackupService {
  private readonly root = path.join(app.getPath("userData"), "backups");

  constructor(private readonly storage: StorageService) {}

  async list(appId: string): Promise<BackupEntry[]> {
    this.validateAppId(appId);
    const directory = path.join(this.root, appId);
    const entries = await fs.readdir(directory, { withFileTypes: true }).catch(() => []);
    const backups = await Promise.all(
      entries
        .filter((entry) => entry.isDirectory() && this.isBackupId(entry.name))
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
    const source = await fs.realpath(path.resolve(config.directory));
    const sourceStat = await fs.stat(source).catch(() => undefined);
    if (!sourceStat?.isDirectory()) throw new Error("The application directory does not exist.");
    const id = new Date().toISOString().replaceAll(":", "-").replaceAll(".", "-");
    const destination = path.join(this.root, appId, id);
    const staging = path.join(this.root, appId, `.partial-${id}`);
    if (destination.startsWith(`${source}${path.sep}`)) {
      throw new Error("Move the Process Manager data folder outside this application directory.");
    }
    await fs.mkdir(path.dirname(destination), { recursive: true });
    try {
      await fs.cp(source, staging, {
        recursive: true,
        preserveTimestamps: true,
        filter: async (candidate) =>
          !candidate.includes(`${path.sep}node_modules${path.sep}.cache`) &&
          !(await fs.lstat(candidate)).isSymbolicLink(),
      });
      await fs.rename(staging, destination);
    } catch (error) {
      await fs.rm(staging, { recursive: true, force: true });
      throw error;
    }
    await this.enforceLimit(appId, config.advanced.maxBackups ?? 5);
    return (await this.list(appId)).find((backup) => backup.id === id);
  }

  async restore(appId: string, backupId: string) {
    const config = this.requireApp(appId);
    const destination = path.resolve(config.directory);
    if (destination === path.parse(destination).root) {
      throw new Error("Restoring into a drive root is not allowed.");
    }
    const source = await this.resolveBackup(appId, backupId);
    const destinationStat = await fs.stat(destination).catch(() => undefined);
    if (!destinationStat?.isDirectory()) throw new Error("The application directory does not exist.");
    const rollback = path.join(
      path.dirname(destination),
      `${path.basename(destination)}.haven-rollback-${crypto.randomUUID()}`,
    );
    await fs.rename(destination, rollback);
    try {
      await fs.mkdir(destination, { recursive: false });
      await fs.cp(source, destination, { recursive: true, preserveTimestamps: true });
      await fs.rm(rollback, { recursive: true, force: true });
    } catch (error) {
      await fs.rm(destination, { recursive: true, force: true });
      await fs.rename(rollback, destination);
      throw error;
    }
  }

  async delete(appId: string, backupId: string) {
    await fs.rm(await this.resolveBackup(appId, backupId), { recursive: true, force: false });
  }

  private requireApp(appId: string) {
    const config = this.storage.getApp(appId);
    if (!config?.directory) throw new Error("Choose an application directory first.");
    return config;
  }

  private async resolveBackup(appId: string, backupId: string) {
    this.validateAppId(appId);
    if (!this.isBackupId(backupId)) throw new Error("Invalid backup.");
    const appRoot = path.resolve(this.root, appId);
    const target = await fs.realpath(path.join(appRoot, backupId));
    if (!target.startsWith(`${appRoot}${path.sep}`)) throw new Error("Invalid backup path.");
    return target;
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
      if (entry.isSymbolicLink()) continue;
      const fullPath = path.join(directory, entry.name);
      total += entry.isDirectory() ? await this.directorySize(fullPath) : (await fs.stat(fullPath)).size;
    }
    return total;
  }

  private validateAppId(appId: string) {
    if (!/^[0-9a-f]{8}-[0-9a-f-]{27}$/i.test(appId)) throw new Error("Invalid application ID.");
  }

  private isBackupId(backupId: string) {
    return /^[0-9TZ-]{20,40}$/.test(backupId);
  }
}
