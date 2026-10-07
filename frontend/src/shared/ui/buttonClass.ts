import { classNames } from './classNames'

export type ButtonVariant = 'primary' | 'secondary' | 'outline' | 'danger' | 'danger-outline'
export type ButtonSize = 'md' | 'lg'

// Light on purpose: 1px borders, no shadow, soft fills, medium weights. 44px tall on phones (touch target),
// natural height from the breakpoint where a pointer is the norm. A disabled button also gets a dashed border, so
// it differs by more than its opacity. The ring on keyboard focus comes from the global :focus-visible rule.
const base =
  'inline-flex min-h-11 items-center justify-center rounded-full border text-center no-underline ' +
  'transition-colors duration-150 disabled:cursor-default disabled:border-dashed disabled:opacity-50 ' +
  'aria-disabled:pointer-events-none aria-disabled:border-dashed aria-disabled:opacity-50 sm:min-h-0'

const variants: Record<ButtonVariant, string> = {
  // The one call to action of a screen: a soft red tint with red ink.
  primary:
    'border-primary/70 bg-primary-soft font-semibold text-primary-ink ' +
    'not-disabled:hover:border-primary not-disabled:hover:bg-primary-soft-hover',
  // Glass: translucent theme surface over whatever is behind it.
  secondary:
    'border-fg/20 bg-surface/80 font-medium text-fg backdrop-blur-sm not-disabled:hover:bg-surface-muted',
  outline:
    'border-primary/50 bg-surface/80 font-medium text-fg backdrop-blur-sm ' +
    'not-disabled:hover:bg-primary-soft',
  danger: 'border-danger bg-surface font-semibold text-danger not-disabled:hover:bg-danger-surface',
  'danger-outline':
    'border-danger/50 bg-surface/80 font-medium text-danger backdrop-blur-sm ' +
    'not-disabled:hover:bg-danger-surface',
}

const sizes: Record<ButtonSize, string> = {
  md: 'px-4 py-1.5',
  lg: 'px-5 py-2.5',
}

/** Classes of a button, also for links that must look like one (header, pagination). */
export const buttonClass = (variant: ButtonVariant = 'secondary', size: ButtonSize = 'md') =>
  classNames(base, variants[variant], sizes[size])
