import { useState } from 'react';
import { CopyButton } from '../components/CopyButton';
import { PageHeader } from '../components/PageHeader';
import { BOUNTY_EXAMPLE, PRESETS, rewriteConfig, unwrapModelYaml } from '../lib/aiConfig';
import { useApp } from '../state';

export function AIConfigPage() {
  const { settings, toast } = useApp();
  const [source, setSource] = useState(BOUNTY_EXAMPLE);
  const [instruction, setInstruction] = useState<string>(PRESETS[0].instruction);
  const [output, setOutput] = useState('');
  const [notes, setNotes] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [engine, setEngine] = useState<'local' | 'model' | ''>('');

  async function generate() {
    const local = rewriteConfig(source, {
      instruction,
      accent: settings.accent,
      secondary: settings.secondary,
    });
    setOutput(local.output);
    setNotes(local.notes);
    setEngine('local');

    if (!settings.apiEnabled) return;
    if (!settings.apiKey.trim()) {
      setNotes([...local.notes, 'Model rewrite is enabled, but no API key is set. Showing the local result.']);
      return;
    }

    setBusy(true);
    try {
      const payload = {
        baseUrl: settings.apiBaseUrl,
        apiKey: settings.apiKey,
        model: settings.apiModel,
        instruction,
        yaml: source,
        accent: settings.accent,
        secondary: settings.secondary,
      };
      const raw = window.avix?.completeAi
        ? await window.avix.completeAi(payload)
        : await completeFromBrowser(payload);
      setOutput(unwrapModelYaml(raw));
      setEngine('model');
      setNotes(['Rewritten by the configured model. Placeholders and unrelated keys should still be intact.']);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Model request failed';
      setNotes([...local.notes, `${message}. Showing the local rewrite.`]);
      toast('Model request failed');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="page">
      <PageHeader
        kicker="Local rewriter"
        title="AI Config"
        description="Paste plugin YAML and say what should change. Bounty heads, accent recolours, and small-font labels run on this computer. A model is optional."
      />

      <div className="chips">
        {PRESETS.map((preset) => (
          <button
            key={preset.id}
            type="button"
            className={instruction === preset.instruction ? 'chip active' : 'chip'}
            onClick={() => setInstruction(preset.instruction)}
          >
            {preset.label}
          </button>
        ))}
        <button
          type="button"
          className="chip"
          onClick={() => {
            setSource(BOUNTY_EXAMPLE);
            setInstruction(PRESETS[0].instruction);
          }}
        >
          Load bounty example
        </button>
      </div>

      <label className="field instruction">
        <span>Instruction</span>
        <input
          value={instruction}
          onChange={(event) => setInstruction(event.target.value)}
          onKeyDown={(event) => {
            if ((event.metaKey || event.ctrlKey) && event.key === 'Enter') {
              event.preventDefault();
              void generate();
            }
          }}
          placeholder="Apply bounty head style, recolour accents, or change name to …"
        />
      </label>

      <div className="split tall">
        <label className="field">
          <span>Config</span>
          <textarea
            className="textarea code tall"
            value={source}
            onChange={(event) => setSource(event.target.value)}
            spellCheck={false}
          />
        </label>
        <div className="field">
          <div className="panel-head inline">
            <span>
              Output
              {engine === 'local' ? <em className="badge">Local</em> : null}
              {engine === 'model' ? <em className="badge">Model</em> : null}
            </span>
            <CopyButton text={output} />
          </div>
          <textarea className="textarea code tall" value={output} onChange={(event) => setOutput(event.target.value)} spellCheck={false} />
        </div>
      </div>

      <div className="page-actions">
        <button type="button" className="btn" disabled={busy || !source.trim()} onClick={() => void generate()}>
          {busy ? 'Generating…' : 'Generate'}
        </button>
        <span className="muted">Ctrl or Cmd + Enter</span>
      </div>

      {notes.length > 0 ? (
        <ul className="notes">
          {notes.map((note) => (
            <li key={note}>{note}</li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}

async function completeFromBrowser(payload: {
  baseUrl: string;
  apiKey: string;
  model: string;
  instruction: string;
  yaml: string;
  accent: string;
  secondary: string;
}): Promise<string> {
  const url = new URL(payload.baseUrl);
  if (url.protocol !== 'http:' && url.protocol !== 'https:') throw new Error('API base URL must be http or https');
  const endpoint = new URL('chat/completions', url.href.endsWith('/') ? url.href : `${url.href}/`);
  const response = await fetch(endpoint, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${payload.apiKey}`,
    },
    body: JSON.stringify({
      model: payload.model,
      temperature: 0.2,
      messages: [
        {
          role: 'system',
          content:
            'You rewrite Minecraft YAML configs. Return only YAML. For bounty item lore use small-font bracket labels, a coloured Description with pipe lines, &# hex mixed with &f and &7, and preserve every placeholder.',
        },
        {
          role: 'user',
          content: `Accent: ${payload.accent}\nHighlight: ${payload.secondary}\nInstruction: ${payload.instruction}\n\nConfig:\n${payload.yaml}`,
        },
      ],
    }),
  });
  if (!response.ok) throw new Error(`Model request failed (${response.status})`);
  const parsed = (await response.json()) as { choices?: { message?: { content?: string } }[] };
  const content = parsed.choices?.[0]?.message?.content;
  if (!content) throw new Error('Model returned an empty response');
  return content;
}
