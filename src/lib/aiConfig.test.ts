import yaml from 'js-yaml';
import { describe, expect, it } from 'vitest';
import { BOUNTY_EXAMPLE, PRESETS, parseIntent, recolourText, rewriteConfig, smallLabelText } from './aiConfig';

const options = { accent: '#ff0000', secondary: '#94ff00' };

describe('ai config', () => {
  it('recognises the three presets', () => {
    expect(parseIntent(PRESETS[0].instruction).bounty).toBe(true);
    expect(parseIntent(PRESETS[1].instruction)).toMatchObject({ recolour: true, bounty: false, smallLabels: false });
    expect(parseIntent(PRESETS[2].instruction)).toMatchObject({ smallLabels: true, bounty: false, recolour: false });
  });

  it('restyles a plain bounty head into the crimson lore pattern', () => {
    const result = rewriteConfig(BOUNTY_EXAMPLE, {
      ...options,
      instruction: PRESETS[0].instruction,
    });
    expect(result.ok).toBe(true);
    const doc = yaml.load(result.output) as {
      head: { name: string; material: string; lore: string[] };
    };
    expect(doc.head.name).toBe('&#ff0000%player%');
    expect(doc.head.material).toBe('PLAYER_HEAD');
    expect(doc.head.lore).toEqual([
      '&7[ʙᴏᴜɴᴛʏ]',
      '',
      '&#ff0000Description:',
      '&#ff0000| &fKill &#ff0000%player% To',
      '&#ff0000| &fGain Money',
      '',
      '&#94ff00☀ &fAmount: &#94ff00%amount%',
      '&#ff0000✎ &fSet By: &#ff0000%setby%',
    ]);
  });

  it('keeps custom description lines and stat labels', () => {
    const result = rewriteConfig(
      `shop:
  sword:
    material: DIAMOND_SWORD
    name: '&cExcalibur'
    lore:
      - '[Legendary]'
      - 'A blade for champions'
      - 'Damage: 12'
      - 'Price: %price%'
`,
      { ...options, instruction: 'Apply bounty head style to item names and lore.' },
    );
    const doc = yaml.load(result.output) as {
      shop: { sword: { material: string; name: string; lore: string[] } };
    };
    expect(doc.shop.sword.material).toBe('DIAMOND_SWORD');
    expect(doc.shop.sword.name).toBe('&#ff0000Excalibur');
    expect(doc.shop.sword.lore).toEqual([
      '&7[ʟᴇɢᴇɴᴅᴀʀʏ]',
      '',
      '&#ff0000Description:',
      '&#ff0000| &fA blade for champions',
      '',
      '&#ff0000🗡 &fDamage: &#94ff0012',
      '&#94ff00☀ &fPrice: &#94ff00%price%',
    ]);
  });

  it('recolours accents in place and keeps white and grey', () => {
    const input = "prefix: '&cHello &7world &fok'\nitem: '&#00aaffGem'\n";
    const result = rewriteConfig(input, { ...options, instruction: PRESETS[1].instruction });
    expect(result.ok).toBe(true);
    expect(result.output).toContain('&#ff0000Hello &7world &fok');
    expect(result.output).toContain('&#ff0000Gem');
    expect(result.output).not.toContain('&#00aaff');
  });

  it('converts bracket labels without touching placeholders', () => {
    expect(smallLabelText('&7[BOUNTY] %player%')).toBe('&7[ʙᴏᴜɴᴛʏ] %player%');
    expect(smallLabelText('[Top %player%]')).toBe('[ᴛᴏᴘ %player%]');
    const result = rewriteConfig("title: '[Bounty]'\n", {
      ...options,
      instruction: PRESETS[2].instruction,
    });
    expect(result.output).toContain('[ʙᴏᴜɴᴛʏ]');
    expect(result.output.startsWith('title:')).toBe(true);
  });

  it('renames and assigns keys', () => {
    const renamed = rewriteConfig('item:\n  name: Old Sword\n  lore:\n    - hello\n', {
      ...options,
      instruction: 'change name to Night Blade',
    });
    const doc = yaml.load(renamed.output) as { item: { name: string; lore: string[] } };
    expect(doc.item.name).toBe('Night Blade');
    expect(doc.item.lore).toEqual(['hello']);

    const assigned = rewriteConfig('cooldown: 10\n', { ...options, instruction: 'set cooldown to 30' });
    expect(yaml.load(assigned.output)).toEqual({ cooldown: 30 });
  });

  it('reports a YAML error for a structural edit', () => {
    const result = rewriteConfig('head: [\n', { ...options, instruction: PRESETS[0].instruction });
    expect(result.ok).toBe(false);
    expect(result.notes[0]).toMatch(/parse/i);
  });

  it('recolours a chosen hex from the instruction', () => {
    expect(recolourText('&aGo', '#55ffff')).toContain('&#55ffffGo');
  });
});
