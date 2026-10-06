import { cx } from './cx'

export type ButtonVariant = 'primary' | 'secondary' | 'outline' | 'danger' | 'danger-outline'
export type ButtonSize = 'md' | 'lg'

// 44px tall on phones (touch target), natural height from the breakpoint where a pointer is the norm.
const base =
  'inline-flex min-h-11 items-center justify-center rounded-full text-center no-underline ' +
  'disabled:cursor-default disabled:opacity-70 aria-disabled:pointer-events-none aria-disabled:opacity-55 sm:min-h-0'

const variants: Record<ButtonVariant, string> = {
  primary:
    'border border-primary bg-primary text-primary-contrast not-disabled:hover:bg-primary-hover',
  secondary: 'border border-border bg-surface text-fg not-disabled:hover:bg-surface-muted',
  outline: 'border border-primary bg-transparent text-fg not-disabled:hover:bg-surface-muted',
  danger: 'border border-danger bg-danger text-surface',
  'danger-outline': 'border border-danger bg-surface text-danger',
}

const sizes: Record<ButtonSize, string> = {
  md: 'px-4 py-2 font-semibold',
  lg: 'px-6 py-3 font-bold',
}

/** Classes of a button, also for links that must look like one (header, pagination). */
export const buttonClass = (variant: ButtonVariant = 'secondary', size: ButtonSize = 'md') =>
  cx(base, variants[variant], sizes[size])
