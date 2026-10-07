import { http, HttpResponse, type HttpHandler } from 'msw'
import type { FieldError } from '../shared/api/errors'
import type {
  PageResponse,
  PokemonDetail,
  PokemonSummary,
  StoredPokemon,
  StoredPokemonSummary,
} from '../shared/api/types'
import { server } from './server'

export const apiUrl = (path: string) => `*/api/v1${path}`

/** The error body the backend sends for every failure. */
export function errorBody(
  status: number,
  code: string,
  message: string,
  fieldErrors: FieldError[] = [],
) {
  return { timestamp: '2026-10-06T00:00:00Z', status, code, message, fieldErrors }
}

export function failWith(
  status: number,
  code: string,
  message: string,
  fieldErrors: FieldError[] = [],
) {
  return HttpResponse.json(errorBody(status, code, message, fieldErrors), { status })
}

/** Answers GET /pokemon and records the query string of every request in `requests`. */
export function mockCatalog(
  respond: (
    searchParams: URLSearchParams,
  ) => PageResponse<PokemonSummary> | Response | Promise<PageResponse<PokemonSummary> | Response>,
  requests: string[] = [],
): HttpHandler {
  const handler = http.get(apiUrl('/pokemon'), async ({ request }) => {
    const searchParams = new URL(request.url).searchParams
    requests.push(searchParams.toString())
    const handlerResult = await respond(searchParams)
    return handlerResult instanceof Response ? handlerResult : HttpResponse.json(handlerResult)
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

/** Answers GET /collection and records the query string of every request in `requests`. */
export function mockCollection(
  respond: (
    searchParams: URLSearchParams,
  ) =>
    | PageResponse<StoredPokemonSummary>
    | Response
    | Promise<PageResponse<StoredPokemonSummary> | Response>,
  requests: string[] = [],
): void {
  server.use(
    http.get(apiUrl('/collection'), async ({ request }) => {
      const searchParams = new URL(request.url).searchParams
      requests.push(searchParams.toString())
      const handlerResult = await respond(searchParams)
      return handlerResult instanceof Response ? handlerResult : HttpResponse.json(handlerResult)
    }),
  )
}

export function mockEntry(id: number, entry: StoredPokemon | Response): void {
  server.use(
    http.get(apiUrl(`/collection/${id}`), () =>
      entry instanceof Response ? entry : HttpResponse.json(entry),
    ),
  )
}

/** Answers GET /collection/ids with whatever `ids()` returns at that moment, so a test can change it between requests. */
export function mockCollectionIds(
  ids: number[] | Response | (() => number[] | Response),
  requests: string[] = [],
): void {
  server.use(
    http.get(apiUrl('/collection/ids'), () => {
      requests.push('ids')
      const response = typeof ids === 'function' ? ids() : ids
      return response instanceof Response ? response : HttpResponse.json(response)
    }),
  )
}

export const signedInUser = {
  id: 1,
  username: 'ash',
  email: 'ash@example.com',
  role: 'USER' as const,
}

/** Answers GET /users/me. */
export function mockProfile(profile: typeof signedInUser | Response = signedInUser): void {
  server.use(
    http.get(apiUrl('/users/me'), () =>
      profile instanceof Response ? profile : HttpResponse.json(profile),
    ),
  )
}
