import { app } from 'electron'

let quitting = false

export function isAppQuitting(): boolean {
  return quitting
}

export function markQuitting(): void {
  quitting = true
}

export function requestQuit(): void {
  quitting = true
  app.quit()
}
