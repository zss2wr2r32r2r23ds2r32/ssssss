import type { NavPage } from '../../../../shared/types'
import logo from '../../assets/logo.png'
import { api } from '../../lib/api'
import { useApp } from '../../store/AppState'
import { BellIcon, CompassIcon, CrosshairIcon, DiscordIcon, DisplayIcon, GaugeIcon, GearIcon, KeyIcon } from '../icons/Icons'

const ITEMS: Array<{ id: NavPage; label: string; icon: typeof CompassIcon }> = [
  { id: 'home', label: 'Home', icon: CompassIcon },
  { id: 'crosshair', label: 'Crosshair', icon: CrosshairIcon },
  { id: 'resolution', label: 'Resolution', icon: DisplayIcon },
  { id: 'performance', label: 'Performance', icon: GaugeIcon },
  { id: 'macro', label: 'Macro', icon: KeyIcon },
  { id: 'scrims', label: 'Scrim alert', icon: BellIcon }
]

export function Sidebar() {
  const { page, setPage, config, pushToast } = useApp()

  return (
    <aside className="sidebar">
      <div className="brand-mark" title="Avix">
        <img src={logo} alt="Avix" />
      </div>
      <nav className="nav">
        {ITEMS.map((item) => {
          const Icon = item.icon
          return (
            <button
              key={item.id}
              type="button"
              title={item.label}
              className={`nav-btn ${page === item.id ? 'active' : ''}`}
              onClick={() => setPage(item.id)}
            >
              <Icon />
            </button>
          )
        })}
      </nav>
      <div className="sidebar-foot">
        <button
          type="button"
          className="nav-btn"
          title="Discord"
          onClick={async () => {
            const url = config?.general.discordUrl ?? 'https://discord.com/invite/fortnite'
            const result = await api.openExternal(url)
            pushToast({ tone: result.ok ? 'info' : 'error', title: 'Discord', body: result.message })
          }}
        >
          <DiscordIcon />
        </button>
        <button type="button" className="nav-btn" title="Settings" onClick={() => setPage('settings')}>
          <GearIcon />
        </button>
      </div>
    </aside>
  )
}
