import { hexToRgb, normalizeHex, rgbToHex } from './hex';

export type Decor = {
  bold: boolean;
  italic: boolean;
  underline: boolean;
  strike: boolean;
};

export type FormatId = 'amp' | 'legacyHex' | 'sectionHex' | 'mini' | 'miniGradient' | 'legacy';

export const FORMAT_META: { id: FormatId; label: string; hint: string }[] = [
  { id: 'amp', label: 'Hex &#', hint: 'Per-character &#RRGGBB. Common on Paper plugins and heads.' },
  { id: 'legacyHex', label: 'Legacy &x', hint: '1.16 hex as &x&r&r&g&g&b&b before each character.' },
  { id: 'sectionHex', label: 'Section §x', hint: 'The same hex form with section signs, for raw chat components.' },
  { id: 'mini', label: 'MiniMessage', hint: 'Adventure <#RRGGBB> before each character.' },
  { id: 'miniGradient', label: 'MM gradient', hint: 'One <gradient:…> tag across the whole string.' },
  { id: 'legacy', label: 'Legacy &', hint: 'Nearest classic &0–&f colour for each character.' },
];

const LEGACY: { code: string; hex: string }[] = [
  { code: '0', hex: '#000000' },
  { code: '1', hex: '#0000aa' },
  { code: '2', hex: '#00aa00' },
  { code: '3', hex: '#00aaaa' },
  { code: '4', hex: '#aa0000' },
  { code: '5', hex: '#aa00aa' },
  { code: '6', hex: '#ffaa00' },
  { code: '7', hex: '#aaaaaa' },
  { code: '8', hex: '#555555' },
  { code: '9', hex: '#5555ff' },
  { code: 'a', hex: '#55ff55' },
  { code: 'b', hex: '#55ffff' },
  { code: 'c', hex: '#ff5555' },
  { code: 'd', hex: '#ff55ff' },
  { code: 'e', hex: '#ffff55' },
  { code: 'f', hex: '#ffffff' },
];

export function lerpChannel(a: number, b: number, t: number): number {
  return Math.round(a + (b - a) * t);
}

export function gradientHex(stops: string[], t: number): string {
  const colours = (stops.length ? stops : ['#ff0000']).map((stop) => hexToRgb(normalizeHex(stop)));
  if (colours.length === 1) return rgbToHex(colours[0]);
  const clamped = Math.min(1, Math.max(0, t));
  const scaled = clamped * (colours.length - 1);
  const index = Math.min(colours.length - 2, Math.floor(scaled));
  const local = scaled - index;
  const from = colours[index];
  const to = colours[index + 1];
  return rgbToHex({
    r: lerpChannel(from.r, to.r, local),
    g: lerpChannel(from.g, to.g, local),
    b: lerpChannel(from.b, to.b, local),
  });
}

/** Parallel to Array.from(text). Empty string means "do not emit a colour code". */
export function characterColors(text: string, stops: string[], skipSpaces: boolean): string[] {
  const chars = Array.from(text);
  const coloredIndexes = chars
    .map((char, index) => ({ char, index }))
    .filter(({ char }) => !(skipSpaces && /\s/u.test(char)))
    .map(({ index }) => index);
  const colors = chars.map(() => '');
  coloredIndexes.forEach((index, order) => {
    const t = coloredIndexes.length <= 1 ? 0 : order / (coloredIndexes.length - 1);
    colors[index] = gradientHex(stops, t);
  });
  return colors;
}

export function previewColors(text: string, stops: string[], skipSpaces: boolean): string[] {
  const colors = characterColors(text, stops, skipSpaces);
  let last = gradientHex(stops, 0);
  return colors.map((color) => {
    if (color) {
      last = color;
      return color;
    }
    return last;
  });
}

function legacyDecor(decor: Decor): string {
  return `${decor.bold ? '&l' : ''}${decor.italic ? '&o' : ''}${decor.underline ? '&n' : ''}${decor.strike ? '&m' : ''}`;
}

function sectionDecor(decor: Decor): string {
  return `${decor.bold ? '§l' : ''}${decor.italic ? '§o' : ''}${decor.underline ? '§n' : ''}${decor.strike ? '§m' : ''}`;
}

function wrapMini(text: string, decor: Decor): string {
  let open = '';
  let close = '';
  if (decor.bold) {
    open += '<b>';
    close = `</b>${close}`;
  }
  if (decor.italic) {
    open += '<i>';
    close = `</i>${close}`;
  }
  if (decor.underline) {
    open += '<u>';
    close = `</u>${close}`;
  }
  if (decor.strike) {
    open += '<st>';
    close = `</st>${close}`;
  }
  return `${open}${text}${close}`;
}

function nearestLegacy(hex: string): string {
  const rgb = hexToRgb(hex);
  let best = LEGACY[0];
  let bestScore = Number.POSITIVE_INFINITY;
  let bestBright = -1;
  for (const entry of LEGACY) {
    const other = hexToRgb(entry.hex);
    const score = (rgb.r - other.r) ** 2 + (rgb.g - other.g) ** 2 + (rgb.b - other.b) ** 2;
    const bright = other.r + other.g + other.b;
    if (score < bestScore || (score === bestScore && bright > bestBright)) {
      best = entry;
      bestScore = score;
      bestBright = bright;
    }
  }
  return best.code;
}

function joinColored(
  text: string,
  colors: string[],
  codeFor: (hex: string) => string,
  decorCode: string,
): string {
  const chars = Array.from(text);
  let out = '';
  chars.forEach((char, index) => {
    const hex = colors[index];
    if (hex) out += `${codeFor(hex)}${decorCode}`;
    out += char;
  });
  return out;
}

export function renderFormat(
  id: FormatId,
  text: string,
  stops: string[],
  skipSpaces: boolean,
  decor: Decor,
): string {
  const safeStops = (stops.length ? stops : ['#ff0000']).map((stop) => normalizeHex(stop));
  const colors = characterColors(text, safeStops, skipSpaces);
  const decorAmp = legacyDecor(decor);
  const decorSection = sectionDecor(decor);

  if (id === 'amp') {
    return joinColored(text, colors, (hex) => `&#${hex.slice(1)}`, decorAmp);
  }
  if (id === 'legacyHex') {
    return joinColored(text, colors, (hex) => `&x&${hex.slice(1).split('').join('&')}`, decorAmp);
  }
  if (id === 'sectionHex') {
    return joinColored(text, colors, (hex) => `§x§${hex.slice(1).split('').join('§')}`, decorSection);
  }
  if (id === 'mini') {
    const body = joinColored(text, colors, (hex) => `<${hex}>`, '');
    return wrapMini(body, decor);
  }
  if (id === 'miniGradient') {
    const gradientStops = safeStops.join(':');
    return wrapMini(`<gradient:${gradientStops}>${text}</gradient>`, decor);
  }
  return joinColored(text, colors, (hex) => `&${nearestLegacy(hex)}`, decorAmp);
}

export function buildFormats(
  text: string,
  stops: string[],
  skipSpaces: boolean,
  decor: Decor,
): Record<FormatId, string> {
  return {
    amp: renderFormat('amp', text, stops, skipSpaces, decor),
    legacyHex: renderFormat('legacyHex', text, stops, skipSpaces, decor),
    sectionHex: renderFormat('sectionHex', text, stops, skipSpaces, decor),
    mini: renderFormat('mini', text, stops, skipSpaces, decor),
    miniGradient: renderFormat('miniGradient', text, stops, skipSpaces, decor),
    legacy: renderFormat('legacy', text, stops, skipSpaces, decor),
  };
}

export const GRADIENT_PRESETS: { name: string; stops: string[] }[] = [
  { name: 'Crimson', stops: ['#ff0000', '#5a0000'] },
  { name: 'Bounty', stops: ['#ff0000', '#94ff00'] },
  { name: 'Ember', stops: ['#ff0000', '#ffaa00', '#ffff55'] },
  { name: 'Ocean', stops: ['#0033aa', '#55ffff'] },
  { name: 'Amethyst', stops: ['#aa00aa', '#ff55ff'] },
];
