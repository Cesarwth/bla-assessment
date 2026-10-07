import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { expectConsoleOutput } from '../test/console'
import { ErrorBoundary } from './ErrorBoundary'

function Boom({ explode }: { explode: boolean }) {
  if (explode) throw new Error('Boom from a component')
  return <p>All good</p>
}

function Harness() {
  const [explode, setExplode] = useState(true)
  return (
    <>
      <button onClick={() => setExplode(false)}>fix it</button>
      <ErrorBoundary>
        <Boom explode={explode} />
      </ErrorBoundary>
    </>
  )
}

describe('ErrorBoundary', () => {
  it('shows a friendly fallback instead of a blank page when something breaks while rendering', () => {
    render(
      <ErrorBoundary>
        <Boom explode />
      </ErrorBoundary>,
    )

    expect(screen.getByRole('alert')).toHaveTextContent('Something went wrong')
    expect(screen.queryByText(/Boom from a component/)).not.toBeInTheDocument()
    expect(expectConsoleOutput(/Boom from a component/).length).toBeGreaterThan(0) // React reports it, we assert it
  })

  it('renders its children when nothing is wrong', () => {
    render(
      <ErrorBoundary>
        <Boom explode={false} />
      </ErrorBoundary>,
    )

    expect(screen.getByText('All good')).toBeInTheDocument()
  })

  it('lets the user try again once the cause is gone', async () => {
    render(<Harness />)
    expect(screen.getByRole('alert')).toBeInTheDocument()
    expectConsoleOutput(/Boom from a component/)

    await userEvent.click(screen.getByRole('button', { name: 'fix it' }))
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))

    expect(screen.getByText('All good')).toBeInTheDocument()
  })
})
