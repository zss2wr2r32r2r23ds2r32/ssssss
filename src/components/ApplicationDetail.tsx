import {
  Activity,
  ArrowLeft,
  Bot,
  Box,
  ChevronRight,
  CirclePlay,
  Clock3,
  Copy,
  Download,
  Edit3,
  ExternalLink,
  File,
  FileCode2,
  Folder,
  FolderOpen,
  Gauge,
  Globe2,
  HardDrive,
  KeyRound,
  MemoryStick,
  MoreVertical,
  PackagePlus,
  Play,
  Plus,
  RefreshCw,
  RotateCcw,
  Save,
  Search,
  Server,
  ShieldCheck,
  Square,
  TerminalSquare,
  Trash2,
  Upload,
  Users,
  Wrench,
  X,
} from "lucide-react";
import type { ComponentType } from "react";
import { useEffect, useMemo, useRef, useState } from "react";
import type {
  AppConfig,
  BackupEntry,
  ConsoleLine,
  FileEntry,
  ProcessMetrics,
  RuntimeCheck,
} from "../shared/types";
import { appTypeLabel, formatBytes, formatUptime, friendlyError } from "../lib/format";
import { ConfirmModal, Modal } from "./Modal";

type DetailTab = "overview" | "console" | "files" | "backups" | "configuration";

export function ApplicationDetail({
  app,
  metric,
  runtimes,
  liveLines,
  onBack,
  onEdit,
  onDeleted,
  onStart,
  onStop,
  onRestart,
  onError,
  onSuccess,
}: {
  app: AppConfig;
  metric?: ProcessMetrics;
  runtimes: RuntimeCheck[];
  liveLines: ConsoleLine[];
  onBack: () => void;
  onEdit: () => void;
  onDeleted: () => void;
  onStart: (app: AppConfig) => void;
  onStop: (app: AppConfig) => void;
  onRestart: (app: AppConfig) => void;
  onError: (error: unknown) => void;
  onSuccess: (message: string) => void;
}) {
  const [tab, setTab] = useState<DetailTab>("overview");
  const [confirm, setConfirm] = useState<{ title: string; message: string; label: string; action: () => Promise<void> }>();
  const status = metric?.status ?? "offline";
  const online = status === "online";

  const runConfirmed = async () => {
    const action = confirm?.action;
    setConfirm(undefined);
    if (!action) return;
    try {
      await action();
    } catch (error) {
      onError(error);
    }
  };

  return (
    <div className="page detail-page">
      <button className="text-button detail-back" onClick={onBack}>
        <ArrowLeft size={16} /> Applications
      </button>
      <section className="detail-hero glass">
        <div className="detail-identity">
          <span className={`detail-app-icon type-${app.type}`}>{app.icon}</span>
          <div>
            <div className="detail-title-row">
              <h1>{app.name}</h1>
              <span className={`status-chip ${status}`}><i />{status}</span>
            </div>
            <p>{appTypeLabel(app.type)} · {app.directory || "Working directory not set"}</p>
          </div>
        </div>
        <div className="detail-actions">
          {app.type === "website" && app.port && (
            <button className="button subtle" onClick={() => window.haven.shell.url(`http://localhost:${app.port}`)}>
              <ExternalLink size={16} /> Open website
            </button>
          )}
          {online ? (
            <button className="button danger" onClick={() => onStop(app)}><Square size={15} fill="currentColor" /> Stop</button>
          ) : (
            <button className="button start" onClick={() => onStart(app)}><Play size={16} fill="currentColor" /> Start</button>
          )}
          <button className="button restart" disabled={!online} onClick={() => onRestart(app)}>
            <RefreshCw size={16} /> Restart
          </button>
          <button className="icon-button detail-menu" onClick={onEdit} aria-label="Edit configuration">
            <MoreVertical size={19} />
          </button>
        </div>
      </section>

      <nav className="detail-tabs glass" aria-label="Application sections">
        {([
          { id: "overview", icon: Activity, label: "Overview" },
          { id: "console", icon: TerminalSquare, label: "Console" },
          { id: "files", icon: FolderOpen, label: "Files" },
          { id: "backups", icon: HardDrive, label: "Backups" },
          { id: "configuration", icon: Wrench, label: "Configuration" },
        ] satisfies { id: DetailTab; icon: ComponentType<{ size?: number }>; label: string }[]).map(({ id, icon: Icon, label }) => (
          <button key={id} className={tab === id ? "active" : ""} onClick={() => setTab(id)}>
            <Icon size={17} /> {label}
          </button>
        ))}
      </nav>

      {tab === "overview" && (
        <Overview app={app} metric={metric} lines={liveLines} runtimes={runtimes} onEdit={onEdit} />
      )}
      {tab === "console" && (
        <ConsoleView
          app={app}
          online={online}
          liveLines={liveLines}
          onError={onError}
          onSuccess={onSuccess}
        />
      )}
      {tab === "files" && (
        <FileManager
          app={app}
          onError={onError}
          onSuccess={onSuccess}
          confirm={(details) => setConfirm(details)}
        />
      )}
      {tab === "backups" && (
        <Backups
          app={app}
          online={online}
          onError={onError}
          onSuccess={onSuccess}
          confirm={(details) => setConfirm(details)}
        />
      )}
      {tab === "configuration" && (
        <Configuration
          app={app}
          onEdit={onEdit}
          onDelete={() =>
            setConfirm({
              title: `Delete ${app.name}?`,
              message: "This removes the application configuration and encrypted token. Files in its working directory are not deleted.",
              label: "Delete application",
              action: async () => {
                await window.haven.apps.delete(app.id);
                onDeleted();
              },
            })
          }
          onError={onError}
          onSuccess={onSuccess}
        />
      )}

      <ConfirmModal
        open={Boolean(confirm)}
        title={confirm?.title ?? ""}
        message={confirm?.message ?? ""}
        confirmLabel={confirm?.label ?? "Confirm"}
        danger
        onCancel={() => setConfirm(undefined)}
        onConfirm={runConfirmed}
      />
    </div>
  );
}

function Overview({
  app,
  metric,
  lines,
  runtimes,
  onEdit,
}: {
  app: AppConfig;
  metric?: ProcessMetrics;
  lines: ConsoleLine[];
  runtimes: RuntimeCheck[];
  onEdit: () => void;
}) {
  const minecraft = useMemo(() => {
    const text = lines.map((line) => line.text).join("\n");
    const version = text.match(/Starting minecraft server version\s+(.+)/i)?.[1];
    const players = text.match(/There are\s+(\d+)\s+of a max of\s+(\d+)\s+players/i);
    const tps = [...text.matchAll(/TPS[^:]*:\s*([\d.]+)/gi)].at(-1)?.[1];
    return { version, players: players ? `${players[1]} / ${players[2]}` : undefined, tps };
  }, [lines]);
  const requiredRuntime =
    app.type === "minecraft" ? "java" : app.type === "discord" || app.type === "website"
      ? app.advanced.runtime ?? "node"
      : undefined;
  const runtime = runtimes.find((item) => item.runtime === requiredRuntime);

  return (
    <div className="detail-content">
      <section className="detail-metrics">
        <MetricCard icon={CpuIcon} label="CPU usage" value={`${metric?.cpu.toFixed(1) ?? "0.0"}%`} />
        <MetricCard icon={MemoryStick} label="Memory" value={formatBytes(metric?.memoryBytes ?? 0)} />
        <MetricCard icon={Clock3} label="Uptime" value={formatUptime(metric?.uptimeSeconds ?? 0)} />
        <MetricCard icon={Server} label="Process ID" value={metric?.pid ? String(metric.pid) : "—"} />
      </section>

      <div className="overview-columns">
        <section className="panel glass">
          <div className="panel-heading"><div><h3>Service details</h3><p>Live process and network information</p></div></div>
          <dl className="detail-list">
            <div><dt>Status</dt><dd><span className={`status-dot ${metric?.status === "online" ? "online" : ""}`} />{metric?.status ?? "offline"}</dd></div>
            <div><dt>Port</dt><dd>{app.port ?? "Not configured"} {app.port && <small className={metric?.portOpen ? "text-green" : ""}>{metric?.portOpen ? "Open" : "Closed"}</small>}</dd></div>
            <div><dt>Local address</dt><dd>{app.port ? `127.0.0.1:${app.port}` : "—"} <Copy size={14} /></dd></div>
            <div><dt>Runtime</dt><dd>{runtime?.installed ? runtime.version : requiredRuntime ? `${requiredRuntime} not detected` : "Command shell"}</dd></div>
            <div><dt>Auto restart</dt><dd>{app.autoRestart ? `Enabled · ${app.restartDelaySeconds}s delay` : "Disabled"}</dd></div>
            <div><dt>Working folder</dt><dd className="truncate">{app.directory || "Not set"}</dd></div>
          </dl>
        </section>

        <section className="panel glass">
          <div className="panel-heading"><div><h3>{appTypeLabel(app.type)} controls</h3><p>Type-aware setup at a glance</p></div><button className="text-button" onClick={onEdit}>Edit</button></div>
          {app.type === "minecraft" && (
            <div className="capability-grid">
              <Capability icon={Users} label="Players" value={minecraft.players ?? "Waiting for query"} />
              <Capability icon={Box} label="Version" value={minecraft.version ?? "Detected on start"} />
              <Capability icon={Gauge} label="TPS / MSPT" value={minecraft.tps ?? "Send /tps"} />
              <Capability icon={MemoryStick} label="RAM limit" value={`${(app.ramLimitMb ?? 2048) / 1024} GB`} />
              <Capability icon={PackagePlus} label="Plugins" value="Manage in Files" />
              <Capability icon={HardDrive} label="Worlds" value="Protected by backups" />
            </div>
          )}
          {app.type === "discord" && (
            <div className="capability-grid">
              <Capability icon={ShieldCheck} label="Bot token" value={app.hasSecret ? "Encrypted" : "Not set"} />
              <Capability icon={Bot} label="Runtime" value={app.advanced.runtime ?? "node"} />
              <Capability icon={FileCode2} label="Startup" value={app.advanced.startupFile ?? app.startCommand} />
              <Capability icon={RefreshCw} label="Crash recovery" value={app.autoRestart ? "Enabled" : "Disabled"} />
            </div>
          )}
          {app.type === "website" && (
            <div className="capability-grid">
              <Capability icon={Globe2} label="Local URL" value={app.port ? `localhost:${app.port}` : "No port"} />
              <Capability icon={FileCode2} label="Runtime" value={app.advanced.runtime ?? "node"} />
              <Capability icon={RefreshCw} label="Crash recovery" value={app.autoRestart ? "Enabled" : "Disabled"} />
              <Capability icon={Folder} label="Project files" value="Manage in Files" />
            </div>
          )}
          {app.type === "custom" && (
            <div className="custom-command-preview">
              <TerminalSquare size={20} />
              <div><span>Start command</span><code>{app.startCommand || "Not configured"}</code></div>
            </div>
          )}
        </section>
      </div>
    </div>
  );
}

function CpuIcon({ size = 20 }: { size?: number }) {
  return <Activity size={size} />;
}

function MetricCard({ icon: Icon, label, value }: { icon: ComponentType<{ size?: number }>; label: string; value: string }) {
  return <div className="metric-card glass"><span><Icon size={19} /></span><div><small>{label}</small><strong>{value}</strong></div></div>;
}

function Capability({ icon: Icon, label, value }: { icon: ComponentType<{ size?: number }>; label: string; value: string }) {
  return <div className="capability"><span><Icon size={18} /></span><div><small>{label}</small><strong title={value}>{value}</strong></div></div>;
}

function ConsoleView({
  app,
  online,
  liveLines,
  onError,
  onSuccess,
}: {
  app: AppConfig;
  online: boolean;
  liveLines: ConsoleLine[];
  onError: (error: unknown) => void;
  onSuccess: (message: string) => void;
}) {
  const [lines, setLines] = useState<ConsoleLine[]>(liveLines);
  const [command, setCommand] = useState("");
  const output = useRef<HTMLDivElement>(null);
  useEffect(() => { void window.haven.process.getConsole(app.id).then(setLines).catch(onError); }, [app.id, onError]);
  useEffect(() => setLines(liveLines), [liveLines]);
  useEffect(() => {
    const element = output.current;
    if (element) element.scrollTop = element.scrollHeight;
  }, [lines]);

  const send = async () => {
    if (!command.trim()) return;
    try {
      await window.haven.process.command(app.id, command.trim());
      setCommand("");
    } catch (error) { onError(error); }
  };
  return (
    <div className="detail-content">
      <section className="console-panel glass">
        <header className="console-toolbar">
          <div><span className={`status-dot ${online ? "online" : ""}`} /><strong>Live console</strong><small>{online ? "Connected" : "Process offline"}</small></div>
          <button className="button subtle small" onClick={async () => {
            await window.haven.process.clearConsole(app.id);
            setLines([]);
            onSuccess("Console and log file cleared.");
          }}>Clear output</button>
        </header>
        <div className="console-output" ref={output}>
          {lines.length ? lines.map((line) => (
            <div key={line.id} className={`console-line ${line.stream}`}>
              <time>{new Date(line.timestamp).toLocaleTimeString([], { hour12: false })}</time>
              <span>{line.text}</span>
            </div>
          )) : <div className="console-empty"><TerminalSquare size={30} /><span>Console output will appear here.</span></div>}
        </div>
        <form className="console-command" onSubmit={(event) => { event.preventDefault(); void send(); }}>
          <span>&gt;</span>
          <input value={command} onChange={(event) => setCommand(event.target.value)} disabled={!online} placeholder={online ? app.type === "minecraft" ? "say Hello, world!" : "Send input to process…" : "Start the application to send commands"} />
          <button className="button primary small" disabled={!online || !command.trim()}>Send</button>
        </form>
      </section>
    </div>
  );
}

type Confirmation = { title: string; message: string; label: string; action: () => Promise<void> };

function FileManager({
  app,
  onError,
  onSuccess,
  confirm,
}: {
  app: AppConfig;
  onError: (error: unknown) => void;
  onSuccess: (message: string) => void;
  confirm: (details: Confirmation) => void;
}) {
  const [path, setPath] = useState("");
  const [entries, setEntries] = useState<FileEntry[]>([]);
  const [selected, setSelected] = useState<FileEntry>();
  const [content, setContent] = useState("");
  const [savedContent, setSavedContent] = useState("");
  const [query, setQuery] = useState("");
  const [busy, setBusy] = useState(false);

  const refresh = async () => {
    if (!app.directory) return;
    try { setEntries(await window.haven.files.list(app.id, path)); } catch (error) { onError(error); }
  };
  useEffect(() => { setSelected(undefined); void refresh(); }, [app.id, path]);

  const open = async (entry: FileEntry) => {
    if (entry.type === "directory") { setPath(entry.path); return; }
    try {
      const text = await window.haven.files.read(app.id, entry.path);
      setSelected(entry); setContent(text); setSavedContent(text);
    } catch (error) { onError(error); }
  };

  const search = async () => {
    if (!query.trim()) { await refresh(); return; }
    try { setEntries(await window.haven.files.search(app.id, query)); } catch (error) { onError(error); }
  };

  return (
    <div className="detail-content file-layout">
      <section className="file-browser panel glass">
        <header className="file-toolbar">
          <div className="file-search">
            <Search size={16} />
            <input value={query} onChange={(event) => setQuery(event.target.value)} onKeyDown={(event) => event.key === "Enter" && void search()} placeholder="Search files…" />
          </div>
          <button className="icon-button" title="Upload files" onClick={async () => { if (await window.haven.files.upload(app.id, path)) { await refresh(); onSuccess("Files added."); } }}><Upload size={17} /></button>
          <button className="icon-button" title="New folder" onClick={async () => {
            const name = window.prompt("Folder name");
            if (!name) return;
            try { await window.haven.files.mkdir(app.id, path, name); await refresh(); } catch (error) { onError(error); }
          }}><Plus size={17} /></button>
          <button className="icon-button" title="Open in Explorer" onClick={() => window.haven.shell.folder(app.directory)}><FolderOpen size={17} /></button>
        </header>
        <div className="breadcrumbs">
          <button onClick={() => setPath("")}>{app.name}</button>
          {path.split("/").filter(Boolean).map((part, index, parts) => (
            <span key={`${part}-${index}`}><ChevronRight size={13} /><button onClick={() => setPath(parts.slice(0, index + 1).join("/"))}>{part}</button></span>
          ))}
        </div>
        <div className="file-table">
          <div className="file-table-head"><span>Name</span><span>Size</span><span>Modified</span><span /></div>
          {path && !query && <button className="file-row parent-row" onDoubleClick={() => setPath(path.split("/").slice(0, -1).join("/"))}><span><Folder size={17} />..</span><span>—</span><span>—</span><span /></button>}
          {entries.map((entry) => (
            <button key={entry.path} className={`file-row ${selected?.path === entry.path ? "selected" : ""}`} onDoubleClick={() => void open(entry)} onClick={() => setSelected(entry)}>
              <span>{entry.type === "directory" ? <Folder size={17} /> : <File size={17} />}{entry.name}</span>
              <span>{entry.type === "file" ? formatBytes(entry.size) : "—"}</span>
              <span>{new Date(entry.modifiedAt).toLocaleDateString()}</span>
              <span className="row-actions">
                {entry.type === "file" && <i role="button" title="Download" onClick={(event) => { event.stopPropagation(); void window.haven.files.download(app.id, entry.path); }}><Download size={15} /></i>}
                <i role="button" title="Rename" onClick={(event) => {
                  event.stopPropagation();
                  const name = window.prompt("New name", entry.name);
                  if (name && name !== entry.name) void window.haven.files.rename(app.id, entry.path, name).then(refresh).catch(onError);
                }}><Edit3 size={14} /></i>
                <i role="button" title="Delete" onClick={(event) => {
                  event.stopPropagation();
                  confirm({ title: `Delete ${entry.name}?`, message: "This cannot be undone.", label: "Delete", action: async () => { await window.haven.files.delete(app.id, entry.path); if (selected?.path === entry.path) setSelected(undefined); await refresh(); } });
                }}><Trash2 size={14} /></i>
              </span>
            </button>
          ))}
          {!entries.length && <div className="empty-inline file-empty"><Folder size={24} /><span>{app.directory ? "This folder is empty." : "Configure a working directory first."}</span></div>}
        </div>
      </section>

      <section className="editor-panel panel glass">
        {selected?.type === "file" ? (
          <>
            <header><div><FileCode2 size={17} /><strong>{selected.name}</strong><small>{content !== savedContent ? "Unsaved changes" : selected.path}</small></div><button className="icon-button" onClick={() => setSelected(undefined)}><X size={16} /></button></header>
            <textarea spellCheck={false} value={content} onChange={(event) => setContent(event.target.value)} />
            <footer><span>{content.split("\n").length} lines · {formatBytes(new Blob([content]).size)}</span><button className="button primary small" disabled={content === savedContent || busy} onClick={async () => {
              setBusy(true);
              try { await window.haven.files.write(app.id, selected.path, content); setSavedContent(content); onSuccess("File saved."); } catch (error) { onError(error); } finally { setBusy(false); }
            }}><Save size={15} /> Save file</button></footer>
          </>
        ) : (
          <div className="editor-empty"><FileCode2 size={32} /><strong>Text editor</strong><span>Double-click a yml, json, properties, js, py, or other text file to edit it safely.</span></div>
        )}
      </section>
    </div>
  );
}

function Backups({
  app,
  online,
  onError,
  onSuccess,
  confirm,
}: {
  app: AppConfig;
  online: boolean;
  onError: (error: unknown) => void;
  onSuccess: (message: string) => void;
  confirm: (details: Confirmation) => void;
}) {
  const [entries, setEntries] = useState<BackupEntry[]>([]);
  const [busy, setBusy] = useState(false);
  const refresh = () => window.haven.backups.list(app.id).then(setEntries).catch(onError);
  useEffect(() => { void refresh(); }, [app.id]);
  return (
    <div className="detail-content">
      <section className="panel glass">
        <div className="panel-heading">
          <div><h3>Application backups</h3><p>Full local snapshots · newest first · maximum {app.advanced.maxBackups ?? 5}</p></div>
          <button className="button primary" disabled={busy || !app.directory || online} onClick={async () => {
            setBusy(true);
            try { await window.haven.backups.create(app.id); await refresh(); onSuccess("Backup created."); } catch (error) { onError(error); } finally { setBusy(false); }
          }}><Plus size={16} />{busy ? "Creating…" : "Create backup"}</button>
        </div>
        {online && <div className="backup-warning"><Activity size={17} />Stop this application before creating or restoring a consistent snapshot.</div>}
        <div className="backup-list">
          {entries.map((entry) => (
            <article key={entry.id}>
              <span className="backup-icon"><HardDrive size={20} /></span>
              <div><strong>{new Date(entry.createdAt).toLocaleString()}</strong><small>{formatBytes(entry.size)} · Entire application directory</small></div>
              <button className="button subtle small" disabled={online} onClick={() => confirm({ title: "Restore this backup?", message: "Current files in the application directory will be replaced. The application must be offline.", label: "Restore backup", action: async () => { await window.haven.backups.restore(app.id, entry.id); onSuccess("Backup restored."); } })}><RotateCcw size={15} />Restore</button>
              <button className="icon-button danger-icon" onClick={() => confirm({ title: "Delete this backup?", message: `${formatBytes(entry.size)} will be permanently removed.`, label: "Delete backup", action: async () => { await window.haven.backups.delete(app.id, entry.id); await refresh(); } })}><Trash2 size={16} /></button>
            </article>
          ))}
          {!entries.length && <div className="empty-state compact"><HardDrive size={28} /><h3>No backups yet</h3><p>Create a local snapshot before risky changes.</p></div>}
        </div>
      </section>
    </div>
  );
}

function Configuration({
  app,
  onEdit,
  onDelete,
  onError,
  onSuccess,
}: {
  app: AppConfig;
  onEdit: () => void;
  onDelete: () => void;
  onError: (error: unknown) => void;
  onSuccess: (message: string) => void;
}) {
  const [secretOpen, setSecretOpen] = useState(false);
  return (
    <div className="detail-content configuration-grid">
      <section className="panel glass">
        <div className="panel-heading"><div><h3>Process configuration</h3><p>How Haven launches and manages this process</p></div><button className="button subtle small" onClick={onEdit}><Edit3 size={15} />Edit</button></div>
        <dl className="detail-list config-list">
          <div><dt>Type</dt><dd>{appTypeLabel(app.type)}</dd></div>
          <div><dt>Directory</dt><dd className="truncate">{app.directory || "Not set"}</dd></div>
          <div><dt>Start command</dt><dd><code>{app.startCommand || "Not set"}</code></dd></div>
          <div><dt>Stop command</dt><dd><code>{app.stopCommand || "CTRL+C"}</code></dd></div>
          <div><dt>Port</dt><dd>{app.port ?? "Not set"}</dd></div>
          <div><dt>Auto-start</dt><dd>{app.autoStart ? "Enabled" : "Disabled"}</dd></div>
          <div><dt>Crash restart</dt><dd>{app.autoRestart ? `After ${app.restartDelaySeconds}s` : "Disabled"}</dd></div>
          <div><dt>Environment</dt><dd>{app.env.length} variable{app.env.length === 1 ? "" : "s"}</dd></div>
        </dl>
      </section>
      <div className="configuration-stack">
        {app.type === "discord" && (
          <section className="panel glass token-panel">
            <span className="large-security-icon"><KeyRound size={23} /></span>
            <div><h3>Protected bot token</h3><p>{app.hasSecret ? "An encrypted token is stored locally. It can be replaced or removed, but never revealed." : "Protect a token with a master password before injecting it into the bot process."}</p></div>
            <button className="button subtle" onClick={() => setSecretOpen(true)}>{app.hasSecret ? "Replace token" : "Add token"}</button>
            {app.hasSecret && <button className="text-button danger-text" onClick={async () => { try { await window.haven.secrets.remove(app.id); onSuccess("Encrypted token removed."); } catch (error) { onError(error); } }}>Remove token</button>}
          </section>
        )}
        <section className="panel glass danger-zone">
          <div><h3>Remove application</h3><p>Removes the configuration from Haven. Project files remain untouched.</p></div>
          <button className="button danger-outline" onClick={onDelete}><Trash2 size={16} />Delete application</button>
        </section>
      </div>
      <SecretModal app={app} open={secretOpen} onClose={() => setSecretOpen(false)} onError={onError} onSuccess={onSuccess} />
    </div>
  );
}

function SecretModal({
  app,
  open,
  onClose,
  onError,
  onSuccess,
}: {
  app: AppConfig;
  open: boolean;
  onClose: () => void;
  onError: (error: unknown) => void;
  onSuccess: (message: string) => void;
}) {
  const [token, setToken] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [saving, setSaving] = useState(false);
  useEffect(() => { if (open) { setToken(""); setPassword(""); setConfirmPassword(""); } }, [open]);
  const save = async () => {
    if (password !== confirmPassword) { onError(new Error("Master passwords do not match.")); return; }
    setSaving(true);
    try {
      await window.haven.secrets.set(app.id, token, password);
      setToken(""); setPassword(""); setConfirmPassword("");
      onClose(); onSuccess("Bot token encrypted. Haven will never display it.");
    } catch (error) { onError(error); } finally { setSaving(false); }
  };
  return (
    <Modal open={open} onClose={onClose} title="Encrypt bot token" subtitle="The token is write-only and protected with AES-256-GCM.">
      <div className="secret-form">
        <label><span>Discord bot token</span><input type="password" autoComplete="off" value={token} onChange={(event) => setToken(event.target.value)} placeholder="Token is never displayed" /></label>
        <label><span>Master password</span><input type="password" autoComplete="new-password" value={password} onChange={(event) => setPassword(event.target.value)} placeholder="At least 8 characters" /></label>
        <label><span>Confirm master password</span><input type="password" autoComplete="new-password" value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} /></label>
        <div className="security-note"><ShieldCheck size={19} /><span>Haven does not store your master password. You’ll enter it when starting the bot after reopening the app.</span></div>
      </div>
      <div className="modal-actions"><button className="button subtle" onClick={onClose}>Cancel</button><button className="button primary" disabled={!token || password.length < 8 || saving} onClick={save}>{saving ? "Encrypting…" : "Encrypt token"}</button></div>
    </Modal>
  );
}
