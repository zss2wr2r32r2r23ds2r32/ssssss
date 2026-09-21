export function normalizeHex(input: string, fallback = '#ff0000'): string {
  let hex = input.trim().toLowerCase().replace(/^#/, '');
  if (/^[0-9a-f]{3}$/.test(hex)) {
    hex = hex
      .split('')
      .map((char) => char + char)
      .join('');
  }
  if (!/^[0-9a-f]{6}$/.test(hex)) return fallback;
  return `#${hex}`;
}

export function ampHex(hex: string): string {
  return `&#${normalizeHex(hex).slice(1)}`;
}

export function hexToRgb(hex: string): { r: number; g: number; b: number } {
  const clean = normalizeHex(hex).slice(1);
  return {
    r: Number.parseInt(clean.slice(0, 2), 16),
    g: Number.parseInt(clean.slice(2, 4), 16),
    b: Number.parseInt(clean.slice(4, 6), 16),
  };
}

export function rgbToHex(rgb: { r: number; g: number; b: number }): string {
  const channel = (value: number) =>
    Math.max(0, Math.min(255, Math.round(value)))
      .toString(16)
      .padStart(2, '0');
  return `#${channel(rgb.r)}${channel(rgb.g)}${channel(rgb.b)}`;
}

export function isNeutralHex(hex: string): boolean {
  const { r, g, b } = hexToRgb(hex);
  return Math.max(r, g, b) - Math.min(r, g, b) < 18;
}
