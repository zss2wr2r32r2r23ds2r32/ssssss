import { z } from 'zod'
import { ALLOWED_MACRO_KEYS } from '../../shared/types'

const hexColor = z.string().regex(/^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$/)

export const crosshairSchema = z.object({
  enabled: z.boolean(),
  onlyWhileFortnite: z.boolean(),
  presetId: z.string().min(1).max(64),
  color: hexColor,
  opacity: z.number().min(0).max(1),
  size: z.number().min(1).max(80),
  thickness: z.number().min(0).max(20),
  gap: z.number().min(0).max(40),
  outline: z.boolean(),
  outlineThickness: z.number().min(0).max(8),
  outlineColor: hexColor,
  showDot: z.boolean(),
  dotSize: z.number().min(0).max(20),
  horizontal: z.boolean(),
  vertical: z.boolean(),
  rotation: z.number().min(0).max(360),
  shape: z.enum(['dot', 'cross', 'hollow-cross', 't', 'plus', 'lines', 'circle', 'dot-circle', 'custom-image']),
  centerGap: z.boolean(),
  customImage: z.string().nullable(),
  customImageName: z.string().nullable()
})

export const resolutionSchema = z.object({
  width: z.number().int().min(640).max(7680),
  height: z.number().int().min(480).max(4320),
  method: z.enum(['gpu', 'display', 'automatic', 'fortnite-only']),
  applyOnLaunch: z.boolean(),
  temporary: z.boolean(),
  applyGameUserSettings: z.boolean()
})

export const graphicsSchema = z.object({
  fullscreen: z.boolean(),
  vsync: z.boolean(),
  performanceMode: z.boolean(),
  lowGraphics: z.boolean(),
  fpsLimit: z.union([z.literal('unlimited'), z.number().min(30).max(360)])
})

export const performanceSchema = z.object({
  cleanupOnLaunch: z.boolean(),
  restoreAfterExit: z.boolean(),
  selectedApps: z.array(z.string().min(1).max(64)).max(32)
})

export const macroSchema = z.object({
  enabled: z.boolean(),
  acknowledgedRisk: z.boolean(),
  key: z.string().refine((value) => (ALLOWED_MACRO_KEYS as readonly string[]).includes(value)),
  intervalSec: z.number().min(0.1).max(2),
  mode: z.enum(['hold', 'toggle', 'fortnite-focus']),
  activationKey: z.string().min(1).max(16),
  onlyWhileFortniteFocused: z.boolean(),
  mouseButton: z.enum(['none', 'left', 'right', 'middle', 'wheel-up', 'wheel-down'])
})

export const profilePatchSchema = z.object({
  name: z.string().min(1).max(48).optional(),
  crosshair: crosshairSchema.partial().optional(),
  resolution: resolutionSchema.partial().optional(),
  graphics: graphicsSchema.partial().optional(),
  performance: performanceSchema.partial().optional(),
  macro: macroSchema.partial().optional()
})

export const configPatchSchema = z.object({
  wizardCompleted: z.boolean().optional(),
  fortnitePath: z.string().nullable().optional(),
  fortniteVersion: z.string().nullable().optional(),
  activeProfileId: z.string().optional(),
  defaultProfileId: z.string().optional(),
  general: z
    .object({
      startWithWindows: z.boolean().optional(),
      trayEnabled: z.boolean().optional(),
      closeToTray: z.boolean().optional(),
      startMinimized: z.boolean().optional(),
      autoLaunchFortnite: z.boolean().optional(),
      checkUpdates: z.boolean().optional(),
      hardwareAcceleration: z.boolean().optional(),
      discordUrl: z.string().max(300).optional(),
      displayName: z.string().max(32).optional(),
      discordWebhookUrl: z.string().max(400).optional(),
      launchMethod: z.enum(['bootstrapper', 'shipping', 'epic-uri']).optional(),
      hideEpicAfterLaunch: z.boolean().optional()
    })
    .optional(),
  appearance: z
    .object({
      theme: z.literal('dark').optional(),
      accent: hexColor.optional(),
      transparency: z.number().min(0.4).max(1).optional(),
      animationIntensity: z.enum(['off', 'subtle', 'full']).optional()
    })
    .optional(),
  avatar: z
    .object({
      fileName: z.string().max(80).nullable(),
      mime: z.string().max(40).nullable()
    })
    .optional()
})

export const idSchema = z.object({ id: z.string().min(1).max(80) })
export const nameSchema = z.object({ name: z.string().min(1).max(48) })
export const pathSchema = z.object({ path: z.string().min(1).max(500) })
export const urlSchema = z.object({ url: z.string().url() })
export const cleanupSchema = z.object({ ids: z.array(z.string().min(1).max(64)).max(32) })
export const resolutionApplySchema = z.object({
  settings: resolutionSchema,
  permanent: z.boolean()
})
export const testResolutionSchema = z.object({
  width: z.number().int().min(640).max(7680),
  height: z.number().int().min(480).max(4320)
})
export const savePresetSchema = z.object({
  name: z.string().min(1).max(40)
})
