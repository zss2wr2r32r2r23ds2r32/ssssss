import { describe, expect, it } from 'vitest';
import { renderFormat, type Decor } from './colour';

const plain: Decor = { bold: false, italic: false, underline: false, strike: false };

describe('colour formats', () => {
  it('builds a per-character hex gradient and skips spaces', () => {
    expect(renderFormat('amp', 'Hi', ['#ff0000', '#0000ff'], true, plain)).toBe('&#ff0000H&#0000ffi');
    expect(renderFormat('amp', 'A B', ['#ff0000', '#0000ff'], true, plain)).toBe('&#ff0000A &#0000ffB');
  });

  it('builds legacy hex, section hex, and MiniMessage', () => {
    expect(renderFormat('legacyHex', 'H', ['#ff0000'], true, plain)).toBe('&x&f&f&0&0&0&0H');
    expect(renderFormat('sectionHex', 'H', ['#ff0000'], true, plain)).toBe('§x§f§f§0§0§0§0H');
    expect(renderFormat('mini', 'Hi', ['#ff0000', '#0000ff'], true, plain)).toBe('<#ff0000>H<#0000ff>i');
    expect(renderFormat('miniGradient', 'Hi', ['#ff0000', '#94ff00'], true, plain)).toBe(
      '<gradient:#ff0000:#94ff00>Hi</gradient>',
    );
  });

  it('maps pure red to the nearest legacy code and repeats bold', () => {
    expect(renderFormat('legacy', 'H', ['#ff0000'], true, plain)).toBe('&4H');
    expect(renderFormat('amp', 'H', ['#ff0000'], true, { ...plain, bold: true })).toBe('&#ff0000&lH');
    expect(renderFormat('miniGradient', 'Hi', ['#ff0000', '#ffffff'], true, { ...plain, italic: true })).toBe(
      '<i><gradient:#ff0000:#ffffff>Hi</gradient></i>',
    );
  });
});
