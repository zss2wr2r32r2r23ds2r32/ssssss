import type { NauticalPreloadApi } from '../../preload/index'

declare global {
  interface Window {
    nautical: NauticalPreloadApi
    nauticalOverlay?: {
      onSettings: (listener: (settings: unknown) => void) => () => void
    }
  }
}

export {}
