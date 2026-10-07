import type { ReactNode } from 'react'
import { classNames } from './classNames'

/**
 * An inline error announced to screen readers (form-level errors). The element is always in the page and only gets
 * its box and text when there is something to say: assistive technology announces changes inside a live region, but
 * often misses a region that is inserted together with its text. Empty, it is visually hidden and out of the layout.
 */
export function Alert({ children, className }: { children?: ReactNode; className?: string }) {
  return (
    <p
      role="alert"
      className={classNames(
        'mb-0',
        children ? 'rounded-sm border border-danger bg-danger-surface px-4 py-3' : 'sr-only',
        children ? className : undefined,
      )}
    >
      {children}
    </p>
  )
}
