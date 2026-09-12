import { buildCrosshair } from '../../../../shared/crosshair-draw'
import type { CrosshairSettings } from '../../../../shared/types'

export function CrosshairMark({ settings, size = 120 }: { settings: CrosshairSettings; size?: number }) {
  const geo = buildCrosshair(settings, size)
  const mid = size / 2
  return (
    <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} style={{ overflow: 'visible' }}>
      <g transform={`translate(${mid} ${mid}) rotate(${settings.rotation})`}>
        {settings.shape === 'custom-image' && settings.customImage ? (
          <image
            href={settings.customImage}
            x={-settings.size}
            y={-settings.size}
            width={settings.size * 2}
            height={settings.size * 2}
            opacity={settings.opacity}
            preserveAspectRatio="xMidYMid meet"
          />
        ) : null}
        {geo.lines.map((line, index) => (
          <line
            key={`l-${index}`}
            x1={line.x1}
            y1={line.y1}
            x2={line.x2}
            y2={line.y2}
            stroke={geo.outline ?? geo.color}
            strokeWidth={Math.max(settings.thickness, 1) + (geo.outline ? geo.outlineWidth * 2 : 0)}
            strokeLinecap="square"
          />
        ))}
        {geo.lines.map((line, index) => (
          <line
            key={`lf-${index}`}
            x1={line.x1}
            y1={line.y1}
            x2={line.x2}
            y2={line.y2}
            stroke={geo.color}
            strokeWidth={Math.max(settings.thickness, 1)}
            strokeLinecap="square"
          />
        ))}
        {geo.circles.map((circle, index) => (
          <circle
            key={`c-${index}`}
            r={circle.r}
            fill="none"
            stroke={geo.color}
            strokeWidth={Math.max(settings.thickness, 1)}
          />
        ))}
        {geo.dots.map((dot, index) => (
          <circle
            key={`d-${index}`}
            r={dot.r}
            fill={geo.color}
            stroke={geo.outline ?? 'none'}
            strokeWidth={geo.outline ? geo.outlineWidth : 0}
          />
        ))}
      </g>
    </svg>
  )
}
