import {
  Bell,
  Command,
  Plus,
  Search,
  ShieldCheck,
  Sparkles,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ActivityPage } from "./components/ActivityPage";
import { ApplicationDetail } from "./components/ApplicationDetail";
import { ApplicationForm } from "./components/ApplicationForm";
import { Dashboard } from "./components/Dashboard";
import { ConfirmModal, Modal } from "./components/Modal";
import { Monitoring } from "./components/Monitoring";
import { SettingsPage } from "./components/Settings";
import { Sidebar } from "./components/Sidebar";
import { friendlyError } from "./lib/format";
import type {
  AppConfig,
  AppDraft,
  AppSettings,
  AppSnapshot,
  ConsoleLine,
  Page,
  ProcessMetrics,
  SystemMetrics,
} from "./shared/types";

const EMPTY_SYSTEM: SystemMetrics = {
  cpu: 0,
  memoryUsed: 0,
  memoryTotal: 0,
  diskUsed: 0,
  diskTotal: 0,
  networkRx: 0,
  networkTx: 0,
  timestamp: Date.now(),
};

export default function App() {
  const [snapshot, setSnapshot] = useState<AppSnapshot>();
  const [metrics, setMetrics] = useState<ProcessMetrics[]>([]);
  const [system, setSystem] = useState<SystemMetrics>(EMPTY_SYSTEM);
  const [history, setHistory] = useState<SystemMetrics[]>([]);
  const [page, setPage] = useState<Page>("dashboard");
  const [selectedId, setSelectedId] = useState<string>();
  const [query, setQuery] = useState("");
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<AppConfig>();
  const [consoleLines, setConsoleLines] = useState<Record<string, ConsoleLine[]>>({});
  const [toast, setToast] = useState<{ message: string; type: "success" | "error" }>();
  const [stopTarget, setStopTarget] = useState<AppConfig>();
  const [unlock, setUnlock] = useState<{ app: AppConfig; action: "start" | "restart" }>();
  const [masterPassword, setMasterPassword] = useState("");
  const searchRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    let cleanups: (() => void)[] = [];
    void window.haven
      .bootstrap()
      .then((initial) => {
        setSnapshot(initial);
        setMetrics(initial.metrics);
        setSystem(initial.system);
        setHistory([initial.system]);
        cleanups = [
          window.haven.events.onState((state) =>
            setSnapshot((current) => current && { ...current, state }),
          ),
          window.haven.events.onMetrics(setMetrics),
          window.haven.events.onSystem((next) => {
            setSystem(next);
            setHistory((current) => [...current, next].slice(-60));
          }),
          window.haven.events.onConsole((line) => {
            setConsoleLines((current) => ({
              ...current,
              [line.appId]: [...(current[line.appId] ?? []), line].slice(-1_000),
            }));
          }),
        ];
      })
      .catch(showError);
    return () => cleanups.forEach((cleanup) => cleanup());
  }, []);

  useEffect(() => {
    if (!snapshot || !selectedId) return;
    if (!snapshot.state.apps.some((app) => app.id === selectedId)) setSelectedId(undefined);
  }, [snapshot, selectedId]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.ctrlKey && event.key.toLowerCase() === "k") {
        event.preventDefault();
        searchRef.current?.focus();
      }
      if (event.ctrlKey && event.key.toLowerCase() === "n") {
        event.preventDefault();
        setEditing(undefined);
        setFormOpen(true);
      }
      if (event.ctrlKey && event.key === "1") {
        setSelectedId(undefined);
        setPage("dashboard");
      }
      if (event.ctrlKey && event.key === "2") {
        setSelectedId(undefined);
        setPage("monitoring");
      }
      if (event.key === "Escape") {
        setFormOpen(false);
        setStopTarget(undefined);
        setUnlock(undefined);
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(undefined), 3_500);
    return () => window.clearTimeout(timer);
  }, [toast]);

  const showError = useCallback((error: unknown) => {
    setToast({ message: friendlyError(error), type: "error" });
  }, []);
  const showSuccess = useCallback((message: string) => {
    setToast({ message, type: "success" });
  }, []);

  const processAction = async (
    app: AppConfig,
    action: "start" | "restart",
    password?: string,
  ) => {
    try {
      await window.haven.process[action](app.id, password);
      showSuccess(`${app.name} ${action === "start" ? "started" : "restarted"}.`);
    } catch (error) {
      showError(error);
    }
  };

  const requestStart = (app: AppConfig, action: "start" | "restart" = "start") => {
    const current = metrics.find((metric) => metric.appId === app.id);
    const needsUnlock = app.hasSecret && !(action === "restart" && current?.status === "online");
    if (needsUnlock) {
      setMasterPassword("");
      setUnlock({ app, action });
    } else {
      void processAction(app, action);
    }
  };

  const requestStop = (app: AppConfig) => {
    if (snapshot?.state.settings.confirmBeforeStop) setStopTarget(app);
    else void stop(app);
  };

  const stop = async (app: AppConfig) => {
    setStopTarget(undefined);
    try {
      await window.haven.process.stop(app.id);
      showSuccess(`${app.name} stopped.`);
    } catch (error) {
      showError(error);
    }
  };

  const saveApp = async (draft: AppDraft) => {
    try {
      const saved = await window.haven.apps.save(draft);
      setFormOpen(false);
      setEditing(undefined);
      showSuccess(`${saved.name} saved.`);
      if (draft.id) setSelectedId(saved.id);
    } catch (error) {
      showError(error);
      throw error;
    }
  };

  const updateSettings = async (patch: Partial<AppSettings>) => {
    await window.haven.settings.save(patch);
  };

  const navigate = (nextPage: Page) => {
    setSelectedId(undefined);
    setPage(nextPage);
  };

  const state = snapshot?.state;
  const selected = state?.apps.find((app) => app.id === selectedId);
  const selectedMetric = metrics.find((metric) => metric.appId === selectedId);
  const preview = Object.fromEntries(
    Object.entries(consoleLines).map(([id, lines]) => [id, lines.at(-1)?.text ?? ""]),
  );
  const backgroundStyle = state?.settings.customBackground
    ? ({ "--custom-background": `url("${state.settings.customBackground}")` } as React.CSSProperties)
    : undefined;
  const errorCount = state?.activity.filter((event) => event.level === "error").length ?? 0;

  if (!snapshot || !state) {
    return (
      <div className="loading-screen">
        <span className="brand-mark large"><span /><span /><span /></span>
        <strong>Opening Haven</strong>
        <i />
      </div>
    );
  }

  return (
    <div
      className={`app-shell theme-${state.settings.theme} ${state.settings.customBackground ? "custom-background" : ""}`}
      style={{ "--accent": state.settings.accent, ...backgroundStyle } as React.CSSProperties}
    >
      <div className="background-scene" />
      <div className="atmosphere" />
      <div className="titlebar-drag">
        <span>HAVEN</span>
      </div>
      <Sidebar
        page={page}
        detailOpen={Boolean(selected)}
        onNavigate={navigate}
        onBack={() => setSelectedId(undefined)}
      />

      <main className="main-area">
        <header className="topbar glass">
          <div className="global-search">
            <Search size={17} />
            <input
              ref={searchRef}
              value={query}
              onChange={(event) => {
                setQuery(event.target.value);
                if (event.target.value) {
                  setSelectedId(undefined);
                  setPage("dashboard");
                }
              }}
              placeholder="Search applications, types, and folders…"
            />
            <kbd><Command size={11} />K</kbd>
          </div>
          <div className="topbar-actions">
            <span className="local-pill"><ShieldCheck size={14} />Local</span>
            <button className="icon-button notification-button" onClick={() => navigate("activity")} aria-label="Activity">
              <Bell size={18} />
              {errorCount > 0 && <i>{Math.min(errorCount, 9)}</i>}
            </button>
            <button className="avatar" onClick={() => navigate("settings")}>L</button>
          </div>
        </header>

        <div className="page-scroll">
          {selected ? (
            <ApplicationDetail
              app={selected}
              metric={selectedMetric}
              runtimes={snapshot.runtimes}
              liveLines={consoleLines[selected.id] ?? []}
              onBack={() => setSelectedId(undefined)}
              onEdit={() => {
                setEditing(selected);
                setFormOpen(true);
              }}
              onDeleted={() => setSelectedId(undefined)}
              onStart={(app) => requestStart(app)}
              onStop={requestStop}
              onRestart={(app) => requestStart(app, "restart")}
              onError={showError}
              onSuccess={showSuccess}
            />
          ) : page === "dashboard" ? (
            <Dashboard
              apps={state.apps}
              metrics={metrics}
              system={system}
              runtimes={snapshot.runtimes}
              query={query}
              consolePreview={preview}
              onAdd={() => {
                setEditing(undefined);
                setFormOpen(true);
              }}
              onOpen={setSelectedId}
              onStart={(app) => requestStart(app)}
              onStop={requestStop}
              onRestart={(app) => requestStart(app, "restart")}
              onPin={(id) => void window.haven.apps.pin(id).catch(showError)}
              onReorder={(ids) => void window.haven.apps.reorder(ids).catch(showError)}
            />
          ) : page === "monitoring" ? (
            <Monitoring
              apps={state.apps}
              metrics={metrics}
              system={system}
              history={history}
              runtimes={snapshot.runtimes}
            />
          ) : page === "activity" ? (
            <ActivityPage
              events={state.activity}
              apps={state.apps}
              onClear={() => void window.haven.activity.clear().catch(showError)}
            />
          ) : (
            <SettingsPage
              settings={state.settings}
              runtimes={snapshot.runtimes}
              onSave={updateSettings}
              onSuccess={showSuccess}
              onError={showError}
            />
          )}
        </div>
      </main>

      <button
        className="floating-add"
        aria-label="Add application"
        onClick={() => {
          setEditing(undefined);
          setFormOpen(true);
        }}
      >
        <Plus size={20} />
      </button>

      <ApplicationForm
        open={formOpen}
        initial={editing}
        onClose={() => {
          setFormOpen(false);
          setEditing(undefined);
        }}
        onSave={saveApp}
      />

      <ConfirmModal
        open={Boolean(stopTarget)}
        title={`Stop ${stopTarget?.name ?? "application"}?`}
        message="The running process and its child processes will be stopped. Unsaved in-memory work may be lost."
        confirmLabel="Stop application"
        danger
        onCancel={() => setStopTarget(undefined)}
        onConfirm={() => stopTarget && stop(stopTarget)}
      />

      <Modal
        open={Boolean(unlock)}
        title={`Unlock ${unlock?.app.name ?? "bot"}`}
        subtitle="Enter the master password to decrypt the token for this process only."
        onClose={() => setUnlock(undefined)}
      >
        <div className="unlock-form">
          <span className="unlock-icon"><ShieldCheck size={23} /></span>
          <label><span>Master password</span><input autoFocus type="password" autoComplete="current-password" value={masterPassword} onChange={(event) => setMasterPassword(event.target.value)} onKeyDown={(event) => {
            if (event.key === "Enter" && masterPassword && unlock) {
              void processAction(unlock.app, unlock.action, masterPassword);
              setUnlock(undefined);
              setMasterPassword("");
            }
          }} /></label>
        </div>
        <div className="modal-actions">
          <button className="button subtle" onClick={() => setUnlock(undefined)}>Cancel</button>
          <button className="button primary" disabled={!masterPassword} onClick={() => {
            if (!unlock) return;
            void processAction(unlock.app, unlock.action, masterPassword);
            setUnlock(undefined);
            setMasterPassword("");
          }}>Unlock & {unlock?.action}</button>
        </div>
      </Modal>

      {toast && (
        <div className={`toast ${toast.type}`}>
          <span>{toast.type === "success" ? <Sparkles size={17} /> : "!"}</span>
          {toast.message}
        </div>
      )}
    </div>
  );
}
