import fs from 'node:fs';
import path from 'node:path';

function applyEnvFile(file) {
  let text = '';
  try {
    text = fs.readFileSync(file, 'utf8');
  } catch {
    return;
  }
  for (const line of text.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const eq = trimmed.indexOf('=');
    if (eq < 1) continue;
    const key = trimmed.slice(0, eq).trim();
    if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(key)) continue;
    if (process.env[key]) continue;
    let value = trimmed.slice(eq + 1).trim();
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1);
    }
    process.env[key] = value;
  }
}

export function loadEnv() {
  const candidates = [
    path.join(process.cwd(), 'server', '.env'),
    path.join(process.cwd(), '.env'),
    path.join(process.cwd(), '..', 'server', '.env'),
    path.join(path.dirname(process.execPath), '.env'),
  ];
  for (const file of candidates) applyEnvFile(file);
}
