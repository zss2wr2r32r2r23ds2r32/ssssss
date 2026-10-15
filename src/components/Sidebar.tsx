import { IconAi, IconColour, IconEmoji, IconFont, IconSettings } from './Icons';

export type PageId = 'emojis' | 'small-font' | 'colour' | 'ai-config' | 'settings';

const ITEMS: { id: PageId; label: string; icon: typeof IconEmoji }[] = [
  { id: 'emojis', label: 'Emojis', icon: IconEmoji },
  { id: 'small-font', label: 'Small Font', icon: IconFont },
  { id: 'colour', label: 'Colour', icon: IconColour },
  { id: 'ai-config', label: 'AI Config', icon: IconAi },
];

export function Sidebar({ page, onNavigate }: { page: PageId; onNavigate: (page: PageId) => void }) {
  return (
    <aside className="sidebar">
      <div className="brand">
        <span className="brand-mark" aria-hidden="true">
          A
        </span>
        <div>
          <div className="brand-name">Avix Studios</div>
          <div className="brand-sub">Minecraft configs</div>
        </div>
      </div>
      <nav className="nav" aria-label="Sections">
        {ITEMS.map((item) => {
          const Icon = item.icon;
          const active = page === item.id;
          return (
            <button
              key={item.id}
              type="button"
              className={active ? 'nav-item active' : 'nav-item'}
              aria-current={active ? 'page' : undefined}
              onClick={() => onNavigate(item.id)}
            >
              <Icon />
              <span>{item.label}</span>
            </button>
          );
        })}
        <div className="nav-spacer" />
        <button
          type="button"
          className={page === 'settings' ? 'nav-item active' : 'nav-item'}
          aria-current={page === 'settings' ? 'page' : undefined}
          onClick={() => onNavigate('settings')}
        >
          <IconSettings />
          <span>Settings</span>
        </button>
      </nav>
    </aside>
  );
}
