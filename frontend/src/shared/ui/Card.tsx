import type { ComponentPropsWithoutRef } from 'react'
import { cx } from './cx'

interface Props extends ComponentPropsWithoutRef<'div'> {
  as?: 'div' | 'article' | 'section'
  /** A soft shadow, for cards that stand out from the page. */
  raised?: boolean
  /** Lifts on hover and keyboard focus; for cards that are one big link. */
  interactive?: boolean
  /** The rounder variant used for the main panel of a page. */
  large?: boolean
}

export function Card({
  as: Tag = 'div',
  raised = false,
  interactive = false,
  large = false,
  className,
  ...rest
}: Props) {
  return (
    <Tag
      className={cx(
        'border border-border bg-surface',
        (raised || interactive) && 'shadow-sm',
        large ? 'rounded-lg' : 'rounded-md',
        interactive &&
          'transition-[transform,box-shadow] duration-150 hover:-translate-y-0.5 hover:shadow-md ' +
            'focus-within:-translate-y-0.5 focus-within:shadow-md',
        className,
      )}
      {...rest}
    />
  )
}
