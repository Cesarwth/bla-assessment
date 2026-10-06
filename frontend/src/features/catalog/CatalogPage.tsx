import { Link, useSearchParams } from 'react-router'
import { ErrorState } from '../../shared/ui/ErrorState'
import { LoadingStatus } from '../../shared/ui/LoadingStatus'
import styles from './CatalogPage.module.css'
import { GoToBox } from './components/GoToBox'
import { Pagination } from './components/Pagination'
import { PokemonCard } from './components/PokemonCard'
import { useCatalogPage } from './queries'

const PAGE_SIZES = [12, 24, 48] as const
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

export function CatalogPage() {
  const [params, setParams] = useSearchParams()
  const page = parsePage(params.get('page'))
  const size = parseSize(params.get('size'))
  const query = useCatalogPage(page - 1, size)
  const hrefFor = (target: number) => `?page=${target}&size=${size}`

  function renderResults() {
    if (query.isError) return <ErrorState error={query.error} onRetry={() => void query.refetch()} />
    if (query.isPending) {
      return (
        <>
          <LoadingStatus />
          <ul className={styles.grid} aria-hidden="true">
            {Array.from({ length: size }, (_, index) => (
              <li key={index} className={styles.skeleton} />
            ))}
          </ul>
        </>
      )
    }
    if (query.data.items.length === 0) {
      return (
        <div className={styles.empty}>
          <h2>No Pokémon on this page</h2>
          <Link to={hrefFor(1)}>Go to the first page</Link>
        </div>
      )
    }
    return (
      <>
        <ul className={styles.grid} aria-busy={query.isPlaceholderData}>
          {query.data.items.map((pokemon) => (
            <li key={pokemon.id}>
              <PokemonCard pokemon={pokemon} />
            </li>
          ))}
        </ul>
        <Pagination
          page={page}
          totalPages={query.data.totalPages}
          size={size}
          sizes={PAGE_SIZES}
          hrefFor={hrefFor}
          onSizeChange={(next) => setParams({ page: '1', size: String(next) })}
        />
      </>
    )
  }

  return (
    <section>
      <h1>Catalog</h1>
      <p className={styles.intro}>Browse every Pokémon. Open one to see its stats and how it evolves.</p>
      <GoToBox />
      {renderResults()}
    </section>
  )
}
