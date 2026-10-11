import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { dataDirectory } from './store.js';

const PREFIX = 'https://fortnite-api.com/images/cosmetics/';

export function cosmeticImageUrl(value) {
  if (typeof value !== 'string' || !value.startsWith(PREFIX)) return '';
  try {
    const url = new URL(value);
    if (url.origin !== 'https://fortnite-api.com' || !url.pathname.startsWith('/images/cosmetics/')) return '';
    if (url.pathname.includes('..')) return '';
    return url.href;
  } catch {
    return '';
  }
}

export function imageType(bytes) {
  if (bytes.length > 8 && bytes[0] === 0x89 && bytes[1] === 0x50) return 'image/png';
  if (bytes.length > 3 && bytes[0] === 0xff && bytes[1] === 0xd8) return 'image/jpeg';
  if (bytes.length > 12 && bytes.slice(0, 4).toString() === 'RIFF') return 'image/webp';
  return '';
}

function artFile(remote) {
  const name = `${crypto.createHash('sha256').update(remote).digest('hex')}.img`;
  return path.join(dataDirectory(), 'shop-art', name);
}

export async function cachedCosmetic(remote, fetchImpl = fetch) {
  const file = artFile(remote);
  try {
    const existing = fs.readFileSync(file);
    if (imageType(existing)) return existing;
  } catch {
    /* download it */
  }
  const response = await fetchImpl(remote, {
    headers: { Accept: 'image/png,image/*,*/*', 'User-Agent': 'nexa-launcher' },
  });
  if (!response.ok) throw new Error('Could not load that cosmetic image.');
  const bytes = Buffer.from(await response.arrayBuffer());
  if (!imageType(bytes) || bytes.length > 3_000_000) throw new Error('Could not load that cosmetic image.');
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, bytes);
  return bytes;
}

export function presentShopItem(item) {
  const remote = cosmeticImageUrl(item?.image);
  if (!remote) return item;
  return { ...item, image: `/api/shop/art?url=${encodeURIComponent(remote)}` };
}

export function warmShopArt(items, fetchImpl = fetch) {
  for (const item of items || []) {
    const remote = cosmeticImageUrl(item?.image);
    if (remote) cachedCosmetic(remote, fetchImpl).catch(() => {});
  }
}
