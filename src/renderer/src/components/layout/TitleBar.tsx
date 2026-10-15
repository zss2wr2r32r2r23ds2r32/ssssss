import { APP_NAME } from '../../../../shared/types'
import { api } from '../../lib/api'
import { useApp } from '../../store/AppState'
import { CloseIcon, MaximizeIcon, MinimizeIcon } from '../icons/Icons'

const STATUS_LABEL: Record<string, string> = {
  NOT_RUNNING: 'Not running',
  LAUNCHING: 'Launching',
  RUNNING: 'Running',
  CLOSING: 'Closing',
  CLOSED: 'Closed'
}

const STATUS_DOT: Record<string, string> = {
  NOT_RUNNING: '',
  LAUNCHING: 'warn',
  RUNNING: 'run',
  CLOSING: 'warn',
  CLOSED: ''
}

export function TitleBar() {
  const { status } = useApp()
  return (
    <header className="titlebar">
      <div className="row">
        <strong style={{ letterSpacing: '0.12em', fontSize: 12 }}>{APP_NAME}</strong>
        <span className="pill no-drag">
          <i className={`dot ${STATUS_DOT[status]}`} />
          Fortnite · {STATUS_LABEL[status]}
        </span>
      </div>
      <div className="window-controls no-drag">
        <button type="button" onClick={() => void api.minimize()} aria-label="Minimize">
          <MinimizeIcon />
        </button>
        <button type="button" onClick={() => void api.maximize()} aria-label="Maximize">
          <MaximizeIcon />
        </button>
        <button type="button" className="close" onClick={() => void api.close()} aria-label="Close">
          <CloseIcon />
        </button>
      </div>
    </header>
  )
}
