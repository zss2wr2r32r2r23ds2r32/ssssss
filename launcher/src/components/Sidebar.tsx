import { IconDonate, IconDownloads, IconHome, IconRanks, IconSettings, IconShop } from './Icons';
import type { Tab } from '../types';

const items: { id: Tab; label: string; icon: JSX.Element }[] = [
  { id: 'home', label: 'Home', icon: <IconHome /> },
  { id: 'library', label: 'Downloads', icon: <IconDownloads /> },
  { id: 'shop', label: 'Item Shop', icon: <IconShop /> },
  { id: 'leaderboards', label: 'Leaderboards', icon: <IconRanks /> },
  { id: 'donate', label: 'Donate', icon: <IconDonate /> },
];

export function Sidebar({ tab, onTab }: { tab: Tab; onTab: (tab: Tab) => void }) {
  return (
    <nav className="sidebar" aria-label="Launcher">
      {items.map((item) => (
        <button
          key={item.id}
          type="button"
          className={tab === item.id ? 'nav-btn active' : 'nav-btn'}
          aria-label={item.label}
          aria-current={tab === item.id ? 'page' : undefined}
          onClick={() => onTab(item.id)}
        >
          {item.icon}
          <span className="tip">{item.label}</span>
        </button>
      ))}
      <div className="spacer" />
      <button
        type="button"
        className={tab === 'settings' ? 'nav-btn active' : 'nav-btn'}
        aria-label="Settings"
        aria-current={tab === 'settings' ? 'page' : undefined}
        onClick={() => onTab('settings')}
      >
        <IconSettings />
        <span className="tip">Settings</span>
      </button>
    </nav>
  );
}
