import { useSearchParams } from 'react-router'

export const PAGE_SIZES = [12, 24, 48] as const
const DEFAULT_SIZE = 12
/** The API rejects longer filters, so the URL and the field never carry more. */
export const MAX_FILTER_LENGTH = 50

// The URL is the source of truth for paging and filtering, so views can be bookmarked and shared. People count pages
// from 1, the API from 0. Anything unusable in the URL falls back to the defaults instead of reaching the API.
function parsePage(raw: string | null): number {
  const page = Number(raw)
  return Number.isInteger(page) && page >= 1 ? page : 1
}

function parseSize(raw: string | null): number {
  const size = Number(raw)
  return (PAGE_SIZES as readonly number[]).includes(size) ? size : DEFAULT_SIZE
}

function parseFilter(raw: string | null): string {
  return (raw ?? '').trim().slice(0, MAX_FILTER_LENGTH)
}

function search(page: number, size: number, filter: string): string {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (filter) params.set('q', filter)
  return params.toString()
}

export function usePaging() {
  const [params, setParams] = useSearchParams()
  const page = parsePage(params.get('page'))
  const size = parseSize(params.get('size'))
  const filter = parseFilter(params.get('q'))
  return {
    /** 1-based, as shown to people. */
    page,
    /** 0-based, as the API expects it. */
    apiPage: page - 1,
    size,
    /** The text to filter by; empty when there is no filter. */
    filter,
    hrefFor: (target: number) => `?${search(target, size, filter)}`,
    hrefWithoutFilter: `?${search(1, size, '')}`,
    /** Changing the page size always goes back to the first page; the filter stays. */
    setSize: (next: number) => setParams(search(1, next, filter)),
    /** Changing the filter always goes back to the first page; the size stays. */
    setFilter: (next: string) => setParams(search(1, size, parseFilter(next))),
  }
}
