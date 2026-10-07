import type { ComponentPropsWithoutRef } from 'react'
import { classNames } from './classNames'

interface Props extends Omit<ComponentPropsWithoutRef<'button'>, 'children'> {
  /** Required: the glyph is decoration, the label is the accessible name. */
  'aria-label': string
  /** The glyph is drawn by the button; "×" for remove / clear / dismiss. */
  glyph?: string
}

export function IconButton({ glyph = '×', className, type = 'button', ...rest }: Props) {
  return (
    <button
      type={type}
      className={classNames(
        'inline-flex size-11 shrink-0 items-center justify-center rounded-full leading-none ' +
          'text-xl text-muted transition-colors not-disabled:hover:bg-border not-disabled:hover:text-fg ' +
          'disabled:opacity-60 sm:size-8',
        className,
      )}
      {...rest}
    >
      <span aria-hidden="true">{glyph}</span>
    </button>
  )
}
