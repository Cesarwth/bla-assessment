import { ApiError } from './errors'

const API_PREFIX = '/api/v1'

interface ClientConfig {
  getToken: () => string | null
  /** Called when the API rejects a request that carried a token (expired or revoked session). */
  onUnauthorized: () => void
}

const defaults: ClientConfig = { getToken: () => null, onUnauthorized: () => undefined }
let config: ClientConfig = defaults

export function configureClient(next: Partial<ClientConfig>): void {
  config = { ...config, ...next }
}

export function resetClient(): void {
  config = defaults
}

function isErrorBody(body: unknown): body is { code: string; message: string; fieldErrors?: unknown } {
  return (
    typeof body === 'object' &&
    body !== null &&
    typeof (body as { code?: unknown }).code === 'string' &&
    typeof (body as { message?: unknown }).message === 'string'
  )
}

async function toApiError(response: Response): Promise<ApiError> {
  try {
    const body: unknown = await response.json()
    if (isErrorBody(body)) {
      const fieldErrors = Array.isArray(body.fieldErrors) ? body.fieldErrors : []
      return new ApiError(response.status, body.code, body.message, fieldErrors)
    }
  } catch {
    // not JSON (a proxy error page, for instance): fall through to the generic error
  }
  return new ApiError(response.status, `HTTP_${response.status}`, `Something went wrong (HTTP ${response.status})`)
}

async function exchange<T>(
  method: string,
  path: string,
  body?: unknown,
  signal?: AbortSignal,
): Promise<{ status: number; data: T }> {
  const token = config.getToken()
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`

  let response: Response
  try {
    // Absolute URL on the page's own origin: the dev server and nginx both proxy /api to the backend.
    response = await fetch(new URL(API_PREFIX + path, window.location.origin), {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    })
  } catch (cause) {
    if (signal?.aborted) throw cause
    throw new ApiError(0, 'NETWORK_ERROR', 'Cannot reach the server. Check your connection and try again.')
  }

  if (response.status === 204) return { status: 204, data: null as T }
  if (!response.ok) {
    if (response.status === 401 && token) config.onUnauthorized()
    throw await toApiError(response)
  }
  return { status: response.status, data: (await response.json()) as T }
}

async function request<T>(method: string, path: string, body?: unknown, signal?: AbortSignal): Promise<T> {
  return (await exchange<T>(method, path, body, signal)).data
}

/** The only module that touches fetch: features call these and receive typed data or an {@link ApiError}. */
export const api = {
  get: <T>(path: string, signal?: AbortSignal) => request<T>('GET', path, undefined, signal),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body ?? {}),
  /** For endpoints whose status carries meaning, e.g. 201 created vs 200 refreshed. */
  postWithStatus: <T>(path: string, body?: unknown) => exchange<T>('POST', path, body ?? {}),
  patch: <T>(path: string, body: unknown) => request<T>('PATCH', path, body),
  delete: (path: string) => request<null>('DELETE', path),
}
