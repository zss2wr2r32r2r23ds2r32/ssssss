export function contrastOn(hex: string) {
  const h = hex.replace('#', '');
  if (h.length !== 6) return '#ffffff';
  const r = parseInt(h.slice(0, 2), 16);
  const g = parseInt(h.slice(2, 4), 16);
  const b = parseInt(h.slice(4, 6), 16);
  const y = (r * 299 + g * 587 + b * 114) / 1000;
  return y > 155 ? '#141414' : '#ffffff';
}

export function applyAccent(hex: string) {
  const root = document.documentElement;
  root.style.setProperty('--accent', hex);
  root.style.setProperty('--on-accent', contrastOn(hex));
}
