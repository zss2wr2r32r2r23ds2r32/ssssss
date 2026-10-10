import { mkdir } from 'node:fs/promises';
import { build } from 'esbuild';
import { spawnSync } from 'node:child_process';

await mkdir('build', { recursive: true });

const icon = spawnSync(
  'python3',
  [
    '-c',
    `
from PIL import Image
im = Image.open('launcher/public/logo.png').convert('RGBA')
side = max(im.size)
canvas = Image.new('RGBA', (side, side), (0, 0, 0, 0))
canvas.paste(im, ((side - im.width) // 2, (side - im.height) // 2), im)
canvas.save('build/icon.ico', sizes=[(256, 256), (128, 128), (64, 64), (48, 48), (32, 32), (16, 16)])
`,
  ],
  { stdio: 'inherit' },
);
if (icon.status !== 0) {
  console.error('Could not write build/icon.ico. Install Pillow (pip install pillow).');
  process.exit(icon.status || 1);
}

await build({
  entryPoints: ['server/src/index.js'],
  bundle: true,
  platform: 'node',
  format: 'cjs',
  outfile: 'electron/server.cjs',
  external: ['electron'],
  logLevel: 'info',
});
