import { app, Notification } from "electron";
import { ChildProcessWithoutNullStreams, spawn } from "node:child_process";
import { promises as fs } from "node:fs";
import net from "node:net";
import path from "node:path";
import pidusage from "pidusage";
import crypto from "node:crypto";
import type {
  ActivityEvent,
  AppConfig,
  ConsoleLine,
  ProcessMetrics,
  ProcessStatus,
} from "../src/shared/types";
import type { StorageService } from "./storage";
import type { SecretVault } from "./security";

interface ManagedProcess {
  child?: ChildProcessWithoutNullStreams;
  status: ProcessStatus;
  startedAt?: number;
  exitCode?: number | null;
  intentionalStop: boolean;
  restartTimer?: NodeJS.Timeout;
  runtimeSecret?: string;
}

type Emit = (channel: string, payload: unknown) => void;

export class ProcessManager {
  private readonly processes = new Map<string, ManagedProcess>();
  private readonly consoleLines = new Map<string, ConsoleLine[]>();
  private metricsTimer?: NodeJS.Timeout;

  constructor(
    private readonly storage: StorageService,
    private readonly vault: SecretVault,
    private readonly emit: Emit,
  ) {}

  startMonitoring() {
    this.metricsTimer = setInterval(() => void this.broadcastMetrics(), 2_000);
  }

  dispose() {
    if (this.metricsTimer) clearInterval(this.metricsTimer);
    for (const managed of this.processes.values()) {
      if (managed.restartTimer) clearTimeout(managed.restartTimer);
    }
  }

  getConsole(appId: string) {
    return structuredClone(this.consoleLines.get(appId) ?? []);
  }

  async clearConsole(appId: string) {
    this.consoleLines.set(appId, []);
    const logPath = this.getLogPath(appId);
    await fs.mkdir(path.dirname(logPath), { recursive: true });
    await fs.writeFile(logPath, "", "utf8");
  }

  async start(appId: string, password?: string) {
    const config = this.requireApp(appId);
    let runtimeSecret: string | undefined;
    if (config.hasSecret) {
      if (!password) throw new Error("Enter the master password to unlock this bot token.");
      runtimeSecret = this.vault.decrypt(appId, password);
    }
    return this.spawnProcess(config, runtimeSecret);
  }

  private async spawnProcess(config: AppConfig, runtimeSecret?: string) {
    const existing = this.processes.get(config.id);
    if (existing?.status === "online" || existing?.status === "starting") {
      throw new Error(`${config.name} is already running.`);
    }
    if (!config.startCommand.trim()) throw new Error("Add a start command before starting this app.");
    if (!config.directory.trim()) throw new Error("Choose a working directory before starting this app.");
    const directory = path.resolve(config.directory);
    const directoryStats = await fs.stat(directory).catch(() => undefined);
    if (!directoryStats?.isDirectory()) throw new Error("The working directory does not exist.");

    const managed: ManagedProcess = {
      status: "starting",
      intentionalStop: false,
      runtimeSecret,
    };
    this.processes.set(config.id, managed);
    this.emitMetrics(config.id);
    this.writeLine(config.id, "system", `Starting ${config.name}…`);

    const child = spawn(config.startCommand, {
      cwd: directory,
      env: {
        ...process.env,
        ...Object.fromEntries(config.env.filter((item) => item.key).map((item) => [item.key, item.value])),
        ...(runtimeSecret ? { DISCORD_TOKEN: runtimeSecret } : {}),
        ...(config.port ? { PORT: String(config.port) } : {}),
      },
      shell: true,
      windowsHide: true,
      detached: process.platform !== "win32",
    });
    managed.child = child;
    managed.status = "online";
    managed.startedAt = Date.now();
    this.emitMetrics(config.id);
    await this.recordActivity(config, "success", "Application started", `${config.name} is now online.`);

    child.stdout.on("data", (data: Buffer) => this.writeOutput(config.id, "stdout", data));
    child.stderr.on("data", (data: Buffer) => this.writeOutput(config.id, "stderr", data));
    child.on("error", (error) => this.writeLine(config.id, "stderr", error.message));
    child.on("close", (code) => void this.handleExit(config.id, code));
    return this.getMetric(config);
  }

  async stop(appId: string) {
    const config = this.requireApp(appId);
    const managed = this.processes.get(appId);
    if (!managed?.child || managed.status === "offline") return this.getMetric(config);
    managed.intentionalStop = true;
    managed.status = "stopping";
    if (managed.restartTimer) clearTimeout(managed.restartTimer);
    this.emitMetrics(appId);
    this.writeLine(appId, "system", `Stopping ${config.name}…`);

    const stopCommand = config.stopCommand.trim();
    if (stopCommand && stopCommand.toUpperCase() !== "CTRL+C") {
      managed.child.stdin.write(`${stopCommand}\n`);
      await new Promise((resolve) => setTimeout(resolve, 1_500));
      if (managed.child.exitCode === null) await this.killTree(managed.child.pid);
    } else if (process.platform === "win32") {
      managed.child.kill("SIGINT");
      await new Promise((resolve) => setTimeout(resolve, 1_200));
      if (managed.child.exitCode === null) await this.killTree(managed.child.pid);
    } else {
      try {
        process.kill(-managed.child.pid, "SIGINT");
      } catch {
        managed.child.kill("SIGINT");
      }
      await new Promise((resolve) => setTimeout(resolve, 1_200));
      if (managed.child.exitCode === null) await this.killTree(managed.child.pid);
    }
    return this.getMetric(config);
  }

  async restart(appId: string, password?: string) {
    const previousSecret = this.processes.get(appId)?.runtimeSecret;
    await this.stop(appId);
    await this.waitUntilStopped(appId);
    const config = this.requireApp(appId);
    if (config.hasSecret && previousSecret) return this.spawnProcess(config, previousSecret);
    return this.start(appId, password);
  }

  sendCommand(appId: string, command: string) {
    const managed = this.processes.get(appId);
    if (!managed?.child || managed.status !== "online") {
      throw new Error("The application must be online before sending a command.");
    }
    managed.child.stdin.write(`${command}\n`);
    this.writeLine(appId, "system", `> ${command}`);
  }

  async stopAll() {
    await Promise.allSettled(
      [...this.processes.entries()]
        .filter(([, managed]) => managed.child && managed.status !== "offline")
        .map(([appId]) => this.stop(appId)),
    );
  }

  getMetrics() {
    return this.storage
      .getState()
      .apps.map((config) => this.getMetric(config))
      .sort((a, b) => a.appId.localeCompare(b.appId));
  }

  private async handleExit(appId: string, code: number | null) {
    const managed = this.processes.get(appId);
    const config = this.storage.getApp(appId);
    if (!managed || !config) return;
    const crashed = !managed.intentionalStop && code !== 0;
    managed.child = undefined;
    managed.exitCode = code;
    managed.status = crashed ? "crashed" : "offline";
    this.emitMetrics(appId);
    this.writeLine(
      appId,
      crashed ? "stderr" : "system",
      crashed ? `Process exited unexpectedly (code ${code ?? "unknown"}).` : "Process stopped.",
    );
    await this.recordActivity(
      config,
      crashed ? "error" : "info",
      crashed ? "Application crashed" : "Application stopped",
      crashed ? `${config.name} exited with code ${code ?? "unknown"}.` : `${config.name} is offline.`,
    );

    if (crashed && config.autoRestart) {
      const delay = Math.max(1, config.restartDelaySeconds) * 1_000;
      this.writeLine(appId, "system", `Auto-restarting in ${delay / 1_000}s…`);
      managed.restartTimer = setTimeout(() => {
        void this.spawnProcess(config, managed.runtimeSecret).catch((error: Error) => {
          this.writeLine(appId, "stderr", `Auto-restart failed: ${error.message}`);
        });
      }, delay);
    } else {
      managed.runtimeSecret = undefined;
    }
  }

  private async broadcastMetrics() {
    const configs = this.storage.getState().apps;
    const metrics = await Promise.all(configs.map((config) => this.getMetricWithUsage(config)));
    this.emit("process:metrics", metrics);
  }

  private emitMetrics(appId: string) {
    const config = this.storage.getApp(appId);
    if (config) this.emit("process:metrics", this.getMetrics());
  }

  private getMetric(config: AppConfig): ProcessMetrics {
    const managed = this.processes.get(config.id);
    return {
      appId: config.id,
      status: managed?.status ?? "offline",
      pid: managed?.child?.pid,
      cpu: 0,
      memoryBytes: 0,
      uptimeSeconds: managed?.startedAt ? Math.floor((Date.now() - managed.startedAt) / 1_000) : 0,
      portOpen: false,
      startedAt: managed?.startedAt ? new Date(managed.startedAt).toISOString() : undefined,
      exitCode: managed?.exitCode,
    };
  }

  private async getMetricWithUsage(config: AppConfig): Promise<ProcessMetrics> {
    const metric = this.getMetric(config);
    if (metric.pid && metric.status === "online") {
      try {
        const usage = await pidusage(metric.pid);
        metric.cpu = usage.cpu;
        metric.memoryBytes = usage.memory;
      } catch {
        // A process can exit between the status check and this sample.
      }
    }
    metric.portOpen = config.port ? await this.checkPort(config.port) : false;
    const managed = this.processes.get(config.id);
    if (
      managed?.status === "online" &&
      config.advanced.scheduledRestartHours &&
      metric.uptimeSeconds >= config.advanced.scheduledRestartHours * 3_600
    ) {
      if (!config.hasSecret || managed.runtimeSecret) void this.restart(config.id);
    }
    return metric;
  }

  private checkPort(port: number) {
    return new Promise<boolean>((resolve) => {
      const socket = net.createConnection({ host: "127.0.0.1", port });
      const finish = (open: boolean) => {
        socket.destroy();
        resolve(open);
      };
      socket.setTimeout(350);
      socket.once("connect", () => finish(true));
      socket.once("timeout", () => finish(false));
      socket.once("error", () => finish(false));
    });
  }

  private writeOutput(appId: string, stream: "stdout" | "stderr", chunk: Buffer) {
    chunk
      .toString("utf8")
      .split(/\r?\n/)
      .filter(Boolean)
      .forEach((line) => this.writeLine(appId, stream, line));
  }

  private writeLine(appId: string, stream: ConsoleLine["stream"], text: string) {
    const line: ConsoleLine = {
      id: crypto.randomUUID(),
      appId,
      stream,
      text,
      timestamp: new Date().toISOString(),
    };
    const lines = this.consoleLines.get(appId) ?? [];
    lines.push(line);
    this.consoleLines.set(appId, lines.slice(-1_000));
    this.emit("process:console", line);
    const logPath = this.getLogPath(appId);
    void fs
      .mkdir(path.dirname(logPath), { recursive: true })
      .then(() => fs.appendFile(logPath, `[${line.timestamp}] [${stream}] ${text}\n`, "utf8"));
  }

  private getLogPath(appId: string) {
    return path.join(app.getPath("userData"), "logs", `${appId}.log`);
  }

  private requireApp(id: string) {
    const config = this.storage.getApp(id);
    if (!config) throw new Error("Application not found.");
    return config;
  }

  private async recordActivity(
    config: AppConfig,
    level: ActivityEvent["level"],
    title: string,
    message: string,
  ) {
    await this.storage.addActivity({ appId: config.id, level, title, message });
    this.emit("state:changed", this.storage.getState());
    if (this.storage.getState().settings.notifications && Notification.isSupported()) {
      new Notification({ title, body: message }).show();
    }
  }

  private killTree(pid: number) {
    return new Promise<void>((resolve) => {
      if (process.platform === "win32") {
        const killer = spawn("taskkill", ["/pid", String(pid), "/T", "/F"], { windowsHide: true });
        killer.once("close", () => resolve());
        killer.once("error", () => resolve());
      } else {
        try {
          process.kill(-pid, "SIGKILL");
        } catch {
          try {
            process.kill(pid, "SIGKILL");
          } catch {
            // Process already exited.
          }
        }
        resolve();
      }
    });
  }

  private async waitUntilStopped(appId: string) {
    const started = Date.now();
    while (this.processes.get(appId)?.child && Date.now() - started < 5_000) {
      await new Promise((resolve) => setTimeout(resolve, 100));
    }
  }
}
