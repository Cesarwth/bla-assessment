import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import { MemoryRouter } from 'react-router'
import { AppProviders } from '../app/AppProviders'
import { createQueryClient } from '../app/queryClient'
import { storeToken } from '../features/auth/storage'

interface TokenOptions {
  uid?: number
  sub?: string
  role?: string
  /** Seconds since the epoch; defaults to one hour from now. */
  exp?: number
}

/** UTF-8 first, like the server does: btoa alone would encode 'é' as one Latin-1 byte. */
function base64Url(value: object): string {
  const bytes = new TextEncoder().encode(JSON.stringify(value))
  const binary = Array.from(bytes, (byte) => String.fromCharCode(byte)).join('')
  return btoa(binary).replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', '')
}

/** A token shaped like the ones the API issues (HS256, claims sub/uid/role/exp); the signature is irrelevant here. */
export function makeToken({ uid = 1, sub = 'ash', role = 'USER', exp }: TokenOptions = {}): string {
  const expiresAt = exp ?? Math.floor(Date.now() / 1000) + 3600
  return [base64Url({ alg: 'HS256' }), base64Url({ uid, sub, role, exp: expiresAt }), 'signature'].join('.')
}

interface RenderOptions {
  route?: string
  /** Puts a session in storage before rendering, as if the user had logged in earlier in this tab. */
  token?: string
}

export function renderApp(ui: ReactElement, { route = '/', token }: RenderOptions = {}) {
  if (token) storeToken(token)
  const queryClient = createQueryClient({ retry: false })
  return render(
    <MemoryRouter initialEntries={[route]}>
      <AppProviders queryClient={queryClient}>{ui}</AppProviders>
    </MemoryRouter>,
  )
}
