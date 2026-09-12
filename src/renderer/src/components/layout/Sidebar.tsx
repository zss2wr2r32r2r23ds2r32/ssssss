import { APP_VERSION, type NavPage } from '../../../../shared/types'
import { api } from '../../lib/api'
import { useApp } from '../../store/AppState'
import { CompassIcon, CrosshairIcon, DiscordIcon, DisplayIcon, GaugeIcon, GearIcon, KeyIcon } from '../icons/Icons'

const ITEMS: Array<{ id: NavPage; label: string; icon: typeof CompassIcon }> = [
  { id: 'home', label: 'Home', icon: CompassIcon },
  { id: 'crosshair', label: 'Crosshair', icon: CrosshairIcon },
  { id: 'resolution', label: 'Resolution', icon: DisplayIcon },
  { id: 'performance', label: 'Performance', icon: GaugeIcon },
  { id: 'macro', label: 'Macro', icon: KeyIcon },
  { id: 'settings', label: 'Settings', icon: GearIcon }
]

export function Sidebar() {
  const { page, setPage, config, pushToast } = useApp()

  return (
    <aside className="sidebar">
      <div className="brand">
        <div className="brand-mark">
          <CompassIcon width={22} height={22} />
        </div>
        <div>
          <h1>NAUTICAL</h1>
          <p>Fortnite launcher</p>
        </div>
      </div>
      <nav className="nav">
        {ITEMS.map((item) => {
          const Icon = item.icon
          return (
            <button
              key={item.id}
              type="button"
              className={`nav-btn ${page === item.id ? 'active' : ''}`}
              onClick={() => setPage(item.id)}
            >
              <Icon />
              {item.label}
            </button>
          )
        })}
      </nav>
      <div className="sidebar-foot">
        <button
          type="button"
          className="btn"
          onClick={async () => {
            const url = config?.general.discordUrl ?? 'https://discord.com/invite/fortnite'
            const result = await api.openExternal(url)
            pushToast({ tone: result.ok ? 'info' : 'error', title: 'Discord', body: result.message })
          }}
        >
          <DiscordIcon />
          Discord
        </button>
        <div className="hint">Version {APP_VERSION}</div>
      </div>
    </aside>
  )
}
