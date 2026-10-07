import type { ComponentPropsWithoutRef } from 'react'
import { buttonClass, type ButtonSize, type ButtonVariant } from './buttonClass'
import { classNames } from './classNames'

interface Props extends ComponentPropsWithoutRef<'button'> {
  variant?: ButtonVariant
  size?: ButtonSize
}

/** `className` is for layout only (margins, width): colors and shape come from `variant` and `size`. */
export function Button({ variant, size, className, type = 'button', ...rest }: Props) {
  return (
    <button type={type} className={classNames(buttonClass(variant, size), className)} {...rest} />
  )
}
