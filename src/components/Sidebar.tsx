import {
  Activity,
  Boxes,
  ChevronLeft,
  LayoutGrid,
  Settings,
  ShieldCheck,
} from "lucide-react";
import type { Page } from "../shared/types";

const items: { id: Page; label: string; icon: typeof LayoutGrid }[] = [
  { id: "dashboard", label: "Applications", icon: LayoutGrid },
  { id: "monitoring", label: "Monitoring", icon: Activity },
  { id: "activity", label: "Activity", icon: Boxes },
  { id: "settings", label: "Settings", icon: Settings },
];

export function Sidebar({
  page,
  detailOpen,
  onNavigate,
  onBack,
}: {
  page: Page;
  detailOpen: boolean;
  onNavigate: (page: Page) => void;
  onBack: () => void;
}) {
  return (
    <aside className="sidebar glass">
      <div className="brand">
        <span className="brand-mark">
          <span />
          <span />
          <span />
        </span>
        <div>
          <strong>Haven</strong>
          <small>Process manager</small>
        </div>
      </div>

      <nav className="side-nav" aria-label="Main navigation">
        {detailOpen && (
          <button className="nav-item back-item" onClick={onBack}>
            <ChevronLeft size={18} />
            Back to apps
          </button>
        )}
        {items.map((item) => {
          const Icon = item.icon;
          return (
            <button
              key={item.id}
              className={`nav-item ${!detailOpen && page === item.id ? "active" : ""}`}
              onClick={() => onNavigate(item.id)}
            >
              <Icon size={18} strokeWidth={1.8} />
              {item.label}
            </button>
          );
        })}
      </nav>

      <div className="local-mode-card">
        <span className="local-mode-icon">
          <ShieldCheck size={18} />
        </span>
        <div>
          <strong>Local Mode</strong>
          <small>Your data stays on this PC</small>
        </div>
        <span className="status-dot online" />
      </div>
    </aside>
  );
}
