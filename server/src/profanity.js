const SUBSTRINGS = [
  'fuck',
  'shit',
  'bitch',
  'asshole',
  'bastard',
  'nigger',
  'nigga',
  'faggot',
  'retard',
  'whore',
  'chink',
  'tranny',
  'kike',
  'rape',
];

const TOKENS = [
  ...SUBSTRINGS,
  'fag',
  'slut',
  'spic',
  'cock',
  'dick',
  'cunt',
];

function leet(value) {
  return value
    .toLowerCase()
    .replace(/[@4]/g, 'a')
    .replace(/3/g, 'e')
    .replace(/1/g, 'i')
    .replace(/0/g, 'o')
    .replace(/5/g, 's')
    .replace(/7/g, 't')
    .replace(/\$/g, 's');
}

export function isProfane(name) {
  const flat = leet(name).replace(/[^a-z]/g, '');
  if (SUBSTRINGS.some((word) => flat.includes(word))) return true;
  const tokens = leet(name)
    .split(/[^a-z]+/)
    .filter(Boolean);
  return tokens.some((token) => TOKENS.includes(token));
}

export function cleanDisplayName(raw) {
  if (typeof raw !== 'string') return { error: 'Display name must be at least 4 characters.' };
  const name = raw.trim().replace(/\s+/g, ' ');
  if (name.length < 4) return { error: 'Display name must be at least 4 characters.' };
  if (name.length > 16) return { error: 'Display name must be 16 characters or fewer.' };
  if (!/^[A-Za-z0-9 ]+$/.test(name)) return { error: 'Use letters, numbers, and spaces only.' };
  if (!/[A-Za-z]/.test(name)) return { error: 'Use at least one letter.' };
  if (isProfane(name)) return { error: "That name isn't allowed." };
  return { name };
}

const TWO_WEEKS_MS = 14 * 24 * 60 * 60 * 1000;

export function cooldownState(lastNameChangeAt, now = Date.now()) {
  if (!lastNameChangeAt) return { allowed: true, remainingMs: 0 };
  const ends = new Date(lastNameChangeAt).getTime() + TWO_WEEKS_MS;
  const remainingMs = ends - now;
  if (remainingMs <= 0) return { allowed: true, remainingMs: 0 };
  return { allowed: false, remainingMs };
}

export function formatRemaining(ms) {
  const total = Math.max(0, Math.ceil(ms / 1000));
  const days = Math.floor(total / 86400);
  const hours = Math.floor((total % 86400) / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  if (days > 0) return `${days}d ${hours}h`;
  if (hours > 0) return `${hours}h ${minutes}m`;
  return `${Math.max(1, minutes)}m`;
}
