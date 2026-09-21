type IconProps = { className?: string };

export function IconEmoji({ className }: IconProps) {
  return (
    <svg className={className} viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="12" r="8" />
      <path d="M8.6 13.3c.85 1.35 2 2 3.4 2s2.55-.65 3.4-2" />
      <circle cx="9" cy="10" r="0.85" fill="currentColor" stroke="none" />
      <circle cx="15" cy="10" r="0.85" fill="currentColor" stroke="none" />
    </svg>
  );
}

export function IconFont({ className }: IconProps) {
  return (
    <svg className={className} viewBox="0 0 24 24" aria-hidden="true">
      <path d="M3.8 17.5 8 6.2 12.2 17.5" />
      <path d="M5.3 13.6h5.4" />
      <path d="M15.2 17.4c1.9 0 3.2-1 3.2-2.4 0-1.2-.9-2-2.2-2.2 1-.3 1.7-1.1 1.7-2 0-1.3-1.1-2.2-2.8-2.2" />
    </svg>
  );
}

export function IconColour({ className }: IconProps) {
  return (
    <svg className={className} viewBox="0 0 24 24" aria-hidden="true">
      <path d="M14.2 4.8 19.2 9.8" />
      <path d="M12.4 6.8 17.2 11.6 13 15.8a3.3 3.3 0 0 1-4.7-4.7l4.1-4.3Z" />
      <path d="M8.2 16.2 5 19.4" />
    </svg>
  );
}

export function IconAi({ className }: IconProps) {
  return (
    <svg className={className} viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 3.2 13.35 8.1 18.2 9.4 13.35 10.75 12 15.6 10.65 10.75 5.8 9.4 10.65 8.1Z" />
      <path d="M17.6 14.2 18.35 16.4 20.6 17.15 18.35 17.9 17.6 20.1 16.85 17.9 14.6 17.15 16.85 16.4Z" />
    </svg>
  );
}

export function IconSettings({ className }: IconProps) {
  return (
    <svg className={className} viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="12" r="3" />
      <path d="M12 3.4v2.3M12 18.3v2.3M3.4 12h2.3M18.3 12h2.3M5.8 5.8l1.6 1.6M16.6 16.6l1.6 1.6M18.2 5.8l-1.6 1.6M7.4 16.6l-1.6 1.6" />
    </svg>
  );
}
