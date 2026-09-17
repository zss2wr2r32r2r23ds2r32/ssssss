import {
  Bot,
  Box,
  CirclePlay,
  Code2,
  Cpu,
  Globe2,
  GripVertical,
  MemoryStick,
  MoreHorizontal,
  Pin,
  Plus,
  RotateCw,
  Server,
  Square,
  Star,
  TerminalSquare,
  Users,
} from "lucide-react";
import { useMemo, useState } from "react";
import type {
  AppConfig,
  ApplicationType,
  ProcessMetrics,
  RuntimeCheck,
  SystemMetrics,
} from "../shared/types";
import { appTypeLabel, formatBytes, formatUptime } from "../lib/format";

const typeIcons: Record<ApplicationType, typeof Server> = {
  minecraft: Box,
  discord: Bot,
  website: Globe2,
  custom: TerminalSquare,
};

const typeColors: Record<ApplicationType, string> = {
  minecraft: "#73db8b",
  discord: "#8995ff",
  website: "#56c5f3",
  custom: "#f19bd2",
};

export function Dashboard({
  apps,
  metrics,
  system,
  runtimes,
  query,
  consolePreview,
  onAdd,
  onOpen,
  onStart,
  onStop,
  onRestart,
  onPin,
  onReorder,
}: {
  apps: AppConfig[];
  metrics: ProcessMetrics[];
  system: SystemMetrics;
  runtimes: RuntimeCheck[];
  query: string;
  consolePreview: Record<string, string>;
  onAdd: () => void;
  onOpen: (id: string) => void;
  onStart: (app: AppConfig) => void;
  onStop: (app: AppConfig) => void;
  onRestart: (app: AppConfig) => void;
  onPin: (id: string) => void;
  onReorder: (ids: string[]) => void;
}) {
  const [filter, setFilter] = useState<"all" | ApplicationType>("all");
  const [dragged, setDragged] = useState<string>();
  const metricMap = new Map(metrics.map((metric) => [metric.appId, metric]));
  const running = metrics.filter((metric) => metric.status === "online").length;
  const filtered = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    return [...apps]
      .sort((a, b) => Number(b.pinned) - Number(a.pinned) || a.order - b.order)
      .filter((item) => filter === "all" || item.type === filter)
      .filter(
        (item) =>
          !normalizedQuery ||
          item.name.toLowerCase().includes(normalizedQuery) ||
          appTypeLabel(item.type).toLowerCase().includes(normalizedQuery) ||
          item.directory.toLowerCase().includes(normalizedQuery),
      );
  }, [apps, filter, query]);

  const reorder = (targetId: string) => {
    if (!dragged || dragged === targetId) return;
    const ordered = [...apps].sort((a, b) => a.order - b.order).map((item) => item.id);
    const sourceIndex = ordered.indexOf(dragged);
    const targetIndex = ordered.indexOf(targetId);
    ordered.splice(sourceIndex, 1);
    ordered.splice(targetIndex, 0, dragged);
    onReorder(ordered);
    setDragged(undefined);
  };

  return (
    <div className="page dashboard-page">
      <section className="hero-row">
        <div>
          <span className="eyebrow">LOCAL CONTROL CENTRE</span>
          <h1>Good evening <span>— everything’s in reach.</span></h1>
          <p>Run local services, watch resource use, and get back to building.</p>
        </div>
        <button className="button primary add-button" onClick={onAdd}>
          <Plus size={18} /> Add application
        </button>
      </section>

      <section className="overview-strip glass">
        <div className="overview-item">
          <span className="overview-icon coral"><CirclePlay size={19} /></span>
          <div><strong>{running}</strong><small>Running now</small></div>
        </div>
        <div className="overview-item">
          <span className="overview-icon purple"><Cpu size={19} /></span>
          <div><strong>{system.cpu.toFixed(0)}%</strong><small>System CPU</small></div>
        </div>
        <div className="overview-item">
          <span className="overview-icon blue"><MemoryStick size={19} /></span>
          <div>
            <strong>{formatBytes(system.memoryUsed)}</strong>
            <small>of {formatBytes(system.memoryTotal)} RAM</small>
          </div>
        </div>
        <div className="overview-item">
          <span className="overview-icon green"><Server size={19} /></span>
          <div><strong>{apps.length}</strong><small>Applications</small></div>
        </div>
      </section>

      {runtimes.some((runtime) => !runtime.installed) && (
        <section className="runtime-notice glass">
          <Code2 size={19} />
          <div>
            <strong>Some runtimes are missing</strong>
            <span>
              {runtimes.filter((runtime) => !runtime.installed).map((runtime) => runtime.runtime).join(", ")}
              {" "}must be installed before related apps can start.
            </span>
          </div>
        </section>
      )}

      <section className="section-heading">
        <div>
          <h2>Your applications</h2>
          <p>{filtered.length} visible · drag cards to reorder</p>
        </div>
        <div className="filter-pills">
          {(["all", "minecraft", "discord", "website", "custom"] as const).map((item) => (
            <button
              key={item}
              className={filter === item ? "active" : ""}
              onClick={() => setFilter(item)}
            >
              {item === "all" ? "All" : appTypeLabel(item)}
            </button>
          ))}
        </div>
      </section>

      {filtered.length ? (
        <section className="app-grid">
          {filtered.map((app) => (
            <AppCard
              key={app.id}
              app={app}
              metric={metricMap.get(app.id)}
              preview={consolePreview[app.id]}
              onOpen={() => onOpen(app.id)}
              onStart={() => onStart(app)}
              onStop={() => onStop(app)}
              onRestart={() => onRestart(app)}
              onPin={() => onPin(app.id)}
              onDragStart={() => setDragged(app.id)}
              onDrop={() => reorder(app.id)}
            />
          ))}
        </section>
      ) : (
        <section className="empty-state glass">
          <span className="empty-orbit"><Plus size={26} /></span>
          <h3>{apps.length ? "No applications match" : "Add your first application"}</h3>
          <p>
            {apps.length
              ? "Try another search or type filter."
              : "Minecraft, Discord bots, websites, and custom processes all live here."}
          </p>
          {!apps.length && (
            <button className="button primary" onClick={onAdd}>
              <Plus size={18} /> Add application
            </button>
          )}
        </section>
      )}
    </div>
  );
}

function AppCard({
  app,
  metric,
  preview,
  onOpen,
  onStart,
  onStop,
  onRestart,
  onPin,
  onDragStart,
  onDrop,
}: {
  app: AppConfig;
  metric?: ProcessMetrics;
  preview?: string;
  onOpen: () => void;
  onStart: () => void;
  onStop: () => void;
  onRestart: () => void;
  onPin: () => void;
  onDragStart: () => void;
  onDrop: () => void;
}) {
  const status = metric?.status ?? "offline";
  const online = status === "online";
  const Icon = typeIcons[app.type];
  const configured = Boolean(app.directory && app.startCommand);
  return (
    <article
      className={`app-card glass ${online ? "is-online" : ""}`}
      onClick={onOpen}
      draggable
      onDragStart={onDragStart}
      onDragOver={(event) => event.preventDefault()}
      onDrop={onDrop}
    >
      <div className="card-accent" style={{ background: typeColors[app.type] }} />
      <header className="app-card-header">
        <span className="drag-handle" title="Drag to reorder"><GripVertical size={17} /></span>
        <span className="app-icon" style={{ "--app-color": typeColors[app.type] } as React.CSSProperties}>
          {app.icon || <Icon size={25} />}
        </span>
        <div className="app-title">
          <h3>{app.name}</h3>
          <span>{appTypeLabel(app.type)}</span>
        </div>
        <button
          className={`icon-button pin-button ${app.pinned ? "pinned" : ""}`}
          aria-label={app.pinned ? "Unpin" : "Pin"}
          onClick={(event) => {
            event.stopPropagation();
            onPin();
          }}
        >
          {app.pinned ? <Star size={17} fill="currentColor" /> : <Pin size={17} />}
        </button>
        <MoreHorizontal size={18} className="muted-icon" />
      </header>

      <div className="status-row">
        <span className={`status-chip ${status}`}>
          <i />
          {status === "online" ? "Online" : status === "crashed" ? "Crashed" : status}
        </span>
        {app.port ? <span className="port-chip">:{app.port}</span> : <span className="port-chip">No port</span>}
        {!configured && <span className="setup-chip">Setup needed</span>}
      </div>

      <div className="metric-grid">
        <div>
          <span>CPU</span>
          <strong>{metric?.cpu.toFixed(1) ?? "0.0"}%</strong>
        </div>
        <div>
          <span>Memory</span>
          <strong>{formatBytes(metric?.memoryBytes ?? 0)}</strong>
        </div>
        <div>
          <span>Uptime</span>
          <strong>{formatUptime(metric?.uptimeSeconds ?? 0)}</strong>
        </div>
      </div>

      <div className={`quick-console ${preview ? "" : "idle"}`}>
        <TerminalSquare size={14} />
        <code>{preview || (online ? "Waiting for console output…" : "Console is idle")}</code>
      </div>

      <footer className="card-actions">
        {online || status === "starting" ? (
          <button
            className="button danger grow"
            onClick={(event) => {
              event.stopPropagation();
              onStop();
            }}
          >
            <Square size={15} fill="currentColor" /> Stop
          </button>
        ) : (
          <button
            className="button start grow"
            onClick={(event) => {
              event.stopPropagation();
              configured ? onStart() : onOpen();
            }}
          >
            <CirclePlay size={17} /> {configured ? "Start" : "Configure"}
          </button>
        )}
        <button
          className="button restart"
          disabled={!online}
          onClick={(event) => {
            event.stopPropagation();
            onRestart();
          }}
        >
          <RotateCw size={16} /> Restart
        </button>
      </footer>
    </article>
  );
}
