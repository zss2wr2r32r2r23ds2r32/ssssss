import type { CrosshairSettings } from './types'

export function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value))
}

export function hexToRgba(hex: string, alpha: number): string {
  const clean = hex.replace('#', '')
  const full = clean.length === 3 ? clean.split('').map((c) => c + c).join('') : clean.slice(0, 6)
  const r = parseInt(full.slice(0, 2), 16)
  const g = parseInt(full.slice(2, 4), 16)
  const b = parseInt(full.slice(4, 6), 16)
  return `rgba(${r}, ${g}, ${b}, ${clamp(alpha, 0, 1)})`
}

export interface CrosshairGeometry {
  color: string
  outline: string | null
  outlineWidth: number
  lines: Array<{ x1: number; y1: number; x2: number; y2: number }>
  circles: Array<{ r: number; fill: boolean }>
  dots: Array<{ r: number }>
}

export function buildCrosshair(settings: CrosshairSettings, view = 120): CrosshairGeometry {
  const color = hexToRgba(settings.color, settings.opacity)
  const outline = settings.outline ? hexToRgba(settings.outlineColor, Math.min(1, settings.opacity + 0.05)) : null
  const size = settings.size
  const gap = settings.centerGap ? Math.max(settings.gap, 2) : settings.gap
  const t = Math.max(settings.thickness, settings.shape === 'dot' ? 0 : 1)
  const lines: CrosshairGeometry['lines'] = []
  const circles: CrosshairGeometry['circles'] = []
  const dots: CrosshairGeometry['dots'] = []

  const addCross = (includeDown: boolean, hollow: boolean) => {
    if (settings.horizontal || settings.shape !== 'cross') {
      const yOff = hollow ? -t / 2 : 0
      lines.push({ x1: -size, y1: yOff, x2: -gap, y2: yOff })
      lines.push({ x1: gap, y1: yOff, x2: size, y2: yOff })
    }
    if (settings.vertical || settings.shape !== 'cross') {
      lines.push({ x1: 0, y1: -size, x2: 0, y2: -gap })
      if (includeDown) lines.push({ x1: 0, y1: gap, x2: 0, y2: size })
    }
  }

  switch (settings.shape) {
    case 'dot':
      dots.push({ r: Math.max(1, settings.dotSize || settings.size / 2) })
      break
    case 'cross':
    case 'plus':
    case 'lines':
      addCross(true, false)
      break
    case 'hollow-cross':
      addCross(true, true)
      break
    case 't':
      addCross(false, false)
      lines.push({ x1: -size, y1: 0, x2: size, y2: 0 })
      break
    case 'circle':
      circles.push({ r: size, fill: false })
      break
    case 'dot-circle':
      circles.push({ r: size, fill: false })
      dots.push({ r: Math.max(1, settings.dotSize) })
      break
    case 'custom-image':
      break
    default:
      addCross(true, false)
  }

  if (settings.showDot && settings.shape !== 'dot' && settings.shape !== 'custom-image') {
    dots.push({ r: Math.max(1, settings.dotSize) })
  }

  void view
  return { color, outline, outlineWidth: settings.outlineThickness, lines, circles, dots }
}
