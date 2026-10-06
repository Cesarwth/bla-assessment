import { setupServer } from 'msw/node'

// No default handlers on purpose: every test states the API behavior it depends on, and any request
// it forgot to mock fails loudly (see setup.ts).
export const server = setupServer()
