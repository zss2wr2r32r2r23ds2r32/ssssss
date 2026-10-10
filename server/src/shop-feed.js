const TYPE_MAP = {
  outfit: 'skin',
  emote: 'emote',
  emoji: 'emote',
  pickaxe: 'pickaxe',
  glider: 'glider',
};

const PRICES = {
  common: 500,
  uncommon: 800,
  rare: 1200,
  epic: 1500,
  legendary: 2000,
  mythic: 1500,
};

function mapRarity(value) {
  const rarity = String(value || '').toLowerCase();
  if (PRICES[rarity] != null && ['common', 'uncommon', 'rare', 'epic', 'legendary', 'mythic'].includes(rarity)) {
    return rarity;
  }
  if (['icon', 'gaminglegends', 'marvel', 'dc', 'starwars', 'frozen', 'lava', 'shadow', 'slurp', 'dark'].includes(rarity)) {
    return 'legendary';
  }
  return 'epic';
}

function imageOf(item) {
  const images = item?.images || {};
  return images.featured || images.icon || images.smallIcon || '';
}

function shuffle(list) {
  const copy = [...list];
  for (let index = copy.length - 1; index > 0; index -= 1) {
    const swap = Math.floor(Math.random() * (index + 1));
    [copy[index], copy[swap]] = [copy[swap], copy[index]];
  }
  return copy;
}

function toItem(item, section) {
  const rarity = mapRarity(item.rarity?.value);
  const type = TYPE_MAP[item.type?.value];
  return {
    id: `${section}-${item.id}`,
    name: String(item.name || 'Item').slice(0, 32),
    type,
    rarity,
    vbucks: PRICES[rarity],
    image: imageOf(item),
    section,
  };
}

export async function pullShopItems(fetchImpl = fetch) {
  const response = await fetchImpl('https://fortnite-api.com/v2/cosmetics/br', {
    headers: { Accept: 'application/json', 'User-Agent': 'nexa-launcher' },
  });
  if (!response.ok) throw new Error('Could not reach the cosmetics list.');
  const body = await response.json();
  const usable = (body.data || []).filter((item) => {
    const type = TYPE_MAP[item.type?.value];
    const image = imageOf(item);
    return type && typeof image === 'string' && image.startsWith('https://');
  });
  const picked = shuffle(usable);
  return [
    ...picked.slice(0, 3).map((item) => toItem(item, 'featured')),
    ...picked.slice(3, 11).map((item) => toItem(item, 'daily')),
  ];
}
