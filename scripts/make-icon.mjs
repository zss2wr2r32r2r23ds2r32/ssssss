import { deflateSync } from 'node:zlib';
import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';

function crc32(buf) {
  let c = ~0;
  for (let i = 0; i < buf.length; i++) {
    c ^= buf[i];
    for (let k = 0; k < 8; k++) c = (c >>> 1) ^ (0xedb88320 & -(c & 1));
  }
  return ~c >>> 0;
}

function chunk(type, data) {
  const typeBuf = Buffer.from(type);
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length, 0);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])), 0);
  return Buffer.concat([len, typeBuf, data, crc]);
}

function makePng(size, pixels) {
  const sig = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0);
  ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8;
  ihdr[9] = 6;
  const raw = Buffer.alloc((size * 4 + 1) * size);
  for (let y = 0; y < size; y++) {
    const row = y * (size * 4 + 1);
    raw[row] = 0;
    pixels.copy(raw, row + 1, y * size * 4, (y + 1) * size * 4);
  }
  return Buffer.concat([
    sig,
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw)),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

function pngToIco(png) {
  const header = Buffer.alloc(6);
  header.writeUInt16LE(0, 0);
  header.writeUInt16LE(1, 2);
  header.writeUInt16LE(1, 4);
  const entry = Buffer.alloc(16);
  entry.writeUInt8(0, 0);
  entry.writeUInt8(0, 1);
  entry.writeUInt8(0, 2);
  entry.writeUInt8(0, 3);
  entry.writeUInt16LE(1, 4);
  entry.writeUInt16LE(32, 6);
  entry.writeUInt32LE(png.length, 8);
  entry.writeUInt32LE(22, 12);
  return Buffer.concat([header, entry, png]);
}

function sdRoundRect(px, py, half, radius) {
  const x = Math.abs(px) - (half - radius);
  const y = Math.abs(py) - (half - radius);
  return Math.hypot(Math.max(x, 0), Math.max(y, 0)) + Math.min(Math.max(x, y), 0) - radius;
}

function side(px, py, x1, y1, x2, y2) {
  return (x2 - x1) * (py - y1) - (y2 - y1) * (px - x1);
}

function inTri(px, py, a, b, c) {
  const d1 = side(px, py, a[0], a[1], b[0], b[1]);
  const d2 = side(px, py, b[0], b[1], c[0], c[1]);
  const d3 = side(px, py, c[0], c[1], a[0], a[1]);
  const hasNeg = d1 < 0 || d2 < 0 || d3 < 0;
  const hasPos = d1 > 0 || d2 > 0 || d3 > 0;
  return !(hasNeg && hasPos);
}

function sample(px, py) {
  const x = px - 128;
  const y = py - 128;
  if (sdRoundRect(x, y, 116, 54) > 0) return [0, 0, 0, 0];

  const apexY = 46;
  const baseY = 208;
  const halfBase = 74;
  const outer = inTri(px, py, [128, apexY], [128 - halfBase, baseY], [128 + halfBase, baseY]);
  if (!outer) return [255, 0, 0, 255];

  const span = baseY - apexY;
  const t = (py - apexY) / span;
  const inset = 27;
  const innerLeft = 128 - halfBase * t + inset;
  const innerRight = 128 + halfBase * t - inset;
  const inCounter = t > 0.34 && py > apexY && py < baseY - 2 && px > innerLeft && px < innerRight;
  const inBar = py >= 136 && py <= 160;
  if (inCounter && !inBar) return [255, 0, 0, 255];
  return [255, 255, 255, 255];
}

const size = 256;
const pixels = Buffer.alloc(size * size * 4);
const offsets = [0.25, 0.75];
for (let y = 0; y < size; y++) {
  for (let x = 0; x < size; x++) {
    let r = 0;
    let g = 0;
    let b = 0;
    let a = 0;
    for (const oy of offsets) {
      for (const ox of offsets) {
        const [sr, sg, sb, sa] = sample(x + ox, y + oy);
        r += sr;
        g += sg;
        b += sb;
        a += sa;
      }
    }
    const i = (y * size + x) * 4;
    pixels[i] = Math.round(r / 4);
    pixels[i + 1] = Math.round(g / 4);
    pixels[i + 2] = Math.round(b / 4);
    pixels[i + 3] = Math.round(a / 4);
  }
}

const png = makePng(size, pixels);
const outDir = path.join(process.cwd(), 'build');
mkdirSync(outDir, { recursive: true });
writeFileSync(path.join(outDir, 'icon.png'), png);
writeFileSync(path.join(outDir, 'icon.ico'), pngToIco(png));
console.log('Wrote build/icon.png and build/icon.ico');
