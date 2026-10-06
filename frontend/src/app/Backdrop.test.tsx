import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Backdrop } from './Backdrop'

describe('Backdrop', () => {
  it('is decoration: hidden from assistive technology and never the target of a click', () => {
    const { container } = render(<Backdrop />)
    const layer = container.firstElementChild!

    expect(layer).toHaveAttribute('aria-hidden', 'true')
    expect(layer).toHaveClass('pointer-events-none', 'fixed', '-z-10')
    expect(layer.querySelectorAll('span').length).toBeGreaterThan(0)
  })

  it('only animates the fireflies where motion is welcome', () => {
    const { container } = render(<Backdrop />)

    for (const firefly of container.querySelectorAll('span'))
      expect(firefly).toHaveClass('motion-safe:animate-firefly')
  })
})
