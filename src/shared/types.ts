export type ApplicationType = "minecraft" | "discord" | "website" | "custom";
export type ProcessStatus = "offline" | "starting" | "online" | "stopping" | "crashed";

export interface EnvVariable {
  key: string;
  value: string;
}

export interface AppConfig {
  id: string;
  name: string;
  type: ApplicationType;
  icon: string;
  directory: string;
  startCommand: string;
  stopCommand: string;
  port?: number;
  ramLimitMb?: number;
  autoStart: boolean;
  autoRestart: boolean;
  restartDelaySeconds: number;
  pinned: boolean;
  order: number;
  env: EnvVariable[];
  hasSecret?: boolean;
  createdAt: string;
  advanced: {
    scheduledRestartHours?: number;
    javaPath?: string;
    jarFile?: string;
    javaVersion?: string;
    runtime?: string;
    startupFile?: string;
    serverIp?: string;
    maxBackups?: number;
  };
}

export interface AppDraft
  extends Omit<AppConfig, "id" | "createdAt" | "order" | "hasSecret"> {
  id?: string;
}

export interface ProcessMetrics {
  appId: string;
  status: ProcessStatus;
  pid?: number;
  cpu: number;
  memoryBytes: number;
  uptimeSeconds: number;
  portOpen: boolean;
  startedAt?: string;
  exitCode?: number | null;
}

export interface SystemMetrics {
  cpu: number;
  memoryUsed: number;
  memoryTotal: number;
  diskUsed: number;
  diskTotal: number;
  networkRx: number;
  networkTx: number;
  timestamp: number;
}

export interface ConsoleLine {
  id: string;
  appId: string;
  stream: "stdout" | "stderr" | "system";
  text: string;
  timestamp: string;
}

export interface ActivityEvent {
  id: string;
  appId?: string;
  level: "info" | "success" | "warning" | "error";
  title: string;
  message: string;
  timestamp: string;
}

export interface AppSettings {
  theme: "dark" | "light";
  accent: string;
  customBackground?: string;
  startWithWindows: boolean;
  minimizeToTray: boolean;
  confirmBeforeStop: boolean;
  confirmBeforeDelete: boolean;
  notifications: boolean;
  debugMode: boolean;
}

export interface PersistedState {
  apps: AppConfig[];
  settings: AppSettings;
  activity: ActivityEvent[];
}

export interface RuntimeCheck {
  runtime: "java" | "node" | "python";
  installed: boolean;
  version?: string;
  installUrl: string;
}

export interface FileEntry {
  name: string;
  path: string;
  type: "file" | "directory";
  size: number;
  modifiedAt: string;
  children?: FileEntry[];
}

export interface BackupEntry {
  id: string;
  appId: string;
  createdAt: string;
  size: number;
  path: string;
}

export interface AppSnapshot {
  state: PersistedState;
  metrics: ProcessMetrics[];
  system: SystemMetrics;
  runtimes: RuntimeCheck[];
}

export type Page = "dashboard" | "monitoring" | "activity" | "settings";
