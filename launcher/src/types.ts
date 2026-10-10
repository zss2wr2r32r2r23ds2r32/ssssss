export type ItemType = 'skin' | 'emote' | 'pickaxe' | 'glider' | 'backbling' | 'wrap';
export type Rarity = 'common' | 'uncommon' | 'rare' | 'epic' | 'legendary' | 'mythic';
export type Tab = 'home' | 'library' | 'shop' | 'leaderboards' | 'donate' | 'settings';
export type SettingsTab = 'account' | 'game' | 'launcher';

export interface ShopItem {
  id: string;
  name: string;
  type: ItemType;
  rarity: Rarity;
  vbucks: number;
  image: string;
  section?: 'featured' | 'daily';
}

export interface User {
  id: string;
  discordId: string;
  discordName: string;
  displayName: string;
  role: 'admin' | 'player';
  avatar: string;
  equipped: {
    skin: ShopItem | null;
    emote: ShopItem | null;
    pickaxe: ShopItem | null;
    glider: ShopItem | null;
  };
  nameChange: {
    allowed: boolean;
    remainingMs: number;
  };
}

export interface Settings {
  theme: string;
  mobileBuilds: boolean;
  resetOnRelease: boolean;
  potatoGraphics: boolean;
}

export interface Stats {
  elims: number;
  wins: number;
  matches: number;
  vbucks: number;
}

export interface NewsItem {
  id: string;
  title: string;
  body: string;
  image?: string | null;
  createdAt: string;
  author: string;
}

export interface Build {
  id: string;
  name: string;
  version: string;
  folderPath: string | null;
  executablePath: string | null;
  splashPath?: string | null;
  gameVersion?: string;
  changelist?: string;
  source: 'local' | 'catalog';
  createdAt: string;
}

export interface LeaderRow {
  id: string;
  name: string;
  discordName: string;
  displayName: string;
  avatar: string;
  wins: number;
  elims: number;
  points: number;
  you: boolean;
}
