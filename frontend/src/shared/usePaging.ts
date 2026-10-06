import { useSearchParams } from 'react-router'

export const PAGE_SIZES = [12, 24, 48] as const
const DEFAULT_SIZE = 12

// The URL is the source of truth for paging, so pages can be bookmarked and shared. People count pages from 1,
// the API from 0. Anything unusable in the URL falls back to the defaults instead of reaching the API.
function parsePage(raw: string | null): number {
  const page = Number(raw)
  return Number.isInteger(page) && page >= 1 ? page : 1
}

function parseSize(raw: string | null): number {
  const size = Number(raw)
  return (PAGE_SIZES as readonly number[]).includes(size) ? size : DEFAULT_SIZE
}

export function usePaging() {
  const [params, setParams] = useSearchParams()
  const page = parsePage(params.get('page'))
  const size = parseSize(params.get('size'))
  return {
    /** 1-based, as shown to people. */
    page,
    /** 0-based, as the API expects it. */
    apiPage: page - 1,
    size,
    hrefFor: (target: number) => `?page=${target}&size=${size}`,
    /** Changing the page size always goes back to the first page. */
    setSize: (next: number) => setParams({ page: '1', size: String(next) }),
  }
}
