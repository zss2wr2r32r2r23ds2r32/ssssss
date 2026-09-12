import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { getActiveProfile } from '../../../shared/defaults'
import type { AppConfig, LaunchStatus, NavPage, Profile, ToastPayload } from '../../../shared/types'
import { api, onConfig, onStatus, onToast } from '../lib/api'

interface AppState {
  ready: boolean
  config: AppConfig | null
  profile: Profile | null
  page: NavPage
  status: LaunchStatus
  toasts: ToastPayload[]
  setPage: (page: NavPage) => void
  refresh: () => Promise<void>
  setConfig: (config: AppConfig) => void
  pushToast: (toast: Omit<ToastPayload, 'id'> & { id?: string }) => void
  dismissToast: (id: string) => void
}

const Ctx = createContext<AppState | null>(null)

export function AppProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false)
  const [config, setConfig] = useState<AppConfig | null>(null)
  const [page, setPage] = useState<NavPage>('home')
  const [status, setStatus] = useState<LaunchStatus>('NOT_RUNNING')
  const [toasts, setToasts] = useState<ToastPayload[]>([])

  const refresh = async () => {
    try {
      const next = await api.getConfig()
      setConfig(next)
      const current = await api.status()
      setStatus(current)
    } catch (error) {
      pushToast({
        tone: 'error',
        title: 'Bridge unavailable',
        body: error instanceof Error ? error.message : 'Could not talk to the main process.'
      })
    } finally {
      setReady(true)
    }
  }

  const pushToast = (toast: Omit<ToastPayload, 'id'> & { id?: string }) => {
    const id = toast.id ?? `t-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`
    setToasts((current) => [...current.slice(-4), { ...toast, id }])
    setTimeout(() => dismissToast(id), 5200)
  }

  const dismissToast = (id: string) => {
    setToasts((current) => current.filter((toast) => toast.id !== id))
  }

  useEffect(() => {
    void refresh()
    const offStatus = onStatus((event) => {
      setStatus(event.status)
    })
    const offToast = onToast((toast) => pushToast(toast))
    const offConfig = onConfig((next) => setConfig(next))
    return () => {
      offStatus()
      offToast()
      offConfig()
    }
  }, [])

  const value = useMemo<AppState>(
    () => ({
      ready,
      config,
      profile: config ? getActiveProfile(config) : null,
      page,
      status,
      toasts,
      setPage,
      refresh,
      setConfig,
      pushToast,
      dismissToast
    }),
    [ready, config, page, status, toasts]
  )

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export function useApp(): AppState {
  const ctx = useContext(Ctx)
  if (!ctx) throw new Error('useApp must be used within AppProvider')
  return ctx
}
