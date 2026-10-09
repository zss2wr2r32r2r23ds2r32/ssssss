import { IconClose, IconMax, IconMin } from './Icons';

export function TitleBar() {
  return (
    <header className="titlebar">
      <div className="titlebar-brand">
        <img src="/logo.png" alt="" className="titlebar-logo" />
        <span>Nexa</span>
      </div>
      <div className="titlebar-controls">
        <button type="button" className="win" aria-label="Minimize" title="Minimize" onClick={() => window.nexa?.minimize()}>
          <IconMin />
        </button>
        <button type="button" className="win" aria-label="Maximize" title="Maximize" onClick={() => window.nexa?.maximize()}>
          <IconMax />
        </button>
        <button type="button" className="win close" aria-label="Close" title="Close" onClick={() => window.nexa?.close()}>
          <IconClose />
        </button>
      </div>
    </header>
  );
}
