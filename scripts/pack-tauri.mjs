import { spawn } from 'node:child_process';
import { createReadStream } from 'node:fs';
import { copyFile, mkdir, open, readdir, stat } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const MAGIC = Buffer.from('NEXAPAY1');
const nodeExe = process.env.NEXA_NODE_EXE || '/tmp/nexa-pack/node.exe';

function run(command, args, cwd) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { cwd, stdio: 'inherit' });
    child.on('error', reject);
    child.on('exit', (code) => {
      if (code === 0) resolve();
      else reject(new Error(`${command} exited ${code}`));
    });
  });
}

async function filesIn(dir, prefix, list) {
  const entries = await readdir(dir, { withFileTypes: true });
  for (const entry of entries) {
    const abs = path.join(dir, entry.name);
    const rel = prefix ? `${prefix}/${entry.name}` : entry.name;
    if (entry.isDirectory()) await filesIn(abs, rel, list);
    else if (entry.isFile()) list.push({ abs, rel: rel.replace(/\\/g, '/') });
  }
}

async function appendEntry(handle, rel, abs) {
  const info = await stat(abs);
  const name = Buffer.from(rel);
  if (name.length === 0 || name.length > 65535) throw new Error(`Bad runtime path ${rel}`);
  const header = Buffer.alloc(2 + name.length + 8);
  header.writeUInt16LE(name.length, 0);
  name.copy(header, 2);
  header.writeBigUInt64LE(BigInt(info.size), 2 + name.length);
  await handle.write(header);
  let written = 0;
  for await (const chunk of createReadStream(abs)) {
    await handle.write(chunk);
    written += chunk.length;
  }
  if (written !== info.size) throw new Error(`Short read for ${rel}`);
  return header.length + written;
}

const built = path.join(root, 'src-tauri', 'target', 'x86_64-pc-windows-msvc', 'release', 'Nexa.exe');
const releaseDir = path.join(root, 'release');
const dest = path.join(releaseDir, 'Nexa.exe');

await run(
  'cargo',
  ['xwin', 'build', '--release', '--target', 'x86_64-pc-windows-msvc', '--manifest-path', path.join(root, 'src-tauri', 'Cargo.toml')],
  root,
);

const entries = [
  { abs: nodeExe, rel: 'node.exe' },
  { abs: path.join(root, 'src-tauri', 'embed', 'server.cjs'), rel: 'server.cjs' },
];
await filesIn(path.join(root, 'launcher', 'dist'), 'ui', entries);

await mkdir(releaseDir, { recursive: true });
await copyFile(built, dest);
const handle = await open(dest, 'a');
let payloadLen = 0;
for (const entry of entries) {
  payloadLen += await appendEntry(handle, entry.rel, entry.abs);
}
const footer = Buffer.alloc(16);
MAGIC.copy(footer, 0);
footer.writeBigUInt64LE(BigInt(payloadLen), 8);
await handle.write(footer);
await handle.close();

const packed = await stat(dest);
console.log(`Packed ${dest} (${packed.size} bytes, payload ${payloadLen})`);
