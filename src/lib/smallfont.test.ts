import { describe, expect, it } from 'vitest';
import { toSmallFont } from './smallfont';

describe('toSmallFont', () => {
  it('matches the Minecraft small-caps example', () => {
    expect(toSmallFont('turn text like this')).toBe('ᴛᴜʀɴ ᴛᴇxᴛ ʟɪᴋᴇ ᴛʜɪs');
  });

  it('small-caps a bounty label and keeps punctuation', () => {
    expect(toSmallFont('Bounty')).toBe('ʙᴏᴜɴᴛʏ');
    expect(toSmallFont('Hello, World!')).toBe('ʜᴇʟʟᴏ, ᴡᴏʀʟᴅ!');
  });

  it('can leave placeholders untouched', () => {
    expect(toSmallFont('Kill %player% now', { preservePlaceholders: true })).toBe('ᴋɪʟʟ %player% ɴᴏᴡ');
    expect(toSmallFont('Kill %player%', { preservePlaceholders: false })).toBe('ᴋɪʟʟ %ᴘʟᴀʏᴇʀ%');
  });
});
