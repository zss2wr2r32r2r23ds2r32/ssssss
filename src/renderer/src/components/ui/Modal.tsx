import { motion } from 'framer-motion'
import type { ReactNode } from 'react'

export function Modal({
  title,
  children,
  onClose,
  actions
}: {
  title: string
  children: ReactNode
  onClose: () => void
  actions?: ReactNode
}) {
  return (
    <div className="modal-back" onClick={onClose}>
      <motion.div
        className="modal"
        onClick={(event) => event.stopPropagation()}
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
      >
        <h3>{title}</h3>
        <div>{children}</div>
        <div className="row" style={{ marginTop: 16, justifyContent: 'flex-end' }}>
          {actions}
        </div>
      </motion.div>
    </div>
  )
}
