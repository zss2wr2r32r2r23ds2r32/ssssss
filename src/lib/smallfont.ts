/**
 * Phonetic small-caps used for Minecraft labels.
 * S and X stay Latin: that is the "ᴛʜɪs" style (there is no small-cap X in
 * the alphabet, and a converted S reads worse in the default font).
 */
export const SMALL_CAPS: Record<string, string> = {
  a: 'ᴀ',
  b: 'ʙ',
  c: 'ᴄ',
  d: 'ᴅ',
  e: 'ᴇ',
  f: 'ꜰ',
  g: 'ɢ',
  h: 'ʜ',
  i: 'ɪ',
  j: 'ᴊ',
  k: 'ᴋ',
  l: 'ʟ',
  m: 'ᴍ',
  n: 'ɴ',
  o: 'ᴏ',
  p: 'ᴘ',
  q: 'ǫ',
  r: 'ʀ',
  t: 'ᴛ',
  u: 'ᴜ',
  v: 'ᴠ',
  w: 'ᴡ',
  y: 'ʏ',
  z: 'ᴢ',
};

const PLACEHOLDER = /(%[A-Za-z0-9_]+%|\{[A-Za-z0-9_]+\}|<[^>\n]+>)/g;

export function toSmallFont(input: string, options?: { preservePlaceholders?: boolean }): string {
  const convert = (text: string) =>
    Array.from(text)
      .map((char) => SMALL_CAPS[char.toLowerCase()] ?? char)
      .join('');

  if (!options?.preservePlaceholders) return convert(input);

  return input
    .split(PLACEHOLDER)
    .map((part, index) => (index % 2 === 1 ? part : convert(part)))
    .join('');
}

export function smallCapsEntries(): { from: string; to: string }[] {
  const letters = 'abcdefghijklmnopqrstuvwxyz'.split('');
  return letters.map((letter) => ({ from: letter, to: SMALL_CAPS[letter] ?? letter }));
}
