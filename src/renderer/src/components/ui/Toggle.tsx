export function Toggle({
  checked,
  onChange,
  label,
  hint
}: {
  checked: boolean
  onChange: (next: boolean) => void
  label: string
  hint?: string
}) {
  return (
    <div className="toggle">
      <div>
        <div>{label}</div>
        {hint ? <div className="hint">{hint}</div> : null}
      </div>
      <button type="button" className={`switch ${checked ? 'on' : ''}`} onClick={() => onChange(!checked)} aria-pressed={checked}>
        <i />
      </button>
    </div>
  )
}
