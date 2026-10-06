import { http, HttpResponse, type HttpHandler } from 'msw'
import type { FieldError } from '../shared/api/errors'
import type { PageResponse, PokemonDetail, PokemonSummary } from '../shared/api/types'
import { server } from './server'

export const apiUrl = (path: string) => `*/api/v1${path}`

/** The error body the backend sends for every failure. */
export function errorBody(status: number, code: string, message: string, fieldErrors: FieldError[] = []) {
  return { timestamp: '2026-10-06T00:00:00Z', status, code, message, fieldErrors }
}

export function failWith(status: number, code: string, message: string, fieldErrors: FieldError[] = []) {
  return HttpResponse.json(errorBody(status, code, message, fieldErrors), { status })
}

/** Answers GET /pokemon and records the query string of every request in `requests`. */
export function mockCatalog(
  respond: (params: URLSearchParams) => PageResponse<PokemonSummary> | Response | Promise<PageResponse<PokemonSummary> | Response>,
  requests: string[] = [],
): HttpHandler {
  const handler = http.get(apiUrl('/pokemon'), async ({ request }) => {
    const params = new URL(request.url).searchParams
    requests.push(params.toString())
    const result = await respond(params)
    return result instanceof Response ? result : HttpResponse.json(result)
  })
  server.use(handler)
  return handler
}

export function mockDetail(idOrName: string, detail: PokemonDetail | Response): void {
  server.use(
    http.get(apiUrl(`/pokemon/${idOrName}`), () =>
      detail instanceof Response ? detail : HttpResponse.json(detail),
    ),
  )
}
