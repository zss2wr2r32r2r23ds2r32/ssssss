import {
  Activity,
  AlertTriangle,
  CheckCircle2,
  CircleAlert,
  Info,
  Trash2,
} from "lucide-react";
import type { ActivityEvent, AppConfig } from "../shared/types";

export function ActivityPage({
  events,
  apps,
  onClear,
}: {
  events: ActivityEvent[];
  apps: AppConfig[];
  onClear: () => void;
}) {
  const appMap = new Map(apps.map((app) => [app.id, app]));
  return (
    <div className="page activity-page">
      <section className="hero-row">
        <div><span className="eyebrow">LOCAL EVENT HISTORY</span><h1>Activity</h1><p>Starts, stops, crashes, and process recovery in one timeline.</p></div>
        {events.length > 0 && <button className="button subtle" onClick={onClear}><Trash2 size={16} />Clear activity</button>}
      </section>
      <section className="activity-timeline glass">
        {events.map((event) => {
          const icons = { info: Info, success: CheckCircle2, warning: AlertTriangle, error: CircleAlert };
          const Icon = icons[event.level];
          const target = event.appId ? appMap.get(event.appId) : undefined;
          return (
            <article key={event.id} className={`event-row ${event.level}`}>
              <span className="event-icon"><Icon size={18} /></span>
              <div className="event-copy"><strong>{event.title}</strong><p>{event.message}</p><small>{target ? `${target.icon} ${target.name}` : "Haven"}</small></div>
              <time>{formatEventTime(event.timestamp)}</time>
            </article>
          );
        })}
        {!events.length && <div className="empty-state compact"><Activity size={29} /><h3>Everything is quiet</h3><p>Process events and crash notifications will appear here.</p></div>}
      </section>
    </div>
  );
}

function formatEventTime(timestamp: string) {
  const date = new Date(timestamp);
  const today = new Date();
  const sameDay = date.toDateString() === today.toDateString();
  return sameDay
    ? date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
    : date.toLocaleDateString([], { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" });
}
