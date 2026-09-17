import {
  Activity,
  ArrowDown,
  ArrowUp,
  CheckCircle2,
  CircleAlert,
  Cpu,
  Download,
  HardDrive,
  MemoryStick,
  Network,
  Server,
} from "lucide-react";
import type {
  AppConfig,
  ProcessMetrics,
  RuntimeCheck,
  SystemMetrics,
} from "../shared/types";
import { appTypeLabel, formatBytes, formatRate, formatUptime } from "../lib/format";

export function Monitoring({
  apps,
  metrics,
  system,
  history,
  runtimes,
}: {
  apps: AppConfig[];
  metrics: ProcessMetrics[];
  system: SystemMetrics;
  history: SystemMetrics[];
  runtimes: RuntimeCheck[];
}) {
  const running = metrics.filter((metric) => metric.status === "online").length;
  const memoryPercent = system.memoryTotal ? (system.memoryUsed / system.memoryTotal) * 100 : 0;
  const diskPercent = system.diskTotal ? (system.diskUsed / system.diskTotal) * 100 : 0;
  const metricMap = new Map(metrics.map((metric) => [metric.appId, metric]));
  return (
    <div className="page monitoring-page">
      <section className="hero-row">
        <div><span className="eyebrow">LIVE SYSTEM TELEMETRY</span><h1>Monitoring</h1><p>Resource health and process-level performance, sampled every two seconds.</p></div>
        <span className="live-indicator"><i />Live</span>
      </section>

      <section className="monitor-grid">
        <MonitorCard icon={Cpu} label="CPU load" value={`${system.cpu.toFixed(1)}%`} percent={system.cpu} color="#f08eb7" />
        <MonitorCard icon={MemoryStick} label="Memory used" value={formatBytes(system.memoryUsed)} sub={`of ${formatBytes(system.memoryTotal)}`} percent={memoryPercent} color="#8a98ff" />
        <MonitorCard icon={HardDrive} label="Disk used" value={formatBytes(system.diskUsed)} sub={`of ${formatBytes(system.diskTotal)}`} percent={diskPercent} color="#70c6ee" />
        <MonitorCard icon={Server} label="Processes online" value={`${running} / ${apps.length}`} percent={apps.length ? running / apps.length * 100 : 0} color="#6dd99a" />
      </section>

      <section className="chart-grid">
        <ChartPanel
          title="CPU over time"
          subtitle="System load · last two minutes"
          color="#ff7799"
          points={history.map((item) => item.cpu)}
          suffix="%"
        />
        <ChartPanel
          title="Memory over time"
          subtitle="Active system memory"
          color="#8d9aff"
          points={history.map((item) => item.memoryTotal ? item.memoryUsed / item.memoryTotal * 100 : 0)}
          suffix="%"
        />
      </section>

      <section className="panel glass">
        <div className="panel-heading">
          <div><h3>Application processes</h3><p>CPU, memory, uptime, PID, and port availability</p></div>
          <div className="network-rates">
            <span><ArrowDown size={14} />{formatRate(system.networkRx)}</span>
            <span><ArrowUp size={14} />{formatRate(system.networkTx)}</span>
          </div>
        </div>
        <div className="process-table">
          <div className="process-row process-head"><span>Application</span><span>Status</span><span>CPU</span><span>Memory</span><span>Uptime</span><span>PID</span><span>Port</span></div>
          {apps.map((app) => {
            const metric = metricMap.get(app.id);
            return (
              <div className="process-row" key={app.id}>
                <span className="process-app"><i>{app.icon}</i><span><strong>{app.name}</strong><small>{appTypeLabel(app.type)}</small></span></span>
                <span><span className={`status-chip tiny ${metric?.status ?? "offline"}`}><i />{metric?.status ?? "offline"}</span></span>
                <span>{metric?.cpu.toFixed(1) ?? "0.0"}%</span>
                <span>{formatBytes(metric?.memoryBytes ?? 0)}</span>
                <span>{formatUptime(metric?.uptimeSeconds ?? 0)}</span>
                <span>{metric?.pid ?? "—"}</span>
                <span>{app.port ? <small className={metric?.portOpen ? "text-green" : "text-muted"}>{app.port} · {metric?.portOpen ? "open" : "closed"}</small> : "—"}</span>
              </div>
            );
          })}
          {!apps.length && <div className="empty-inline process-empty">Add an application to see per-process metrics.</div>}
        </div>
      </section>

      <section className="panel glass runtime-panel">
        <div className="panel-heading"><div><h3>Runtime readiness</h3><p>Detected on this computer</p></div></div>
        <div className="runtime-grid">
          {runtimes.map((runtime) => (
            <article key={runtime.runtime}>
              <span className={runtime.installed ? "runtime-ok" : "runtime-missing"}>{runtime.installed ? <CheckCircle2 size={20} /> : <CircleAlert size={20} />}</span>
              <div><strong>{runtime.runtime === "java" ? "Java" : runtime.runtime === "node" ? "Node.js" : "Python"}</strong><small>{runtime.installed ? runtime.version : "Not detected"}</small></div>
              {!runtime.installed && <button className="button subtle small" onClick={() => window.haven.shell.url(runtime.installUrl)}><Download size={14} />Install</button>}
            </article>
          ))}
        </div>
      </section>
    </div>
  );
}

function MonitorCard({
  icon: Icon,
  label,
  value,
  sub,
  percent,
  color,
}: {
  icon: typeof Activity;
  label: string;
  value: string;
  sub?: string;
  percent: number;
  color: string;
}) {
  return (
    <article className="monitor-card glass">
      <span className="monitor-icon" style={{ color, background: `${color}18` }}><Icon size={20} /></span>
      <div><small>{label}</small><strong>{value}</strong>{sub && <span>{sub}</span>}</div>
      <div className="ring" style={{ "--ring": color, "--percent": `${Math.min(100, Math.max(0, percent)) * 3.6}deg` } as React.CSSProperties}><i>{percent.toFixed(0)}%</i></div>
    </article>
  );
}

function ChartPanel({
  title,
  subtitle,
  points,
  color,
  suffix,
}: {
  title: string;
  subtitle: string;
  points: number[];
  color: string;
  suffix: string;
}) {
  const safePoints = points.length > 1 ? points : [0, 0];
  const coordinates = safePoints
    .map((point, index) => `${(index / (safePoints.length - 1)) * 100},${100 - Math.min(100, Math.max(0, point))}`)
    .join(" ");
  const latest = safePoints.at(-1) ?? 0;
  return (
    <section className="chart-panel glass">
      <header><div><h3>{title}</h3><p>{subtitle}</p></div><strong style={{ color }}>{latest.toFixed(1)}{suffix}</strong></header>
      <div className="chart-area">
        <span className="axis top">100%</span><span className="axis middle">50%</span><span className="axis bottom">0%</span>
        <svg viewBox="0 0 100 100" preserveAspectRatio="none">
          <defs>
            <linearGradient id={`gradient-${title.replaceAll(" ", "-")}`} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={color} stopOpacity=".35" />
              <stop offset="100%" stopColor={color} stopOpacity="0" />
            </linearGradient>
          </defs>
          <polygon points={`0,100 ${coordinates} 100,100`} fill={`url(#gradient-${title.replaceAll(" ", "-")})`} />
          <polyline points={coordinates} fill="none" stroke={color} strokeWidth="1.3" vectorEffect="non-scaling-stroke" />
        </svg>
      </div>
    </section>
  );
}
