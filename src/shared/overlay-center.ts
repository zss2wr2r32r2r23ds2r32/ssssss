export interface PixelRect {
  x: number
  y: number
  width: number
  height: number
}

export interface OverlayPlacement {
  x: number
  y: number
  width: number
  height: number
}

export function overlayMarkSize(crosshairSize: number, min = 72, max = 160): number {
  return Math.max(min, Math.min(max, Math.round(crosshairSize * 2 + 28)))
}

/** Geometric center of a DIP or pixel rect, minus half the overlay size. */
export function centerOverlayOnRect(rect: PixelRect, markSize: number): OverlayPlacement {
  const width = Math.max(1, Math.round(markSize))
  const height = width
  return {
    x: Math.round(rect.x + rect.width / 2 - width / 2),
    y: Math.round(rect.y + rect.height / 2 - height / 2),
    width,
    height
  }
}

export function rectFromCorners(left: number, top: number, right: number, bottom: number): PixelRect {
  return {
    x: left,
    y: top,
    width: Math.max(1, right - left),
    height: Math.max(1, bottom - top)
  }
}
