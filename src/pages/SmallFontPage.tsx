import { useMemo, useState } from 'react';
import { CopyButton } from '../components/CopyButton';
import { PageHeader } from '../components/PageHeader';
import { smallCapsEntries, toSmallFont } from '../lib/smallfont';

export function SmallFontPage() {
  const [text, setText] = useState('turn text like this');
  const [keepPlaceholders, setKeepPlaceholders] = useState(false);
  const output = useMemo(
    () => toSmallFont(text, { preservePlaceholders: keepPlaceholders }),
    [text, keepPlaceholders],
  );
  const entries = smallCapsEntries();

  return (
    <div className="page">
      <PageHeader
        kicker="Labels"
        title="Small Font"
        description="Turns letters into the phonetic small-caps used in bounty tags and menu titles. Spaces stay spaces. S and X stay Latin, which is the ᴛʜɪs style."
      />

      <div className="split">
        <label className="field">
          <span>Text</span>
          <textarea className="textarea" value={text} onChange={(event) => setText(event.target.value)} rows={8} />
        </label>
        <div className="field">
          <div className="panel-head inline">
            <span>Preview</span>
            <CopyButton text={output} />
          </div>
          <div className="preview-chat" aria-live="polite">
            <p className="preview-line">{output || ' '}</p>
          </div>
          <label className="check">
            <input
              type="checkbox"
              checked={keepPlaceholders}
              onChange={(event) => setKeepPlaceholders(event.target.checked)}
            />
            <span>Keep %placeholders% and {'{tokens}'} as typed</span>
          </label>
        </div>
      </div>

      <section className="panel mapping-panel">
        <div className="panel-head">
          <h2>Alphabet</h2>
          <p>A–Z and a–z share one map. Anything else, including digits and punctuation, is copied through.</p>
        </div>
        <div className="mapping">
          {entries.map((entry) => (
            <div key={entry.from} className="map-cell">
              <span>{entry.from}</span>
              <b>{entry.to}</b>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
