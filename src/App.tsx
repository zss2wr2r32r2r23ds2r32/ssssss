import { useCallback, useEffect, useState } from 'react';
import { Sidebar, type PageId } from './components/Sidebar';
import { AIConfigPage } from './pages/AIConfigPage';
import { ColourPage } from './pages/ColourPage';
import { EmojisPage } from './pages/EmojisPage';
import { SettingsPage } from './pages/SettingsPage';
import { SmallFontPage } from './pages/SmallFontPage';
import { loadState, saveState, type PersistedState } from './lib/storage';
import { AppStateProvider, useApp } from './state';

const TITLES: Record<PageId, string> = {
  emojis: 'Emojis',
  'small-font': 'Small Font',
  colour: 'Colour',
  'ai-config': 'AI Config',
  settings: 'Settings',
};

export function App() {
  const [ready, setReady] = useState(false);
  const [persisted, setPersisted] = useState<PersistedState | null>(null);
  const [page, setPage] = useState<PageId>('emojis');

  useEffect(() => {
    let cancelled = false;
    loadState().then((state) => {
      if (cancelled) return;
      setPersisted(state);
      setReady(true);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!ready || !persisted) return;
    const timer = window.setTimeout(() => {
      void saveState(persisted);
    }, 120);
    return () => window.clearTimeout(timer);
  }, [persisted, ready]);

  useEffect(() => {
    document.title = `Avix Studios — ${TITLES[page]}`;
  }, [page]);

  const onChange = useCallback((state: PersistedState) => {
    setPersisted(state);
  }, []);

  if (!ready || !persisted) {
    return (
      <div className="boot">
        <span className="brand-mark">A</span>
      </div>
    );
  }

  return (
    <AppStateProvider initial={persisted} onChange={onChange}>
      <Shell page={page} onNavigate={setPage} />
    </AppStateProvider>
  );
}

function Shell({ page, onNavigate }: { page: PageId; onNavigate: (page: PageId) => void }) {
  const { toasts } = useApp();
  return (
    <div className="app">
      <Sidebar page={page} onNavigate={onNavigate} />
      <main className="main" data-page={page}>
        {page === 'emojis' ? <EmojisPage /> : null}
        {page === 'small-font' ? <SmallFontPage /> : null}
        {page === 'colour' ? <ColourPage /> : null}
        {page === 'ai-config' ? <AIConfigPage /> : null}
        {page === 'settings' ? <SettingsPage /> : null}
      </main>
      <div className="toast-wrap" aria-live="polite">
        {toasts.map((item) => (
          <div key={item.id} className="toast">
            {item.text}
          </div>
        ))}
      </div>
    </div>
  );
}
