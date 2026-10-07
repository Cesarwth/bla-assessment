import type { ComponentPropsWithoutRef } from 'react'
import { classNames } from './classNames'

interface Props extends ComponentPropsWithoutRef<'div'> {
  as?: 'div' | 'article' | 'section'
  /** A soft shadow, for cards that stand out from the page. */
  raised?: boolean
  /** Lifts on hover and keyboard focus; for cards that are one big link. */
  interactive?: boolean
  /** The rounder variant used for the main panel of a page. */
  large?: boolean
  /** A translucent, blurred surface for text that sits on the page backdrop instead of on a card. */
  glass?: boolean
}

export function Card({
  as: Tag = 'div',
  raised = false,
  interactive = false,
  large = false,
  glass = false,
  className,
  ...rest
}: Props) {
  return (
    <Tag
      className={classNames(
        'border border-border',
        glass ? 'bg-surface/80 backdrop-blur' : 'bg-surface',
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
