import { useEffect, useMemo, useState } from 'react';
import { CopyButton } from '../components/CopyButton';
import { PageHeader } from '../components/PageHeader';
import {
  FORMAT_META,
  GRADIENT_PRESETS,
  buildFormats,
  previewColors,
  type Decor,
  type FormatId,
} from '../lib/colour';
import { normalizeHex } from '../lib/hex';
import { useApp } from '../state';

const EMPTY_DECOR: Decor = { bold: false, italic: false, underline: false, strike: false };

export function ColourPage() {
  const { settings } = useApp();
  const [text, setText] = useState('Avix Studios');
  const [stops, setStops] = useState(['#ff0000', '#ff8a80']);
  const [skipSpaces, setSkipSpaces] = useState(true);
  const [decor, setDecor] = useState<Decor>(EMPTY_DECOR);
  const [tab, setTab] = useState<FormatId | 'all'>('all');
  const [drafts, setDrafts] = useState<Record<FormatId, string>>(() =>
    buildFormats('Avix Studios', ['#ff0000', '#ff8a80'], true, EMPTY_DECOR),
  );

  const generated = useMemo(() => buildFormats(text, stops, skipSpaces, decor), [text, stops, skipSpaces, decor]);
  useEffect(() => {
    setDrafts(generated);
  }, [generated]);

  const preview = previewColors(text, stops.length ? stops : ['#ff0000'], skipSpaces);
  const chars = Array.from(text);
  const shown = tab === 'all' ? FORMAT_META : FORMAT_META.filter((format) => format.id === tab);
  const bar = `linear-gradient(90deg, ${stops.map((stop) => normalizeHex(stop)).join(', ')})`;

  function updateStop(index: number, value: string) {
    setStops((current) => current.map((stop, stopIndex) => (stopIndex === index ? value : stop)));
  }

  function addStop() {
    setStops((current) => {
      if (current.length >= 8) return current;
      const extras = ['#ffffff', '#ffaa00', '#55ffff', '#ff55ff', '#94ff00', '#aa00aa'];
      const next = extras.find((colour) => !current.includes(colour)) ?? '#ffffff';
      return [...current, next];
    });
  }

  return (
    <div className="page">
      <PageHeader
        kicker="Birdflop style"
        title="Colour"
        description="Paint a string with a hex gradient and copy it as &# codes, legacy hex, MiniMessage, or the nearest classic colour."
      />

      <div className="panel">
        <label className="field">
          <span>Text</span>
          <textarea className="textarea short" value={text} onChange={(event) => setText(event.target.value)} rows={3} />
        </label>

        <div className="stops-head">
          <h2>Colour stops</h2>
          <button type="button" className="btn secondary" onClick={addStop} disabled={stops.length >= 8}>
            Add stop
          </button>
        </div>
        <div className="stops">
          {stops.map((stop, index) => (
            <div className="stop" key={`${index}-${stops.length}`}>
              <input
                type="color"
                aria-label={`Stop ${index + 1} colour`}
                value={normalizeHex(stop)}
                onChange={(event) => updateStop(index, event.target.value)}
              />
              <input
                value={stop}
                aria-label={`Stop ${index + 1} hex`}
                onChange={(event) => updateStop(index, event.target.value)}
                onBlur={() => updateStop(index, normalizeHex(stop))}
                spellCheck={false}
              />
              <button
                type="button"
                className="btn ghost"
                aria-label={`Remove stop ${index + 1}`}
                disabled={stops.length <= 1}
                onClick={() => setStops((current) => current.filter((_, stopIndex) => stopIndex !== index))}
              >
                ×
              </button>
            </div>
          ))}
        </div>
        <div className="gradient-bar" style={{ background: bar }} />
        <div className="chips">
          {GRADIENT_PRESETS.map((preset) => (
            <button key={preset.name} type="button" className="chip" onClick={() => setStops(preset.stops)}>
              {preset.name}
            </button>
          ))}
          <button type="button" className="chip" onClick={() => setStops([settings.accent, settings.secondary])}>
            Settings accent
          </button>
        </div>

        <div className="toggles">
          <label className="check">
            <input type="checkbox" checked={skipSpaces} onChange={(event) => setSkipSpaces(event.target.checked)} />
            <span>Skip spaces</span>
          </label>
          {(
            [
              ['bold', 'Bold'],
              ['italic', 'Italic'],
              ['underline', 'Underline'],
              ['strike', 'Strike'],
            ] as const
          ).map(([key, label]) => (
            <label key={key} className="check">
              <input
                type="checkbox"
                checked={decor[key]}
                onChange={(event) => setDecor((current) => ({ ...current, [key]: event.target.checked }))}
              />
              <span>{label}</span>
            </label>
          ))}
        </div>
      </div>

      <section className="panel preview-panel">
        <div className="panel-head">
          <h2>Preview</h2>
          <p>What the gradient reads as. Formatting codes are in the outputs below.</p>
        </div>
        <div className="preview-chat">
          <p className="preview-line">
            {chars.length === 0
              ? ' '
              : chars.map((char, index) => (
                  <span
                    key={`${index}-${char}`}
                    style={{
                      color: preview[index] ?? '#ffffff',
                      fontWeight: decor.bold ? 700 : 500,
                      fontStyle: decor.italic ? 'italic' : 'normal',
                      textDecoration: `${decor.underline ? 'underline' : ''} ${decor.strike ? 'line-through' : ''}`.trim(),
                    }}
                  >
                    {char}
                  </span>
                ))}
          </p>
        </div>
      </section>

      <div className="chips" role="tablist" aria-label="Formats">
        <button type="button" className={tab === 'all' ? 'chip active' : 'chip'} onClick={() => setTab('all')}>
          All formats
        </button>
        {FORMAT_META.map((format) => (
          <button
            key={format.id}
            type="button"
            className={tab === format.id ? 'chip active' : 'chip'}
            onClick={() => setTab(format.id)}
          >
            {format.label}
          </button>
        ))}
      </div>

      <div className="format-list">
        {shown.map((format) => (
          <section key={format.id} className="panel">
            <div className="panel-head inline">
              <div>
                <h2>{format.label}</h2>
                <p>{format.hint}</p>
              </div>
              <CopyButton text={drafts[format.id] ?? ''} />
            </div>
            <textarea
              className="textarea code"
              value={drafts[format.id] ?? ''}
              onChange={(event) => setDrafts((current) => ({ ...current, [format.id]: event.target.value }))}
              spellCheck={false}
              rows={tab === 'all' ? 3 : 6}
            />
          </section>
        ))}
      </div>
    </div>
  );
}
