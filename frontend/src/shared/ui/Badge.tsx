import type { ReactNode } from 'react'
import { classNames } from './classNames'

const tones = {
  primary: 'bg-primary px-2 py-0.5 text-primary-contrast',
  success: 'bg-success-surface px-3 py-1 text-fg',
}

export function Badge({
  tone,
  className,
  children,
}: {
  tone: keyof typeof tones
  className?: string
  children: ReactNode
}) {
  return (
    <span className={classNames('rounded-full text-xs font-bold', tones[tone], className)}>
      {children}
    </span>
  )
}
