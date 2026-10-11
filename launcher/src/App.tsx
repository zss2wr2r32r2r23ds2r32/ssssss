import { AnimatePresence, motion } from 'framer-motion';
import { useEffect, useState } from 'react';
import { playClick } from './audio';
import { Arrival } from './components/Arrival';
import { Sidebar } from './components/Sidebar';
import { TitleBar } from './components/TitleBar';
import { motionTransition, reducedMotion } from './format';
import { DonatePage } from './pages/DonatePage';
import { HomePage } from './pages/HomePage';
import { LeaderboardPage } from './pages/LeaderboardPage';
import { LibraryPage } from './pages/LibraryPage';
import { LoginPage } from './pages/LoginPage';
import { SettingsPage } from './pages/SettingsPage';
import { ShopPage } from './pages/ShopPage';
import { useSession } from './session';
import type { SettingsTab, Tab } from './types';

const TABS: Tab[] = ['home', 'library', 'shop', 'leaderboards', 'donate', 'settings'];
const SETTINGS: SettingsTab[] = ['account', 'game', 'launcher'];

function parseHash() {
  const raw = window.location.hash.replace(/^#\/?/, '');
  const [head, sub] = raw.split('/');
  const tab = TABS.includes(head as Tab) ? (head as Tab) : 'home';
  const settings = SETTINGS.includes(sub as SettingsTab) ? (sub as SettingsTab) : 'account';
  return { tab, settings };
}

export function App() {
  const { user, booting, toastMessage, arriving, finishArrival } = useSession();
  const [tab, setTab] = useState<Tab>('home');
  const [settingsTab, setSettingsTab] = useState<SettingsTab>('account');

  useEffect(() => {
    const sync = () => {
      const parsed = parseHash();
      setTab(parsed.tab);
      setSettingsTab(parsed.settings);
    };
    sync();
    window.addEventListener('hashchange', sync);
    return () => window.removeEventListener('hashchange', sync);
  }, []);

  useEffect(() => {
    const lockImages = () => {
      document.querySelectorAll('img').forEach((img) => {
        img.draggable = false;
      });
    };
    const blockDrag = (event: DragEvent) => {
      const target = event.target;
      if (target instanceof Element && target.closest('input, textarea, select')) return;
      event.preventDefault();
    };
    lockImages();
    const observer = new MutationObserver(lockImages);
    observer.observe(document.body, { childList: true, subtree: true });
    const blockInspect = (event: MouseEvent) => {
      event.preventDefault();
    };
    const blockKeys = (event: KeyboardEvent) => {
      const key = event.key.toLowerCase();
      const inspect =
        event.key === 'F12' ||
        (event.ctrlKey && event.shiftKey && (key === 'i' || key === 'j' || key === 'c')) ||
        (event.ctrlKey && !event.shiftKey && key === 'u') ||
        (event.metaKey && event.altKey && key === 'i');
      if (!inspect) return;
      event.preventDefault();
      event.stopPropagation();
    };
    document.addEventListener('dragstart', blockDrag);
    document.addEventListener('contextmenu', blockInspect);
    window.addEventListener('keydown', blockKeys, true);
    return () => {
      observer.disconnect();
      document.removeEventListener('dragstart', blockDrag);
      document.removeEventListener('contextmenu', blockInspect);
      window.removeEventListener('keydown', blockKeys, true);
    };
  }, []);

  function go(next: Tab, settings?: SettingsTab) {
    playClick();
    const sub = settings || settingsTab;
    const hash = next === 'settings' ? `#/settings/${sub}` : `#/${next}`;
    if (window.location.hash !== hash) window.location.hash = hash;
  }

  let body = <div className="boot">Loading Nexa…</div>;
  if (!booting && !user) body = <LoginPage />;
  if (!booting && user && arriving) body = <Arrival user={user} onDone={finishArrival} />;
  if (!booting && user && !arriving) {
    body = (
      <div className="workspace">
        <Sidebar tab={tab} onTab={go} />
        <div className="stage">
          <AnimatePresence mode="wait">
            <motion.div
              key={tab}
              className="page-wrap"
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -8 }}
              transition={reducedMotion() ? { duration: 0 } : motionTransition}
            >
              {tab === 'home' ? <HomePage /> : null}
              {tab === 'library' ? <LibraryPage /> : null}
              {tab === 'shop' ? <ShopPage /> : null}
              {tab === 'leaderboards' ? <LeaderboardPage /> : null}
              {tab === 'donate' ? <DonatePage /> : null}
              {tab === 'settings' ? <SettingsPage tab={settingsTab} onTab={(next) => go('settings', next)} /> : null}
            </motion.div>
          </AnimatePresence>
        </div>
      </div>
    );
  }

  return (
    <div className={`shell${user && tab === 'library' ? ' is-library' : ''}`}>
      <TitleBar />
      {body}
      <AnimatePresence>
        {toastMessage ? (
          <motion.div
            key={toastMessage}
            className="toast"
            role="status"
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 8 }}
          >
            {toastMessage}
          </motion.div>
        ) : null}
      </AnimatePresence>
    </div>
  );
}
