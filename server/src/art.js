function svgUrl(body, viewBox = '0 0 128 128') {
  const doc = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${viewBox}" fill="none">${body}</svg>`;
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(doc)}`;
}

export function avatarDataUrl() {
  return svgUrl(`
    <defs>
      <linearGradient id="g" x1="20" y1="10" x2="110" y2="120">
        <stop offset="0" stop-color="#f4fbff"/>
        <stop offset="1" stop-color="#8fb0cc"/>
      </linearGradient>
    </defs>
    <circle cx="64" cy="64" r="64" fill="#0b0d12"/>
    <circle cx="64" cy="64" r="56" fill="url(#g)"/>
    <text x="64" y="76" text-anchor="middle" font-family="Arial, Helvetica, sans-serif" font-size="36" font-weight="700" fill="#12141a">AV</text>
  `);
}
