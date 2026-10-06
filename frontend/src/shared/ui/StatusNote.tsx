import type { ReactNode } from 'react'
import { cx } from './cx'

/**
 * A polite confirmation ("Saved") for screen readers. Like `Alert`, the live region is always rendered and only
 * shows a box once it has text, so the announcement is reliable. `boxClass` is what it looks like with text.
 */
export function StatusNote({
  children,
  boxClass = 'rounded-sm border border-border bg-surface-muted px-4 py-3',
  className,
}: {
  children?: ReactNode
  boxClass?: string
  className?: string
}) {
  return (
    <p role="status" className={cx('mb-0', children ? cx(boxClass, className) : 'sr-only')}>
      {children}
    </p>
  )
}
