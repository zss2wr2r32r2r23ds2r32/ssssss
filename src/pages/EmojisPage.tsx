import { useMemo, useState, type FormEvent } from 'react';
import { CopyButton } from '../components/CopyButton';
import { PageHeader } from '../components/PageHeader';
import { EMOJI_CATALOG, EMOJI_CATEGORIES, filterEmojis } from '../lib/emojis';
import { copyText } from '../lib/clipboard';
import type { CustomEmoji } from '../lib/storage';
import { useApp } from '../state';

type Listed = {
  id: string;
  char: string;
  name: string;
  category: string;
  keywords?: string;
  custom?: boolean;
};

export function EmojisPage() {
  const { customEmojis, addEmoji, removeEmoji, toast } = useApp();
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState('All');
  const [char, setChar] = useState('');
  const [name, setName] = useState('');
  const [customCategory, setCustomCategory] = useState('Custom');
  const [copiedId, setCopiedId] = useState('');

  const entries = useMemo<Listed[]>(() => {
    const custom: Listed[] = customEmojis.map((emoji) => ({ ...emoji, custom: true, keywords: 'custom' }));
    return [...custom, ...EMOJI_CATALOG];
  }, [customEmojis]);

  const visible = useMemo(() => filterEmojis(entries, query, category), [entries, query, category]);
  const categories = ['All', ...EMOJI_CATEGORIES];

  function copySymbol(entry: Listed) {
    copyText(entry.char)
      .then(() => {
        setCopiedId(entry.id);
        toast(`Copied ${entry.char}`);
        window.setTimeout(() => setCopiedId((current) => (current === entry.id ? '' : current)), 700);
      })
      .catch(() => toast("Couldn't copy"));
  }

  function onAdd(event: FormEvent) {
    event.preventDefault();
    const symbol = Array.from(char.trim()).slice(0, 16).join('');
    if (!symbol) {
      toast('Enter a character');
      return;
    }
    const emoji: CustomEmoji = {
      id: crypto.randomUUID(),
      char: symbol,
      name: name.trim() || 'Custom symbol',
      category: customCategory || 'Custom',
    };
    addEmoji(emoji);
    setChar('');
    setName('');
    toast('Custom symbol saved');
  }

  return (
    <div className="page">
      <PageHeader
        kicker="Catalogue"
        title="Emojis"
        description="Unicode symbols that hold up in Minecraft chat, scoreboards, and item lore. Click any symbol to copy it."
      />

      <form className="panel add-form" onSubmit={onAdd}>
        <div className="panel-head">
          <h2>Add custom emoji</h2>
          <p>Saved on this computer and still here after a restart.</p>
        </div>
        <div className="form-grid">
          <label className="field">
            <span>Character</span>
            <input value={char} onChange={(event) => setChar(event.target.value)} placeholder="✦" maxLength={16} />
          </label>
          <label className="field">
            <span>Name</span>
            <input value={name} onChange={(event) => setName(event.target.value)} placeholder="Optional name" />
          </label>
          <label className="field">
            <span>Category</span>
            <select value={customCategory} onChange={(event) => setCustomCategory(event.target.value)}>
              {EMOJI_CATEGORIES.map((item) => (
                <option key={item}>{item}</option>
              ))}
            </select>
          </label>
          <button type="submit" className="btn">
            Add
          </button>
        </div>
      </form>

      <div className="toolbar">
        <label className="field grow">
          <span className="sr-only">Search symbols</span>
          <input
            className="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search name, category, or paste a symbol"
          />
        </label>
        <CopyButton text={visible.map((entry) => entry.char).join(' ')} label="Copy visible" />
      </div>

      <div className="chips" role="tablist" aria-label="Categories">
        {categories.map((item) => (
          <button
            key={item}
            type="button"
            className={category === item ? 'chip active' : 'chip'}
            onClick={() => setCategory(item)}
          >
            {item}
          </button>
        ))}
      </div>

      <p className="muted count">
        {visible.length} symbol{visible.length === 1 ? '' : 's'}
      </p>

      {visible.length === 0 ? (
        <div className="empty">Nothing matches that search.</div>
      ) : (
        <div className="emoji-grid">
          {visible.map((entry) => (
            <div key={entry.id} className={copiedId === entry.id ? 'emoji-card flash' : 'emoji-card'}>
              {entry.custom ? (
                <button
                  type="button"
                  className="emoji-remove"
                  aria-label={`Remove ${entry.name}`}
                  onClick={() => {
                    removeEmoji(entry.id);
                    toast('Removed custom symbol');
                  }}
                >
                  ×
                </button>
              ) : null}
              <button type="button" className="emoji-hit" onClick={() => copySymbol(entry)}>
                <span className="emoji-char">{entry.char}</span>
                <span className="emoji-name">{entry.name}</span>
                <span className="emoji-cat">{entry.category}</span>
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
