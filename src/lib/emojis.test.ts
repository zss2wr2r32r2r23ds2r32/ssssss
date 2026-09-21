import { describe, expect, it } from 'vitest';
import { EMOJI_CATALOG, REQUIRED_EMOJI, filterEmojis } from './emojis';

describe('emoji catalogue', () => {
  it('includes the seeded Minecraft symbols and unique ids', () => {
    const chars = new Set(EMOJI_CATALOG.map((entry) => entry.char));
    for (const symbol of REQUIRED_EMOJI) {
      expect(chars.has(symbol), symbol).toBe(true);
    }
    expect(new Set(EMOJI_CATALOG.map((entry) => entry.id)).size).toBe(EMOJI_CATALOG.length);
    expect(EMOJI_CATALOG.length).toBeGreaterThan(120);
  });

  it('filters by name, category, and character', () => {
    expect(filterEmojis(EMOJI_CATALOG, 'pickaxe', 'All').some((entry) => entry.char === '⛏')).toBe(true);
    expect(filterEmojis(EMOJI_CATALOG, '', 'Weapons').every((entry) => entry.category === 'Weapons')).toBe(true);
    expect(filterEmojis(EMOJI_CATALOG, '★', 'All').some((entry) => entry.char === '★')).toBe(true);
  });
});
