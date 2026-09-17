import {
  Bell,
  Check,
  ChevronRight,
  Database,
  Download,
  ExternalLink,
  FolderOpen,
  HardDrive,
  Image,
  Keyboard,
  MonitorCog,
  Palette,
  RefreshCcw,
  ShieldCheck,
  SunMoon,
  Upload,
} from "lucide-react";
import { useEffect, useState } from "react";
import type { AppSettings, RuntimeCheck } from "../shared/types";
import { ConfirmModal } from "./Modal";

export function SettingsPage({
  settings,
  runtimes,
  onSave,
  onSuccess,
  onError,
}: {
  settings: AppSettings;
  runtimes: RuntimeCheck[];
  onSave: (patch: Partial<AppSettings>) => Promise<void>;
  onSuccess: (message: string) => void;
  onError: (error: unknown) => void;
}) {
  const [paths, setPaths] = useState({ data: "", logs: "" });
  const [version, setVersion] = useState("");
  const [confirmReset, setConfirmReset] = useState(false);
  useEffect(() => {
    void window.haven.paths().then(setPaths);
    void window.haven.version().then(setVersion);
  }, []);

  const save = async (patch: Partial<AppSettings>) => {
    try { await onSave(patch); } catch (error) { onError(error); }
  };
  return (
    <div className="page settings-page">
      <section className="hero-row">
        <div><span className="eyebrow">PERSONALISE YOUR WORKSPACE</span><h1>Settings</h1><p>Appearance, Windows integration, notifications, and local data.</p></div>
        <span className="version-chip">Haven {version || "…"}</span>
      </section>

      <div className="settings-layout">
        <div className="settings-main">
          <SettingsSection icon={Palette} title="Appearance" subtitle="Make Haven feel at home on your desktop">
            <SettingRow title="Theme" description="Switch between dark and light glass surfaces" icon={SunMoon}>
              <div className="segmented">
                <button className={settings.theme === "dark" ? "active" : ""} onClick={() => void save({ theme: "dark" })}>Dark</button>
                <button className={settings.theme === "light" ? "active" : ""} onClick={() => void save({ theme: "light" })}>Light</button>
              </div>
            </SettingRow>
            <SettingRow title="Accent colour" description="Used for active controls and highlights" icon={Palette}>
              <div className="accent-choices">
                {["#ff6678", "#8c91ff", "#4dc9c2", "#e197ff", "#e9a75c"].map((color) => (
                  <button key={color} className={settings.accent === color ? "active" : ""} style={{ background: color }} onClick={() => void save({ accent: color })}>{settings.accent === color && <Check size={13} />}</button>
                ))}
                <input type="color" value={settings.accent} onChange={(event) => void save({ accent: event.target.value })} title="Custom accent" />
              </div>
            </SettingRow>
            <SettingRow title="Custom background" description="Choose an image from this computer" icon={Image}>
              <div className="row-buttons">
                {settings.customBackground && <button className="button subtle small" onClick={() => void save({ customBackground: undefined })}>Use default</button>}
                <button className="button subtle small" onClick={async () => {
                  try {
                    const selected = await window.haven.dialog.background();
                    if (selected) await save({ customBackground: selected });
                  } catch (error) { onError(error); }
                }}><Image size={15} />Choose image</button>
              </div>
            </SettingRow>
          </SettingsSection>

          <SettingsSection icon={MonitorCog} title="Windows & behavior" subtitle="Startup and window preferences">
            <SettingRow title="Start with Windows" description="Launch Haven when you sign in" icon={MonitorCog}><Switch checked={settings.startWithWindows} onChange={(value) => void save({ startWithWindows: value })} /></SettingRow>
            <SettingRow title="Minimize to system tray" description="Keep processes accessible after closing the window" icon={HardDrive}><Switch checked={settings.minimizeToTray} onChange={(value) => void save({ minimizeToTray: value })} /></SettingRow>
            <SettingRow title="Windows notifications" description="Show native alerts for starts, stops, and crashes" icon={Bell}><Switch checked={settings.notifications} onChange={(value) => void save({ notifications: value })} /></SettingRow>
            <SettingRow title="Debug mode" description="Retain extra diagnostic information in local logs" icon={MonitorCog}><Switch checked={settings.debugMode} onChange={(value) => void save({ debugMode: value })} /></SettingRow>
          </SettingsSection>

          <SettingsSection icon={ShieldCheck} title="Safety" subtitle="Confirmation before destructive actions">
            <SettingRow title="Confirm before stopping" description="Avoid accidentally interrupting a running service" icon={ShieldCheck}><span className="secure-pill"><ShieldCheck size={13} />Always on</span></SettingRow>
            <SettingRow title="Confirm before deleting" description="Confirm application, file, and backup deletion" icon={ShieldCheck}><span className="secure-pill"><ShieldCheck size={13} />Always on</span></SettingRow>
          </SettingsSection>

          <SettingsSection icon={Database} title="Local data" subtitle="Configuration and logs stay on this PC">
            <SettingRow title="Data folder" description={paths.data || "Loading…"} icon={Database}><button className="icon-button" onClick={() => window.haven.shell.folder(paths.data)}><ExternalLink size={16} /></button></SettingRow>
            <SettingRow title="Log folder" description={paths.logs || "Loading…"} icon={FolderOpen}><button className="icon-button" onClick={() => window.haven.shell.folder(paths.logs)}><ExternalLink size={16} /></button></SettingRow>
            <div className="data-actions">
              <button className="button subtle" onClick={async () => { try { if (await window.haven.settings.export()) onSuccess("Configuration exported."); } catch (error) { onError(error); } }}><Download size={16} />Export config</button>
              <button className="button subtle" onClick={async () => { try { if (await window.haven.settings.import()) onSuccess("Configuration imported."); } catch (error) { onError(error); } }}><Upload size={16} />Import config</button>
              <button className="button danger-outline" onClick={() => setConfirmReset(true)}><RefreshCcw size={16} />Reset settings & apps</button>
            </div>
          </SettingsSection>
        </div>

        <aside className="settings-side">
          <section className="local-profile glass">
            <span className="local-avatar">L</span>
            <div><small>ACTIVE PROFILE</small><h3>Local Mode</h3><p>No account required. Configuration, logs, and secrets remain on this computer.</p></div>
            <span className="secure-pill"><ShieldCheck size={14} />Private</span>
          </section>
          <section className="shortcut-card glass">
            <header><Keyboard size={18} /><strong>Keyboard shortcuts</strong></header>
            <dl><div><dt>Global search</dt><dd>Ctrl K</dd></div><div><dt>Add application</dt><dd>Ctrl N</dd></div><div><dt>Dashboard</dt><dd>Ctrl 1</dd></div><div><dt>Monitoring</dt><dd>Ctrl 2</dd></div></dl>
          </section>
          <section className="runtime-summary glass">
            <header><MonitorCog size={18} /><strong>Runtime status</strong></header>
            {runtimes.map((runtime) => <div key={runtime.runtime}><span className={`status-dot ${runtime.installed ? "online" : ""}`} /><strong>{runtime.runtime}</strong><small>{runtime.installed ? "Ready" : "Missing"}</small></div>)}
            <button className="text-button" onClick={() => window.haven.shell.url("https://github.com/")}>Check for updates <ChevronRight size={14} /></button>
          </section>
        </aside>
      </div>

      <ConfirmModal open={confirmReset} title="Reset Haven?" message="All application configurations and activity history will be removed. Project folders and backup files are not deleted." confirmLabel="Reset everything" danger onCancel={() => setConfirmReset(false)} onConfirm={async () => {
        setConfirmReset(false);
        try { await window.haven.settings.reset(); onSuccess("Haven reset to defaults."); } catch (error) { onError(error); }
      }} />
    </div>
  );
}

function SettingsSection({ icon: Icon, title, subtitle, children }: { icon: typeof Palette; title: string; subtitle: string; children: React.ReactNode }) {
  return <section className="settings-section glass"><header><span><Icon size={19} /></span><div><h3>{title}</h3><p>{subtitle}</p></div></header><div>{children}</div></section>;
}

function SettingRow({ icon: Icon, title, description, children }: { icon: typeof Palette; title: string; description: string; children: React.ReactNode }) {
  return <div className="setting-row"><span className="setting-icon"><Icon size={17} /></span><div><strong>{title}</strong><small title={description}>{description}</small></div><div className="setting-control">{children}</div></div>;
}

function Switch({ checked, onChange }: { checked: boolean; onChange: (checked: boolean) => void }) {
  return <button type="button" role="switch" aria-checked={checked} className={`switch ${checked ? "checked" : ""}`} onClick={() => onChange(!checked)}><i /></button>;
}
