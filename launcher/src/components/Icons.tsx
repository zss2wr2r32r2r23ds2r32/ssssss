interface IconProps {
  size?: number;
}

function base(size = 22) {
  return {
    width: size,
    height: size,
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: 1.7,
    strokeLinecap: 'round' as const,
    strokeLinejoin: 'round' as const,
    'aria-hidden': true,
  };
}

export function IconHome({ size }: IconProps) {
  return (
    <svg {...base(size)}>
      <path d="M4 11.2 12 4l8 7.2" />
      <path d="M7.2 10.4V20h9.6v-9.6" />
    </svg>
  );
}

export function IconDownloads({ size }: IconProps) {
  return (
    <svg {...base(size)}>
      <path d="M12 4v10" />
      <path d="m8 10 4 4 4-4" />
      <path d="M5 18.5h14" />
    </svg>
  );
}

export function IconShop({ size }: IconProps) {
  return (
    <svg {...base(size)}>
      <path d="M6 8h12l-1 11H7L6 8z" />
      <path d="M9 8V6.8A3 3 0 0 1 12 4a3 3 0 0 1 3 2.8V8" />
    </svg>
  );
}

export function IconRanks({ size }: IconProps) {
  return (
    <svg {...base(size)}>
      <path d="M5 19V10" />
      <path d="M12 19V5" />
      <path d="M19 19v-7" />
      <path d="M4 19h16" />
    </svg>
  );
}

export function IconDonate({ size }: IconProps) {
  return (
    <svg {...base(size)}>
      <path d="M12 19s-7-4.2-7-9a3.8 3.8 0 0 1 7-2 3.8 3.8 0 0 1 7 2c0 4.8-7 9-7 9z" />
    </svg>
  );
}

export function IconSettings({ size }: IconProps) {
  return (
    <svg {...base(size)}>
      <circle cx="12" cy="12" r="3" />
      <path d="M12 3.5v2.2M12 18.3v2.2M3.5 12h2.2M18.3 12h2.2M6 6l1.6 1.6M16.4 16.4 18 18M18 6l-1.6 1.6M7.6 16.4 6 18" />
    </svg>
  );
}

export function IconMin() {
  return (
    <svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true">
      <path d="M2 6.5h8" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
    </svg>
  );
}

export function IconMax() {
  return (
    <svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true">
      <rect x="2.2" y="2.2" width="7.6" height="7.6" rx="1" fill="none" stroke="currentColor" strokeWidth="1.3" />
    </svg>
  );
}

export function IconClose() {
  return (
    <svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true">
      <path d="M3 3l6 6M9 3 3 9" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
    </svg>
  );
}

export function IconPencil() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
      <path d="M9.2 3.2 12.8 6.8 6 13.6H2.4V10z" stroke="currentColor" strokeWidth="1.4" strokeLinejoin="round" />
      <path d="M8.2 4.2 11.8 7.8" stroke="currentColor" strokeWidth="1.4" />
    </svg>
  );
}

export function IconDiscord() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" aria-hidden="true">
      <path
        fill="currentColor"
        d="M5 7.5h11.2A2.8 2.8 0 0 1 19 10.3V15a2.8 2.8 0 0 1-2.8 2.8H10l-3.4 2.4v-2.4H5.8A2.8 2.8 0 0 1 3 15v-4.7A2.8 2.8 0 0 1 5.8 7.5H5z"
      />
    </svg>
  );
}

export function IconFolder() {
  return (
    <svg width="36" height="36" viewBox="0 0 36 36" fill="none" aria-hidden="true">
      <path d="M5 12.5V26a3 3 0 0 0 3 3h20a3 3 0 0 0 3-3V14a3 3 0 0 0-3-3H17l-2.4-2.6A3 3 0 0 0 12.4 7.5H8a3 3 0 0 0-3 3v2z" stroke="currentColor" strokeWidth="1.6" />
    </svg>
  );
}

export function IconCloud() {
  return (
    <svg width="36" height="36" viewBox="0 0 36 36" fill="none" aria-hidden="true">
      <path d="M12 25h12.5a5.5 5.5 0 0 0 .6-11 7 7 0 0 0-13.4 1.8A4.5 4.5 0 0 0 12 25z" stroke="currentColor" strokeWidth="1.6" />
      <path d="M18 20v6M15.5 23.5 18 26l2.5-2.5" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

export function IconType({ type }: { type: string }) {
  const props = { width: 16, height: 16, viewBox: '0 0 16 16', fill: 'none', 'aria-hidden': true as const };
  if (type === 'emote') {
    return (
      <svg {...props}>
        <circle cx="8" cy="8" r="5.2" stroke="white" strokeWidth="1.3" />
        <path d="M6 9.2c.5.8 1.2 1.1 2 1.1s1.5-.3 2-1.1" stroke="white" strokeWidth="1.2" strokeLinecap="round" />
        <path d="M6.2 6.6h.1M9.7 6.6h.1" stroke="white" strokeWidth="1.6" strokeLinecap="round" />
      </svg>
    );
  }
  if (type === 'pickaxe') {
    return (
      <svg {...props}>
        <path d="M3 12.5 11 4.5" stroke="white" strokeWidth="1.4" strokeLinecap="round" />
        <path d="M9.2 3.2h3.6v2.2L10.6 7.4" stroke="white" strokeWidth="1.3" strokeLinejoin="round" />
      </svg>
    );
  }
  if (type === 'glider') {
    return (
      <svg {...props}>
        <path d="M2 8c2.2-3 9.8-3 12 0-2 1.6-10 1.6-12 0z" stroke="white" strokeWidth="1.3" />
        <path d="M8 8.2v4.2" stroke="white" strokeWidth="1.3" strokeLinecap="round" />
      </svg>
    );
  }
  return (
    <svg {...props}>
      <circle cx="8" cy="5.2" r="2" stroke="white" strokeWidth="1.3" />
      <path d="M4.4 12.4c.6-2.2 1.8-3.2 3.6-3.2s3 1 3.6 3.2" stroke="white" strokeWidth="1.3" strokeLinecap="round" />
    </svg>
  );
}

export function VBuck({ size = 16 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden="true">
      <defs>
        <linearGradient id="nexa-coin" x1="6" y1="4" x2="26" y2="28">
          <stop offset="0" stopColor="#fff6d0" />
          <stop offset="0.45" stopColor="#f0c14d" />
          <stop offset="1" stopColor="#b87412" />
        </linearGradient>
      </defs>
      <circle cx="16" cy="16" r="13" fill="url(#nexa-coin)" />
      <circle cx="16" cy="16" r="9.5" fill="none" stroke="#8a5610" strokeWidth="1.4" />
      <path d="M16 8.5 21.2 12.2 19.4 18.6 12.6 18.6 10.8 12.2 Z" fill="#fff9e8" />
      <path d="M16 12.2 18.2 16.4 13.8 16.4 Z" fill="#c48416" />
    </svg>
  );
}
