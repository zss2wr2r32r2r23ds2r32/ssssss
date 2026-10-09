function svgUrl(body, viewBox = '0 0 320 460') {
  const doc = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${viewBox}" fill="none">${body}</svg>`;
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(doc)}`;
}

function skin({
  coat,
  deep,
  skinTone,
  trim,
  visor,
  extra = '',
}) {
  return svgUrl(`
    <ellipse cx="160" cy="432" rx="86" ry="16" fill="#05060a" opacity="0.35"/>
    <path d="M122 268c-28 62-16 132 2 156h34l8-148z" fill="${deep}"/>
    <path d="M198 276l8 148h34c18-24 30-94 2-156z" fill="${deep}"/>
    <path d="M70 236c22 8 36 8 48-6l6 78-52 16z" fill="${deep}"/>
    <path d="M250 236c-22 8-36 8-48-6l-6 78 52 16z" fill="${deep}"/>
    <path d="M108 214c16-18 36-26 52-26s36 8 52 26l22 168c-36 26-112 26-148 0z" fill="${coat}"/>
    <path d="M128 214c10 18 22 28 32 28s22-10 32-28" stroke="${trim}" stroke-width="3" opacity="0.7"/>
    <path d="M118 156c6-58 28-86 42-86s36 28 42 86c2 22-16 40-42 44-26-4-44-22-42-44z" fill="${skinTone}"/>
    <path d="M112 148c8-64 34-92 48-92 22 0 48 30 54 92 2 18-18 8-28-6-14-16-30-22-46-16-16 4-28 8-28 22z" fill="${deep}"/>
    <path d="${visor}" stroke="${trim}" stroke-width="6" stroke-linecap="round"/>
    <circle cx="148" cy="186" r="3.4" fill="${trim}"/>
    <circle cx="178" cy="186" r="3.4" fill="${trim}"/>
    <path d="M146 206c6 8 22 8 30 0" stroke="${deep}" stroke-width="2" stroke-linecap="round" opacity="0.55"/>
    ${extra}
  `);
}

export function avatarDataUrl() {
  return svgUrl(
    `
    <defs>
      <linearGradient id="g" x1="20" y1="10" x2="110" y2="120">
        <stop offset="0" stop-color="#f4fbff"/>
        <stop offset="1" stop-color="#8fb0cc"/>
      </linearGradient>
    </defs>
    <circle cx="64" cy="64" r="64" fill="#0b0d12"/>
    <circle cx="64" cy="64" r="56" fill="url(#g)"/>
    <text x="64" y="76" text-anchor="middle" font-family="Arial, Helvetica, sans-serif" font-size="36" font-weight="700" fill="#12141a">AV</text>
  `,
    '0 0 128 128',
  );
}

export const itemArt = {
  nyxVale: skin({
    coat: '#3a2468',
    deep: '#140b22',
    skinTone: '#f0d2c4',
    trim: '#e4d4ff',
    visor: 'M132 184 H190',
    extra: `
      <path d="M96 228l28-18 6 46-30 8z" fill="#d8c6ff"/>
      <path d="M214 120l18 34-28 6-6-28z" fill="#b79cff" opacity="0.9"/>
      <path d="M70 250h180" stroke="#cbb6ff" stroke-width="2" opacity="0.35"/>
    `,
  }),
  auricWarden: skin({
    coat: '#6a4a16',
    deep: '#241806',
    skinTone: '#f3d7bf',
    trim: '#ffe7a8',
    visor: 'M134 182 H188',
    extra: `
      <circle cx="160" cy="118" r="28" fill="none" stroke="#ffe7a8" stroke-width="6"/>
      <path d="M160 78 v18 M160 140 v16 M118 118 h18 M184 118 h18" stroke="#ffe7a8" stroke-width="4" stroke-linecap="round"/>
      <path d="M124 248h72l-8 18h-56z" fill="#ffe7a8" opacity="0.85"/>
    `,
  }),
  lumenFox: skin({
    coat: '#14324d',
    deep: '#07131f',
    skinTone: '#f6d8c8',
    trim: '#b9f6ff',
    visor: 'M136 184 H188',
    extra: `
      <path d="M118 132 L138 78 L156 128 Z" fill="#0c2233" stroke="#b9f6ff" stroke-width="3"/>
      <path d="M176 128 L196 74 L214 136 Z" fill="#0c2233" stroke="#b9f6ff" stroke-width="3"/>
      <path d="M148 196c8 10 20 10 28 0" stroke="#7fe7f5" stroke-width="2" opacity="0.8"/>
    `,
  }),
  cinderPike: svgUrl(`
    <ellipse cx="160" cy="400" rx="70" ry="14" fill="#05060a" opacity="0.28"/>
    <path d="M78 312 L214 92" stroke="#2a2118" stroke-width="22" stroke-linecap="round"/>
    <path d="M78 312 L214 92" stroke="#ffb15a" stroke-width="8" stroke-linecap="round"/>
    <path d="M196 78l62 18-28 36-46-8z" fill="#ffd7a4"/>
    <path d="M188 104l46 10-18 24-36-6z" fill="#e07a1c"/>
    <path d="M64 328l28-8 10 22-26 12z" fill="#8a5a2a"/>
    <circle cx="214" cy="96" r="6" fill="#fff4e0"/>
  `),
  grayline: svgUrl(`
    <ellipse cx="160" cy="400" rx="70" ry="14" fill="#05060a" opacity="0.28"/>
    <path d="M86 330 L210 112" stroke="#2c313a" stroke-width="20" stroke-linecap="round"/>
    <path d="M86 330 L210 112" stroke="#d5d8e0" stroke-width="6" stroke-linecap="round"/>
    <path d="M196 96h58l-16 28h-36z" fill="#eef1f6"/>
    <path d="M204 124h34l-10 16h-22z" fill="#8b93a1"/>
    <rect x="70" y="318" width="36" height="18" rx="4" fill="#9aa1ad" transform="rotate(-28 88 327)"/>
  `),
  orbitVeil: svgUrl(`
    <ellipse cx="160" cy="400" rx="90" ry="14" fill="#05060a" opacity="0.28"/>
    <path d="M40 210c40-90 200-90 240 0-30 40-210 40-240 0z" fill="#ffb15a"/>
    <path d="M70 206c30-48 150-48 180 0" stroke="#fff1dc" stroke-width="6" opacity="0.8"/>
    <path d="M150 230 l10 90 h-20z" fill="#7a3e10"/>
    <circle cx="160" cy="188" r="16" fill="none" stroke="#fff6e8" stroke-width="4"/>
    <path d="M48 214c20 16 40 16 54 0 M218 214c14 16 34 16 54 0" stroke="#8a3d0c" stroke-width="4" opacity="0.45"/>
  `),
  signalPop: svgUrl(`
    <ellipse cx="160" cy="414" rx="64" ry="12" fill="#05060a" opacity="0.28"/>
    <circle cx="160" cy="168" r="36" fill="#14301c"/>
    <circle cx="160" cy="168" r="22" fill="#d9ffe4"/>
    <path d="M148 164h6l4 10 4-10h6" stroke="#146b38" stroke-width="2"/>
    <path d="M132 230c10 40 46 40 56 0l18 120h-28l-8-70-8 70h-28l-10-120z" fill="#1c6b3a"/>
    <path d="M118 250c-30-10-40 20-18 32" stroke="#d9ffe4" stroke-width="6" stroke-linecap="round"/>
    <path d="M206 246c28-16 42 16 16 34" stroke="#d9ffe4" stroke-width="6" stroke-linecap="round"/>
    <path d="M92 150c10-28 8-28 20-8 M228 148c-8-24-6-28-20-4" stroke="#b6ff9a" stroke-width="4" stroke-linecap="round"/>
    <circle cx="108" cy="132" r="6" fill="#eaffd8"/>
    <circle cx="214" cy="124" r="5" fill="#eaffd8"/>
  `),
  freewheel: svgUrl(`
    <ellipse cx="160" cy="418" rx="72" ry="12" fill="#05060a" opacity="0.28"/>
    <path d="M70 250c30-20 50-8 70 10 18-28 48-36 74-16" stroke="#f3e8ff" stroke-width="8" stroke-linecap="round"/>
    <circle cx="168" cy="176" r="34" fill="#2a1248"/>
    <circle cx="168" cy="176" r="20" fill="#f6e4ff"/>
    <path d="M158 172h20" stroke="#5b21b6" stroke-width="3" stroke-linecap="round"/>
    <path d="M140 230c8 50 40 70 52 48 8 36-10 78-6 110h-26l-8-78-16 78h-26c4-40 8-92 30-158z" fill="#6d28d9"/>
    <path d="M214 250c26 8 36 36 16 48" stroke="#f3e8ff" stroke-width="8" stroke-linecap="round"/>
    <path d="M96 300c-20 6-24 28-6 34" stroke="#e9d5ff" stroke-width="7" stroke-linecap="round"/>
    <path d="M64 188c18-8 28 6 16 16 M248 176c14 10 6 26-10 20" stroke="#f5e1ff" stroke-width="4" stroke-linecap="round"/>
  `),
};
