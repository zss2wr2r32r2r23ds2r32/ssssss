import { motion } from 'framer-motion';
import type { ReactNode } from 'react';

export function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  return (
    <div className="modal-back" onMouseDown={onClose} role="presentation">
      <motion.div
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onMouseDown={(event) => event.stopPropagation()}
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.2 }}
      >
        <header className="modal-head">
          <h2>{title}</h2>
          <button type="button" className="icon-x" onClick={onClose} aria-label="Close">
            ×
          </button>
        </header>
        {children}
      </motion.div>
    </div>
  );
}
