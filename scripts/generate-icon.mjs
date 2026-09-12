import { writeFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import zlib from 'node:zlib'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const pngPath = path.join(root, 'resources', 'icon.png')
const icoPath = path.join(root, 'resources', 'icon.ico')

function crc32(buf) {
  let c = ~0
  for (const byte of buf) {
    c ^= byte
    for (let i = 0; i < 8; i++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
  }
  return ~c >>> 0
}

function chunk(type, data) {
  const typeBuf = Buffer.from(type)
  const len = Buffer.alloc(4)
  len.writeUInt32BE(data.length)
  const crc = Buffer.alloc(4)
  crc.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])))
  return Buffer.concat([len, typeBuf, data, crc])
}

function pixel(r, g, b, a = 255) {
  return Buffer.from([r, g, b, a])
}

function makePng(size) {
  const raw = []
  const cx = (size - 1) / 2
  for (let y = 0; y < size; y++) {
    raw.push(0)
    for (let x = 0; x < size; x++) {
      const dx = x - cx
      const dy = y - cx
      const dist = Math.sqrt(dx * dx + dy * dy) / cx
      const corner = Math.max(Math.abs(dx), Math.abs(dy)) / cx
      if (corner > 0.92) {
        raw.push(...pixel(0, 0, 0, 0))
        continue
      }
      if (dist > 0.78) {
        raw.push(...pixel(7, 16, 24, 255))
        continue
      }
      const t = Math.min(1, dist)
      const r = Math.round(122 + (20 - 122) * t)
      const g = Math.round(230 + (80 - 230) * t)
      const b = Math.round(255 + (160 - 255) * t)
      const ring = Math.abs(dist - 0.62) < 0.04 || (Math.abs(dx) < size * 0.02 && Math.abs(dy) > size * 0.22) || (Math.abs(dy) < size * 0.02 && Math.abs(dx) > size * 0.22)
      if (ring) {
        raw.push(...pixel(215, 247, 255, 255))
      } else if (dist < 0.08) {
        raw.push(...pixel(124, 244, 255, 255))
      } else {
        raw.push(...pixel(r, g, b, 255))
      }
    }
  }
  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(size, 0)
  ihdr.writeUInt32BE(size, 4)
  ihdr[8] = 8
  ihdr[9] = 6
  const compressed = zlib.deflateSync(Buffer.from(raw))
  return Buffer.concat([
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    chunk('IHDR', ihdr),
    chunk('IDAT', compressed),
    chunk('IEND', Buffer.alloc(0))
  ])
}

const png = makePng(256)
writeFileSync(pngPath, png)

const header = Buffer.alloc(6)
header.writeUInt16LE(0, 0)
header.writeUInt16LE(1, 2)
header.writeUInt16LE(1, 4)
const entry = Buffer.alloc(16)
entry[0] = 0
entry[1] = 0
entry[2] = 0
entry[3] = 0
entry.writeUInt16LE(1, 4)
entry.writeUInt16LE(32, 6)
entry.writeUInt32LE(png.length, 8)
entry.writeUInt32LE(22, 12)
writeFileSync(icoPath, Buffer.concat([header, entry, png]))
console.log('Wrote', pngPath, 'and', icoPath)
