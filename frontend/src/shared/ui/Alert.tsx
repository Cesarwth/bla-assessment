import type { ReactNode } from 'react'
import { cx } from './cx'

/** An inline error announced to screen readers (form-level errors). */
export function Alert({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <p
      role="alert"
      className={cx('mb-0 rounded-sm border border-danger bg-danger-surface px-4 py-3', className)}
    >
      {children}
    </p>
  )
}
