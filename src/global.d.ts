import type {
  AppConfig,
  AppDraft,
  AppSettings,
  AppSnapshot,
  BackupEntry,
  ConsoleLine,
  FileEntry,
  PersistedState,
  ProcessMetrics,
  SystemMetrics,
} from "./shared/types";

export {};

declare global {
  interface Window {
    haven: {
      bootstrap(): Promise<AppSnapshot>;
      version(): Promise<string>;
      paths(): Promise<{ data: string; logs: string }>;
      apps: {
        save(draft: AppDraft): Promise<AppConfig>;
        delete(id: string): Promise<void>;
        reorder(ids: string[]): Promise<void>;
        pin(id: string): Promise<boolean>;
      };
      process: {
        start(id: string, password?: string): Promise<ProcessMetrics>;
        stop(id: string): Promise<ProcessMetrics>;
        restart(id: string, password?: string): Promise<ProcessMetrics>;
        command(id: string, command: string): Promise<void>;
        getConsole(id: string): Promise<ConsoleLine[]>;
        clearConsole(id: string): Promise<void>;
      };
      secrets: {
        set(id: string, token: string, password: string): Promise<void>;
        remove(id: string): Promise<void>;
      };
      files: {
        list(id: string, relativePath?: string): Promise<FileEntry[]>;
        read(id: string, relativePath: string): Promise<string>;
        write(id: string, relativePath: string, content: string): Promise<void>;
        mkdir(id: string, relativeParent: string, name: string): Promise<void>;
        rename(id: string, relativePath: string, name: string): Promise<void>;
        delete(id: string, relativePath: string): Promise<void>;
        search(id: string, query: string): Promise<FileEntry[]>;
        upload(id: string, relativePath: string): Promise<boolean>;
        download(id: string, relativePath: string): Promise<boolean>;
      };
      backups: {
        list(id: string): Promise<BackupEntry[]>;
        create(id: string): Promise<BackupEntry>;
        restore(id: string, backupId: string): Promise<void>;
        delete(id: string, backupId: string): Promise<void>;
      };
      dialog: {
        directory(): Promise<string | undefined>;
        file(extensions?: string[]): Promise<string | undefined>;
        background(): Promise<string | undefined>;
      };
      shell: {
        folder(path: string): Promise<void>;
        url(url: string): Promise<void>;
      };
      settings: {
        save(patch: Partial<AppSettings>): Promise<AppSettings>;
        export(): Promise<boolean>;
        import(): Promise<boolean>;
        reset(): Promise<void>;
      };
      activity: {
        clear(): Promise<void>;
      };
      events: {
        onState(listener: (state: PersistedState) => void): () => void;
        onMetrics(listener: (metrics: ProcessMetrics[]) => void): () => void;
        onSystem(listener: (metrics: SystemMetrics) => void): () => void;
        onConsole(listener: (line: ConsoleLine) => void): () => void;
      };
    };
  }
}
