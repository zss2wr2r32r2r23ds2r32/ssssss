import yaml from 'js-yaml';
import { ampHex, isNeutralHex, normalizeHex } from './hex';
import { toSmallFont } from './smallfont';

export type RewriteOptions = {
  instruction: string;
  accent: string;
  secondary: string;
};

export type RewriteResult = {
  ok: boolean;
  output: string;
  notes: string[];
  source: 'local';
};

type Assignment = { key: string; raw: string };

type Intent = {
  bounty: boolean;
  recolour: boolean;
  smallLabels: boolean;
  renameTo?: string;
  accentOverride?: string;
  assignments: Assignment[];
};

type Walk = {
  intent: Intent;
  accent: string;
  secondary: string;
  styled: number;
  assigned: number;
  renamed: number;
};

const NAME_KEYS = ['name', 'display-name', 'display_name', 'displayname', 'item-name', 'item_name', 'title'];

const SKIP_ASSIGNMENT_KEYS = new Set([
  'name',
  'the',
  'it',
  'them',
  'accent',
  'color',
  'colour',
  'colors',
  'colours',
  'lore',
  'label',
  'labels',
  'bracket',
]);

const KEEP_LEGACY = new Set(['f', '7', '8', '0', 'r', 'l', 'o', 'n', 'm', 'k']);

const MINI_NEUTRAL = new Set(['white', 'gray', 'grey', 'dark_gray', 'dark_grey', 'black', 'reset']);
const MINI_FORMAT = new Set(['bold', 'italic', 'underlined', 'strikethrough', 'obfuscated', 'b', 'i', 'u', 'st', 'obf']);
const MINI_COLORS = [
  'dark_red',
  'red',
  'gold',
  'yellow',
  'dark_green',
  'green',
  'aqua',
  'dark_aqua',
  'dark_blue',
  'blue',
  'light_purple',
  'dark_purple',
];

export const PRESETS = [
  {
    id: 'bounty',
    label: 'Bounty head',
    instruction: 'Apply bounty head style to item names and lore.',
  },
  {
    id: 'recolour',
    label: 'Recolour accent',
    instruction: 'Recolour accent colours to the configured accent. Keep white and grey.',
  },
  {
    id: 'small',
    label: 'Small-font labels',
    instruction: 'Convert bracket labels to small-font.',
  },
] as const;

export const BOUNTY_EXAMPLE = `head:
  name: '%player%'
  lore:
    - '[Bounty]'
    - 'Kill %player% To'
    - 'Gain Money'
    - 'Amount: %amount%'
    - 'Set By: %setby%'
  material: PLAYER_HEAD
`;

export function parseIntent(instruction: string): Intent {
  const text = instruction.toLowerCase();
  const bounty = /\bbounty\b|polish(?: the)? lore|restyle|head style/.test(text);
  const recolour = /recolou?r|change colou?rs?|colou?r (?:it |them |the )?(?:to|accent)|make (?:it |them )?(?:red|crimson)/.test(
    text,
  );
  const smallLabels = /small[ -]?font|small[ -]?caps|bracket labels?/.test(text);

  const quoted = instruction.match(
    /\b(?:change|rename|set)\s+(?:the\s+)?(?:display\s+|item\s+)?name\s+(?:to|as)\s+["']([^"']+)["']/i,
  );
  const bare = instruction.match(
    /\b(?:change|rename|set)\s+(?:the\s+)?(?:display\s+|item\s+)?name\s+(?:to|as)\s+([^,\n]+?)(?:\s+and\s+|\s*$)/i,
  );
  const renameTo = (quoted?.[1] ?? bare?.[1])?.trim() || undefined;

  const hexMention = instruction.match(/#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})\b/);
  const accentOverride = hexMention ? normalizeHex(`#${hexMention[1]}`) : undefined;

  return {
    bounty,
    recolour,
    smallLabels,
    renameTo,
    accentOverride,
    assignments: extractAssignments(instruction),
  };
}

function extractAssignments(instruction: string): Assignment[] {
  const out: Assignment[] = [];
  const re =
    /\b(?:set|change)\s+([A-Za-z_][\w-]*)\s+to\s+(?:"([^"]+)"|'([^']+)'|([^\n,]+?)(?=\s*(?:,|\band\b|$)))/gi;
  let match: RegExpExecArray | null;
  while ((match = re.exec(instruction))) {
    const key = match[1];
    if (SKIP_ASSIGNMENT_KEYS.has(key.toLowerCase())) continue;
    const raw = (match[2] ?? match[3] ?? match[4] ?? '').trim();
    if (raw) out.push({ key, raw });
  }
  return out;
}

export function stripColors(input: string): string {
  return input
    .replace(/<\/?(?:gradient|rainbow)\b[^>]*>/gi, '')
    .replace(/&x(?:&[0-9a-fA-F]){6}/gi, '')
    .replace(/§x(?:§[0-9a-fA-F]){6}/gi, '')
    .replace(/&#[0-9a-fA-F]{6}/gi, '')
    .replace(/<#[0-9a-fA-F]{6}>/gi, '')
    .replace(/<\/?(?:bold|italic|underlined|strikethrough|obfuscated|reset|white|gray|grey|dark_gray|dark_grey|black|red|dark_red|gold|yellow|green|dark_green|aqua|dark_aqua|blue|dark_blue|light_purple|dark_purple|b|i|u|st|obf)>/gi, '')
    .replace(/&[0-9a-fk-or]/gi, '')
    .replace(/§[0-9a-fk-or]/gi, '')
    .trim();
}

export function recolourText(input: string, accent: string): string {
  const accentHex = normalizeHex(accent).slice(1);
  const accentAmp = `&#${accentHex}`;
  const legacyHex = `&x&${accentHex.split('').join('&')}`;
  const sectionHex = `§x§${accentHex.split('').join('§')}`;
  const masks: string[] = [];
  const mask = (token: string) => {
    const id = `\u0000${masks.length}\u0000`;
    masks.push(token);
    return id;
  };

  let out = input.replace(/&#([0-9a-fA-F]{6})/gi, (full, hex: string) => (isNeutralHex(hex) ? full : accentAmp));
  out = out.replace(/<#([0-9a-fA-F]{6})>/gi, (full, hex: string) => (isNeutralHex(hex) ? full : `<#${accentHex}>`));
  out = out.replace(/(?<![&<])#([0-9a-fA-F]{6})\b/g, (full, hex: string) => (isNeutralHex(hex) ? full : `#${accentHex}`));

  out = out.replace(/&x(?:&[0-9a-fA-F]){6}/gi, (full) => {
    const hex = full.slice(2).replace(/&/g, '');
    return mask(isNeutralHex(hex) ? full : legacyHex);
  });
  out = out.replace(/§x(?:§[0-9a-fA-F]){6}/gi, (full) => {
    const hex = full.slice(2).replace(/§/g, '');
    return mask(isNeutralHex(hex) ? full : sectionHex);
  });

  out = out.replace(/&([0-9a-fk-or])/gi, (full, code: string) => (KEEP_LEGACY.has(code.toLowerCase()) ? full : accentAmp));
  out = out.replace(/§([0-9a-fk-or])/gi, (full, code: string) => (KEEP_LEGACY.has(code.toLowerCase()) ? full : accentAmp));

  out = out.replace(/<(\/?)([a-z_]+)>/gi, (full, slash: string, name: string) => {
    const lower = name.toLowerCase();
    if (MINI_NEUTRAL.has(lower) || MINI_FORMAT.has(lower)) return full;
    if (MINI_COLORS.includes(lower)) return `<${slash}#${accentHex}>`;
    return full;
  });

  return out.replace(/\u0000(\d+)\u0000/g, (_full, index: string) => masks[Number(index)] ?? '');
}

export function smallLabelText(input: string): string {
  return input.replace(/\[([^\[\]\n]{1,48})\]/g, (_full, inner: string) => {
    return `[${toSmallFont(inner, { preservePlaceholders: true })}]`;
  });
}

function isLabelCandidate(line: string): boolean {
  if (!line || line.length > 18) return false;
  if (/[:%]/.test(line)) return false;
  if (/description|kill|gain|amount|set\s*by|money/i.test(line)) return false;
  return line.split(/\s+/).length <= 2;
}

function bodyWithPlaceholders(plain: string, placeholderColor: string): string {
  const parts = plain.split(/(%[A-Za-z0-9_]+%|\{[A-Za-z0-9_]+\})/g);
  let out = '';
  let white = false;
  for (const part of parts) {
    if (!part) continue;
    if (/^(%[A-Za-z0-9_]+%|\{[A-Za-z0-9_]+\})$/.test(part)) {
      out += placeholderColor + part;
      white = false;
    } else if (!white) {
      out += `&f${part}`;
      white = true;
    } else {
      out += part;
    }
  }
  return out;
}

function colorValues(plain: string, color: string): string {
  return `${color}${plain}`;
}

type StatPresentation = { emoji: string; color: string; label: string; valueColor: string };

function presentStat(label: string, accent: string, secondary: string): StatPresentation {
  const lower = label.toLowerCase();
  if (/amount|money|price|cost|reward|balance|coin|pay/.test(lower)) {
    return { emoji: '☀', color: secondary, label: label.trim(), valueColor: secondary };
  }
  if (/set\s*by|author|owner|created by|maker/.test(lower)) {
    const pretty = /set\s*by/i.test(label) ? 'Set By' : label.trim();
    return { emoji: '✎', color: accent, label: pretty, valueColor: accent };
  }
  if (/damage|attack|dmg|strength/.test(lower)) {
    return { emoji: '🗡', color: accent, label: label.trim(), valueColor: secondary };
  }
  if (/health|heart|life/.test(lower)) {
    return { emoji: '❤', color: accent, label: label.trim(), valueColor: secondary };
  }
  if (/time|duration|cooldown|left/.test(lower)) {
    return { emoji: '⏳', color: accent, label: label.trim(), valueColor: secondary };
  }
  if (/world|location|warp|coord/.test(lower)) {
    return { emoji: '⚑', color: accent, label: label.trim(), valueColor: secondary };
  }
  if (/level|xp|exp/.test(lower)) {
    return { emoji: '★', color: accent, label: label.trim(), valueColor: secondary };
  }
  return { emoji: '⚡', color: accent, label: label.trim(), valueColor: secondary };
}

function buildBountyFields(rawName: string, loreRaw: unknown[], walk: Walk): { name: string; lore: unknown[] } {
  const accent = ampHex(walk.accent);
  const secondary = ampHex(walk.secondary);
  const stringLines = loreRaw.filter((line): line is string => typeof line === 'string');
  const passthrough = loreRaw.filter((line) => typeof line !== 'string');
  const lines = stringLines.map((line) => stripColors(line));
  const namePlainOriginal = stripColors(rawName);
  const blob = [namePlainOriginal, ...lines].join('\n');

  let label = '';
  const bracket = lines.find((line) => /^\[[^\]]+\]$/.test(line));
  if (bracket) label = bracket.slice(1, -1).trim();
  if (!label) {
    const short = lines.find((line) => isLabelCandidate(line));
    if (short) label = short;
  }
  if (!label) {
    if (isLabelCandidate(namePlainOriginal) && !namePlainOriginal.includes('%')) label = namePlainOriginal;
    else label = 'BOUNTY';
  }

  const small = toSmallFont(label, { preservePlaceholders: true });
  const namePlain = walk.intent.renameTo || namePlainOriginal || '%player%';
  const name = nameHasColor(walk.intent.renameTo) ? walk.intent.renameTo! : `${accent}${namePlain}`;

  const descLines: string[] = [];
  const stats: { label: string; value: string }[] = [];
  let canonicalKill = false;
  let canonicalGain = false;

  for (const line of lines) {
    if (!line) continue;
    if (/^\[[^\]]+\]$/.test(line)) continue;
    if (/^description:?$/i.test(line)) continue;
    if (label && line.toLowerCase() === label.toLowerCase()) continue;
    if (/^kill\s+%player%\s+to\.?$/i.test(line)) {
      canonicalKill = true;
      continue;
    }
    if (/^gain money\.?$/i.test(line)) {
      canonicalGain = true;
      continue;
    }
    if (/^kill\s+%player%\s+to\s+gain money\.?$/i.test(line)) {
      canonicalKill = true;
      canonicalGain = true;
      continue;
    }
    const stat = line.match(/^([^:%]{1,24}):\s*(.+)$/);
    if (stat && !/^(kill|gain)$/i.test(stat[1].trim())) {
      stats.push({ label: stat[1].trim(), value: stat[2].trim() });
      continue;
    }
    descLines.push(line);
  }

  const hasPlayer = /%player%/i.test(blob) || /%player%/i.test(namePlain);
  if (!canonicalKill && !canonicalGain && descLines.length === 0 && hasPlayer) {
    canonicalKill = true;
    canonicalGain = true;
  }

  const lore: string[] = [`&7[${small}]`, '', `${accent}Description:`];
  if (canonicalKill) lore.push(`${accent}| &fKill ${accent}%player% To`);
  if (canonicalGain) lore.push(`${accent}| &fGain Money`);
  for (const line of descLines) {
    lore.push(`${accent}| ${bodyWithPlaceholders(line, accent)}`);
  }
  if (stats.length) lore.push('');
  for (const stat of stats) {
    const presentation = presentStat(stat.label, accent, secondary);
    lore.push(
      `${presentation.color}${presentation.emoji} &f${presentation.label}: ${colorValues(stat.value, presentation.valueColor)}`,
    );
  }

  if (passthrough.length) lore.push(...(passthrough as string[]));
  return { name, lore };
}

function nameHasColor(value: string | undefined): boolean {
  if (!value) return false;
  return /&#[0-9a-fA-F]{6}|&x(?:&[0-9a-fA-F]){6}|§x(?:§[0-9a-fA-F]){6}|§[0-9a-fk-or]|&[0-9a-fk-or]|<[a-z#/]/i.test(
    value,
  );
}

function isBountyTarget(obj: Record<string, unknown>): boolean {
  if (Array.isArray(obj.lore)) return true;
  const hasName = NAME_KEYS.some((key) => typeof obj[key] === 'string');
  if (!hasName) return false;
  return ['material', 'skull', 'texture', 'value', 'base64', 'owner', 'uuid'].some((key) => key in obj);
}

function replaceName(oldValue: string, next: string, accent: string, color: boolean): string {
  if (nameHasColor(next)) return next;
  if (color) return `${ampHex(accent)}${next}`;
  const leading = oldValue.match(/^(?:&#[0-9a-fA-F]{6}|&x(?:&[0-9a-fA-F]){6}|§x(?:§[0-9a-fA-F]){6}|&[0-9a-fk-or]|§[0-9a-fk-or])+/i);
  return `${leading ? leading[0] : ''}${next}`;
}

function transformNode(value: unknown, walk: Walk): unknown {
  if (Array.isArray(value)) return value.map((entry) => transformNode(entry, walk));
  if (!value || typeof value !== 'object') return value;

  const obj = value as Record<string, unknown>;
  if (walk.intent.bounty && isBountyTarget(obj)) {
    const nameKey = NAME_KEYS.find((key) => typeof obj[key] === 'string') ?? 'name';
    const rawName = typeof obj[nameKey] === 'string' ? obj[nameKey] : '%player%';
    const loreRaw = Array.isArray(obj.lore) ? obj.lore : [];
    const built = buildBountyFields(rawName, loreRaw, walk);
    const next: Record<string, unknown> = { ...obj, [nameKey]: built.name, lore: built.lore };
    walk.styled += 1;
    if (walk.intent.renameTo) walk.renamed += 1;
    for (const [key, child] of Object.entries(next)) {
      if (key === 'lore' || key === nameKey) continue;
      next[key] = transformNode(child, walk);
    }
    return next;
  }

  const next: Record<string, unknown> = {};
  for (const [key, child] of Object.entries(obj)) {
    if (typeof child === 'string' && walk.intent.renameTo && !walk.intent.bounty && NAME_KEYS.includes(key)) {
      next[key] = replaceName(child, walk.intent.renameTo, walk.accent, walk.intent.recolour);
      walk.renamed += 1;
    } else {
      next[key] = transformNode(child, walk);
    }
  }
  return next;
}

function coerce(raw: string): string | number | boolean {
  if (/^(true|false)$/i.test(raw)) return raw.toLowerCase() === 'true';
  if (/^-?\d+(?:\.\d+)?$/.test(raw)) return Number(raw);
  return raw;
}

function applyAssignments(value: unknown, assignments: Assignment[], walk: Walk): unknown {
  if (!assignments.length) return value;
  if (Array.isArray(value)) return value.map((entry) => applyAssignments(entry, assignments, walk));
  if (!value || typeof value !== 'object') return value;
  const obj = { ...(value as Record<string, unknown>) };
  for (const [key, child] of Object.entries(obj)) {
    const hit = assignments.find((assignment) => assignment.key.toLowerCase() === key.toLowerCase());
    if (hit && (typeof child === 'string' || typeof child === 'number' || typeof child === 'boolean')) {
      obj[key] = coerce(hit.raw);
      walk.assigned += 1;
    } else {
      obj[key] = applyAssignments(child, assignments, walk);
    }
  }
  return obj;
}

function dumpYaml(doc: unknown): string {
  const text = yaml.dump(doc, {
    lineWidth: -1,
    noRefs: true,
    quotingType: "'",
    forceQuotes: true,
    sortKeys: false,
  });
  return text.endsWith('\n') ? text : `${text}\n`;
}

export function unwrapModelYaml(raw: string): string {
  const fenced = raw.match(/```(?:ya?ml)?\s*([\s\S]*?)```/i);
  const body = (fenced ? fenced[1] : raw).trim();
  return body.endsWith('\n') ? body : `${body}\n`;
}

function unique(notes: string[]): string[] {
  return [...new Set(notes.filter(Boolean))];
}

export function rewriteConfig(input: string, options: RewriteOptions): RewriteResult {
  const instruction = options.instruction.trim();
  const intent = parseIntent(instruction);
  const accent = intent.accentOverride ?? normalizeHex(options.accent);
  const secondary = normalizeHex(options.secondary, '#94ff00');
  const notes: string[] = [];

  if (!input.trim()) {
    return { ok: false, output: '', notes: ['Paste a YAML config first.'], source: 'local' };
  }

  const structural = intent.bounty || Boolean(intent.renameTo) || intent.assignments.length > 0;
  if (!structural && !intent.recolour && !intent.smallLabels) {
    return {
      ok: false,
      output: input,
      notes: [
        'No matching local action. Try a preset, or ask for a bounty restyle, a recolour, small-font labels, or a rename.',
      ],
      source: 'local',
    };
  }

  let text = input.endsWith('\n') ? input : `${input}\n`;
  const walk: Walk = { intent, accent, secondary, styled: 0, assigned: 0, renamed: 0 };

  if (structural) {
    let doc: unknown;
    try {
      doc = yaml.load(input);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Invalid YAML';
      return {
        ok: false,
        output: input,
        notes: [`Could not parse YAML (${message}). Recolour and small-font presets edit the text in place.`],
        source: 'local',
      };
    }
    if (doc === null || typeof doc !== 'object') {
      return { ok: false, output: input, notes: ['Expected a YAML object or list.'], source: 'local' };
    }
    let next = applyAssignments(doc, intent.assignments, walk);
    next = transformNode(next, walk);
    if (walk.styled === 0 && walk.assigned === 0 && walk.renamed === 0) {
      notes.push('No item name or lore block matched that instruction. The config was left unchanged.');
    } else {
      text = dumpYaml(next);
      if (walk.styled > 0) {
        notes.push(walk.styled === 1 ? 'Rebuilt 1 item in bounty style.' : `Rebuilt ${walk.styled} items in bounty style.`);
      }
      if (walk.renamed > 0 && intent.renameTo) notes.push(`Set the name to ${intent.renameTo}.`);
      if (walk.assigned > 0) notes.push(`Updated ${walk.assigned} matching ${walk.assigned === 1 ? 'key' : 'keys'}.`);
    }
  } else {
    notes.push('Edited the config in place.');
  }

  if (intent.smallLabels) {
    const next = smallLabelText(text);
    if (next !== text) notes.push('Converted bracket labels to small-font.');
    text = next;
  }

  if (intent.recolour && !intent.bounty) {
    text = recolourText(text, accent);
    notes.push(`Recoloured accents to ${accent}. White, grey, and format codes were left alone.`);
  }

  return { ok: true, output: text.endsWith('\n') ? text : `${text}\n`, notes: unique(notes), source: 'local' };
}
