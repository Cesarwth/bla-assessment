import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll, beforeEach, expect, vi } from 'vitest'
import * as axeMatchers from 'vitest-axe/matchers'
import { resetClient } from '../shared/api/client'
import { server } from './server'

expect.extend(axeMatchers)

const consoleOutput: string[] = []

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))

beforeEach(() => {
  consoleOutput.length = 0
  sessionStorage.clear()
  resetClient()
  // The brief asks for no warnings in the browser console: React warnings, failed prop types, unhandled
  // errors... all of it makes the test fail instead of scrolling by unnoticed.
  for (const method of ['error', 'warn'] as const) {
    vi.spyOn(console, method).mockImplementation((...args: unknown[]) => {
      consoleOutput.push(`console.${method}: ${args.map(String).join(' ')}`)
    })
  }
})

afterEach(() => {
  cleanup()
  server.resetHandlers()
  if (consoleOutput.length > 0) {
    throw new Error(`Unexpected console output during the test:\n${consoleOutput.join('\n')}`)
  }
})

afterAll(() => server.close())
