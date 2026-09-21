import { useEffect, useState } from 'react';
import { PageHeader } from '../components/PageHeader';
import { normalizeHex } from '../lib/hex';
import { DEFAULT_SETTINGS, type Settings } from '../lib/storage';
import { useApp } from '../state';

export function SettingsPage() {
  const { settings, setSettings, toast } = useApp();
  const [draft, setDraft] = useState<Settings>(settings);
  const [savedTick, setSavedTick] = useState(0);

  useEffect(() => {
    setDraft(settings);
  }, [settings]);

  function commit(next: Settings) {
    const clean: Settings = {
      ...next,
      accent: normalizeHex(next.accent),
      secondary: normalizeHex(next.secondary, '#94ff00'),
      apiBaseUrl: next.apiBaseUrl.trim() || DEFAULT_SETTINGS.apiBaseUrl,
      apiModel: next.apiModel.trim() || DEFAULT_SETTINGS.apiModel,
    };
    setDraft(clean);
    setSettings(clean);
    setSavedTick((value) => value + 1);
  }

  return (
    <div className="page">
      <PageHeader
        kicker="Local"
        title="Settings"
        description="Accent colours feed AI Config and the Colour page. Custom symbols and these settings stay in the app data folder."
      />

      <section className="panel">
        <div className="panel-head">
          <h2>Colours</h2>
          <p>Crimson is the default bounty accent. Lime is the amount highlight.</p>
        </div>
        <div className="settings-grid">
          <label className="field">
            <span>Accent</span>
            <div className="stop">
              <input
                type="color"
                value={normalizeHex(draft.accent)}
                aria-label="Accent colour"
                onChange={(event) => commit({ ...draft, accent: event.target.value })}
              />
              <input
                value={draft.accent}
                onChange={(event) => setDraft({ ...draft, accent: event.target.value })}
                onBlur={() => commit(draft)}
                spellCheck={false}
              />
            </div>
          </label>
          <label className="field">
            <span>Highlight</span>
            <div className="stop">
              <input
                type="color"
                value={normalizeHex(draft.secondary, '#94ff00')}
                aria-label="Highlight colour"
                onChange={(event) => commit({ ...draft, secondary: event.target.value })}
              />
              <input
                value={draft.secondary}
                onChange={(event) => setDraft({ ...draft, secondary: event.target.value })}
                onBlur={() => commit(draft)}
                spellCheck={false}
              />
            </div>
          </label>
        </div>
      </section>

      <section className="panel">
        <div className="panel-head">
          <h2>Model rewrite</h2>
          <p>
            Optional OpenAI-compatible endpoint. Leave this off and Generate still rewrites configs locally. The key never
            leaves this computer except when you call the endpoint you set.
          </p>
        </div>
        <label className="check">
          <input
            type="checkbox"
            checked={draft.apiEnabled}
            onChange={(event) => commit({ ...draft, apiEnabled: event.target.checked })}
          />
          <span>Use a model when generating</span>
        </label>
        <div className="settings-grid">
          <label className="field">
            <span>Base URL</span>
            <input
              value={draft.apiBaseUrl}
              onChange={(event) => setDraft({ ...draft, apiBaseUrl: event.target.value })}
              onBlur={() => commit(draft)}
              placeholder="https://api.openai.com/v1"
              spellCheck={false}
            />
          </label>
          <label className="field">
            <span>Model</span>
            <input
              value={draft.apiModel}
              onChange={(event) => setDraft({ ...draft, apiModel: event.target.value })}
              onBlur={() => commit(draft)}
              spellCheck={false}
            />
          </label>
          <label className="field wide">
            <span>API key</span>
            <input
              type="password"
              value={draft.apiKey}
              onChange={(event) => setDraft({ ...draft, apiKey: event.target.value })}
              onBlur={() => commit(draft)}
              placeholder="sk-…"
              autoComplete="off"
            />
          </label>
        </div>
      </section>

      <p className="muted saved-line" aria-live="polite">
        {savedTick > 0 ? 'Saved.' : 'Changes save automatically.'} Avix Studios 1.0.0
      </p>
      <button
        type="button"
        className="btn secondary"
        onClick={() => {
          commit(DEFAULT_SETTINGS);
          toast('Settings reset');
        }}
      >
        Reset colours and model
      </button>
    </div>
  );
}
