import type { SVGProps } from 'react'

type IconProps = SVGProps<SVGSVGElement>

const base = {
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.7,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const
}

export function CompassIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" {...base} {...props}>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 4.5v2M12 17.5v2M4.5 12h2M17.5 12h2" />
      <path d="m9.2 14.8 2.1-5.6 5.5 2.2-2.2 5.4z" />
    </svg>
  )
}

export function CrosshairIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" {...base} {...props}>
      <circle cx="12" cy="12" r="3.2" />
      <path d="M12 3.5v5M12 15.5v5M3.5 12h5M15.5 12h5" />
    </svg>
  )
}

export function DisplayIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" {...base} {...props}>
      <rect x="3.5" y="4.5" width="17" height="11.5" rx="2" />
      <path d="M8 19.5h8M12 16v3.5" />
      <path d="M7 9h4M7 11.5h2" />
    </svg>
  )
}

export function GaugeIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" {...base} {...props}>
      <path d="M5 17a8 8 0 1 1 14 0" />
      <path d="M12 17l4-5" />
      <circle cx="12" cy="17" r="1.2" fill="currentColor" stroke="none" />
    </svg>
  )
}

export function KeyIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" {...base} {...props}>
      <rect x="4" y="6.5" width="16" height="11" rx="2.2" />
      <path d="M8 12h8M9.5 15h5" />
    </svg>
  )
}

export function GearIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" {...base} {...props}>
      <circle cx="12" cy="12" r="3" />
      <path d="M12 4.5v2.2M12 17.3V19.5M4.5 12h2.2M17.3 12H19.5M6.4 6.4l1.6 1.6M16 16l1.6 1.6M17.6 6.4 16 8M8 16l-1.6 1.6" />
    </svg>
  )
}

export function DiscordIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" {...base} {...props}>
      <path d="M7.2 6.8c2.4-1 4.8-1 7.2 0M6 16.6c.8.8 3.2 1.6 6 1.6s5.2-.8 6-1.6" />
      <path d="M8.2 8.6C6.4 11 6 13.6 6 16.2c1.4.8 2.8 1.2 6 1.2s4.6-.4 6-1.2c0-2.6-.4-5.2-2.2-7.6" />
      <circle cx="9.4" cy="13" r="1" fill="currentColor" stroke="none" />
      <circle cx="14.6" cy="13" r="1" fill="currentColor" stroke="none" />
    </svg>
  )
}

export function MinimizeIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" width="14" height="14" {...base} {...props}>
      <path d="M6 12h12" />
    </svg>
  )
}

export function CloseIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" width="14" height="14" {...base} {...props}>
      <path d="m7 7 10 10M17 7 7 17" />
    </svg>
  )
}

export function MaximizeIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" width="14" height="14" {...base} {...props}>
      <rect x="7" y="7" width="10" height="10" rx="1.4" />
    </svg>
  )
}

export function BellIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" {...base} {...props}>
      <path d="M6 16h12l-1.2-2.1V11a4.8 4.8 0 1 0-9.6 0v2.9z" />
      <path d="M10 17.5a2 2 0 0 0 4 0" />
    </svg>
  )
}

export function LaunchIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" width="22" height="22" {...base} {...props}>
      <path d="M5 19c5-1 8-6 9-11 4-1 6 2 6 5-4 2-10 4-15 6z" />
      <path d="M9 15l-2 4" />
    </svg>
  )
}

export function FolderIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" {...base} {...props}>
      <path d="M4 8.5V18a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V10a2 2 0 0 0-2-2h-6.2L10 6H6a2 2 0 0 0-2 2.5z" />
    </svg>
  )
}

export function ShieldIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" {...base} {...props}>
      <path d="M12 4 6 6.5v6.2c0 3.8 2.5 6.3 6 7.8 3.5-1.5 6-4 6-7.8V6.5z" />
    </svg>
  )
}
