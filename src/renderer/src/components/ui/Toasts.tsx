import { AnimatePresence, motion } from 'framer-motion'
import type { ToastPayload } from '../../../../shared/types'

export function Toasts({
  toasts,
  onDismiss,
  reducedMotion = false
}: {
  toasts: ToastPayload[]
  onDismiss: (id: string) => void
  reducedMotion?: boolean
}) {
  return (
    <div className="toasts">
      {reducedMotion ? (
        toasts.map((toast) => (
          <button key={toast.id} type="button" className={`toast ${toast.tone}`} onClick={() => onDismiss(toast.id)}>
            <strong>{toast.title}</strong>
            {toast.body ? <div className="hint">{toast.body}</div> : null}
          </button>
        ))
      ) : (
        <AnimatePresence>
          {toasts.map((toast) => (
            <motion.button
              key={toast.id}
              className={`toast ${toast.tone}`}
              initial={{ opacity: 0, y: 12, scale: 0.98 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: 8 }}
              onClick={() => onDismiss(toast.id)}
            >
              <strong>{toast.title}</strong>
              {toast.body ? <div className="hint">{toast.body}</div> : null}
            </motion.button>
          ))}
        </AnimatePresence>
      )}
    </div>
  )
}
