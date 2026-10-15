import {
  Bot,
  Box,
  ChevronDown,
  FolderOpen,
  Globe2,
  Plus,
  ShieldCheck,
  TerminalSquare,
  Trash2,
} from "lucide-react";
import { useEffect, useState } from "react";
import type { AppConfig, AppDraft, ApplicationType, EnvVariable } from "../shared/types";
import { appTypeLabel } from "../lib/format";
import { Modal } from "./Modal";

const presets: Record<
  ApplicationType,
  {
    icon: string;
    description: string;
    startCommand: string;
    stopCommand: string;
    port?: number;
    ramLimitMb?: number;
  }
> = {
  minecraft: {
    icon: "⛏️",
    description: "Java server, worlds, plugins, and backups",
    startCommand: "java -Xmx2G -jar server.jar nogui",
    stopCommand: "stop",
    port: 25565,
    ramLimitMb: 2048,
  },
  discord: {
    icon: "🤖",
    description: "Node.js or Python bot with protected token",
    startCommand: "npm start",
    stopCommand: "CTRL+C",
  },
  website: {
    icon: "🌐",
    description: "Local web app, runtime, port, and environment",
    startCommand: "npm run dev",
    stopCommand: "CTRL+C",
    port: 3000,
  },
  custom: {
    icon: "⚙️",
    description: "Any executable, script, or long-running command",
    startCommand: "",
    stopCommand: "CTRL+C",
  },
};

function createDraft(type: ApplicationType): AppDraft {
  const preset = presets[type];
  return {
    name: `New ${appTypeLabel(type)}`,
    type,
    icon: preset.icon,
    directory: "",
    startCommand: preset.startCommand,
    stopCommand: preset.stopCommand,
    port: preset.port,
    ramLimitMb: preset.ramLimitMb,
    autoStart: false,
    autoRestart: true,
    restartDelaySeconds: 5,
    pinned: false,
    env: [],
    advanced: {
      maxBackups: 5,
      serverIp: type === "minecraft" ? "0.0.0.0" : undefined,
      runtime: type === "discord" ? "node" : type === "website" ? "node" : undefined,
      jarFile: type === "minecraft" ? "server.jar" : undefined,
    },
  };
}

export function ApplicationForm({
  open,
  initial,
  onClose,
  onSave,
}: {
  open: boolean;
  initial?: AppConfig;
  onClose: () => void;
  onSave: (draft: AppDraft) => Promise<void>;
}) {
  const [draft, setDraft] = useState<AppDraft>(() => createDraft("minecraft"));
  const [saving, setSaving] = useState(false);
  const [advanced, setAdvanced] = useState(false);

  useEffect(() => {
    if (open) {
      setDraft(initial ? structuredClone(initial) : createDraft("minecraft"));
      setAdvanced(false);
    }
  }, [open, initial]);

  const selectType = (type: ApplicationType) => {
    if (initial) return;
    setDraft(createDraft(type));
  };

  const patch = <K extends keyof AppDraft>(key: K, value: AppDraft[K]) =>
    setDraft((current) => ({ ...current, [key]: value }));

  const patchAdvanced = (key: keyof AppDraft["advanced"], value: string | number | undefined) =>
    setDraft((current) => ({
      ...current,
      advanced: { ...current.advanced, [key]: value },
    }));

  const setMinecraftRam = (ramLimitMb: number) =>
    setDraft((current) => ({
      ...current,
      ramLimitMb,
      startCommand: current.startCommand.replace(/-Xmx\d+[MG]/i, `-Xmx${ramLimitMb}M`),
    }));

  const setMinecraftJar = (jarFile?: string) =>
    setDraft((current) => {
      const previous = current.advanced.jarFile;
      return {
        ...current,
        startCommand:
          previous && jarFile
            ? current.startCommand.replace(`-jar ${previous}`, `-jar ${jarFile}`)
            : current.startCommand,
        advanced: { ...current.advanced, jarFile },
      };
    });

  const setJavaPath = (javaPath: string) =>
    setDraft((current) => {
      const previous = current.advanced.javaPath || "java";
      return {
        ...current,
        startCommand: current.startCommand.startsWith(`${previous} `)
          ? `${javaPath}${current.startCommand.slice(previous.length)}`
          : current.startCommand,
        advanced: { ...current.advanced, javaPath },
      };
    });

  const save = async () => {
    setSaving(true);
    try {
      await onSave({
        ...draft,
        name: draft.name.trim(),
        directory: draft.directory.trim(),
        startCommand: draft.startCommand.trim(),
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={initial ? `Configure ${initial.name}` : "Add application"}
      subtitle="Local-first process control. Nothing is uploaded."
      wide
    >
      {!initial && (
        <div className="type-selector">
          {(Object.keys(presets) as ApplicationType[]).map((type) => {
            const icons = {
              minecraft: Box,
              discord: Bot,
              website: Globe2,
              custom: TerminalSquare,
            };
            const Icon = icons[type];
            return (
              <button
                key={type}
                className={draft.type === type ? "selected" : ""}
                onClick={() => selectType(type)}
              >
                <span><Icon size={21} /></span>
                <strong>{appTypeLabel(type)}</strong>
                <small>{presets[type].description}</small>
              </button>
            );
          })}
        </div>
      )}

      <div className="form-grid">
        <label>
          <span>Application name</span>
          <input
            value={draft.name}
            onChange={(event) => patch("name", event.target.value)}
            placeholder="My server"
          />
        </label>
        <label>
          <span>Icon</span>
          <input
            value={draft.icon}
            onChange={(event) => patch("icon", event.target.value)}
            placeholder="Emoji or short label"
            maxLength={4}
          />
        </label>
        <label className="span-2">
          <span>Working directory</span>
          <div className="input-with-button">
            <input
              value={draft.directory}
              onChange={(event) => patch("directory", event.target.value)}
              placeholder="C:\Servers\my-app"
            />
            <button
              className="button subtle"
              onClick={async () => {
                const selected = await window.haven.dialog.directory();
                if (selected) patch("directory", selected);
              }}
            >
              <FolderOpen size={17} /> Browse
            </button>
          </div>
        </label>
        <label className="span-2">
          <span>Start command</span>
          <input
            className="code-input"
            value={draft.startCommand}
            onChange={(event) => patch("startCommand", event.target.value)}
            placeholder="npm start"
          />
        </label>
        <label>
          <span>Stop command</span>
          <input
            className="code-input"
            value={draft.stopCommand}
            onChange={(event) => patch("stopCommand", event.target.value)}
            placeholder="CTRL+C"
          />
        </label>
        <label>
          <span>Port</span>
          <input
            type="number"
            min={1}
            max={65535}
            value={draft.port ?? ""}
            onChange={(event) => patch("port", event.target.value ? Number(event.target.value) : undefined)}
            placeholder="Optional"
          />
        </label>

        {draft.type === "minecraft" && (
          <>
            <label>
              <span>RAM limit · {(draft.ramLimitMb ?? 2048) / 1024} GB</span>
              <input
                type="range"
                min={1024}
                max={16384}
                step={512}
                value={draft.ramLimitMb ?? 2048}
                onChange={(event) => setMinecraftRam(Number(event.target.value))}
              />
            </label>
            <label>
              <span>Server JAR</span>
              <div className="input-with-button compact">
                <input
                  value={draft.advanced.jarFile ?? ""}
                  onChange={(event) => setMinecraftJar(event.target.value)}
                  placeholder="server.jar"
                />
                <button
                  className="icon-button field-button"
                  onClick={async () => {
                    const selected = await window.haven.dialog.file(["jar"]);
                    if (selected) setMinecraftJar(selected.split(/[\\/]/).pop());
                  }}
                  aria-label="Choose JAR"
                >
                  <FolderOpen size={17} />
                </button>
              </div>
            </label>
          </>
        )}

        {draft.type === "discord" && (
          <div className="security-note span-2">
            <ShieldCheck size={20} />
            <div>
              <strong>Token is configured after this app is saved</strong>
              <span>It is AES-256-GCM encrypted with your master password and never shown again.</span>
            </div>
          </div>
        )}

        <div className="toggle-row span-2">
          <Toggle
            checked={draft.autoRestart}
            onChange={(checked) => patch("autoRestart", checked)}
            label="Auto-restart after a crash"
          />
          <Toggle
            checked={draft.autoStart}
            onChange={(checked) => patch("autoStart", checked)}
            label="Start with this PC"
          />
          <Toggle
            checked={draft.pinned}
            onChange={(checked) => patch("pinned", checked)}
            label="Pin to dashboard"
          />
        </div>
      </div>

      <button className={`advanced-toggle ${advanced ? "open" : ""}`} onClick={() => setAdvanced(!advanced)}>
        <ChevronDown size={17} />
        Advanced configuration
      </button>

      {advanced && (
        <div className="advanced-panel">
          <div className="form-grid">
            <label>
              <span>Restart delay (seconds)</span>
              <input
                type="number"
                min={1}
                value={draft.restartDelaySeconds}
                onChange={(event) => patch("restartDelaySeconds", Number(event.target.value))}
              />
            </label>
            <label>
              <span>Scheduled restart (hours)</span>
              <input
                type="number"
                min={0}
                value={draft.advanced.scheduledRestartHours ?? ""}
                onChange={(event) =>
                  patchAdvanced(
                    "scheduledRestartHours",
                    event.target.value ? Number(event.target.value) : undefined,
                  )
                }
                placeholder="Disabled"
              />
            </label>
            {draft.type === "minecraft" && (
              <>
                <label>
                  <span>Java executable</span>
                  <input
                    value={draft.advanced.javaPath ?? "java"}
                    onChange={(event) => setJavaPath(event.target.value)}
                  />
                </label>
                <label>
                  <span>Max backups</span>
                  <input
                    type="number"
                    min={1}
                    max={50}
                    value={draft.advanced.maxBackups ?? 5}
                    onChange={(event) => patchAdvanced("maxBackups", Number(event.target.value))}
                  />
                </label>
              </>
            )}
            {(draft.type === "discord" || draft.type === "website") && (
              <label>
                <span>Runtime</span>
                <select
                  value={draft.advanced.runtime ?? "node"}
                  onChange={(event) => patchAdvanced("runtime", event.target.value)}
                >
                  <option value="node">Node.js</option>
                  <option value="python">Python</option>
                </select>
              </label>
            )}
          </div>
          <EnvEditor env={draft.env} onChange={(env) => patch("env", env)} />
        </div>
      )}

      <div className="modal-actions sticky-actions">
        <button className="button subtle" onClick={onClose}>Cancel</button>
        <button className="button primary" disabled={!draft.name.trim() || saving} onClick={save}>
          {saving ? "Saving…" : initial ? "Save changes" : "Add application"}
        </button>
      </div>
    </Modal>
  );
}

function Toggle({
  checked,
  onChange,
  label,
}: {
  checked: boolean;
  onChange: (checked: boolean) => void;
  label: string;
}) {
  return (
    <label className="toggle-control">
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        className={checked ? "checked" : ""}
        onClick={() => onChange(!checked)}
      >
        <i />
      </button>
      <span>{label}</span>
    </label>
  );
}

function EnvEditor({
  env,
  onChange,
}: {
  env: EnvVariable[];
  onChange: (env: EnvVariable[]) => void;
}) {
  const update = (index: number, key: keyof EnvVariable, value: string) =>
    onChange(env.map((item, itemIndex) => (index === itemIndex ? { ...item, [key]: value } : item)));
  return (
    <div className="env-editor">
      <div className="inline-heading">
        <div><strong>Environment variables</strong><span>Stored locally in the application config</span></div>
        <button className="button subtle small" onClick={() => onChange([...env, { key: "", value: "" }])}>
          <Plus size={15} /> Add variable
        </button>
      </div>
      {env.map((variable, index) => (
        <div className="env-row" key={index}>
          <input value={variable.key} onChange={(event) => update(index, "key", event.target.value)} placeholder="KEY" />
          <input value={variable.value} onChange={(event) => update(index, "value", event.target.value)} placeholder="Value" />
          <button className="icon-button" onClick={() => onChange(env.filter((_, itemIndex) => itemIndex !== index))}>
            <Trash2 size={16} />
          </button>
        </div>
      ))}
      {!env.length && <p className="empty-inline">No environment variables configured.</p>}
    </div>
  );
}
