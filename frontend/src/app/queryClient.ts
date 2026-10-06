import { QueryClient } from '@tanstack/react-query'
import { isApiError } from '../shared/api/errors'

interface Options {
  /** Tests turn retries off so failures show up immediately. */
  retry?: boolean
}

// The API sends public GETs with a 60 s cache header and caches PokeAPI calls itself: keep results fresh for as
// long, and only retry what can succeed later (network and 5xx), never a 4xx.
export function createQueryClient({ retry = true }: Options = {}): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 60_000,
        refetchOnWindowFocus: false,
        retry: retry
          ? (failureCount, error) =>
              failureCount < 1 && (!isApiError(error) || error.status === 0 || error.status >= 500)
          : false,
      },
    },
  })
}
