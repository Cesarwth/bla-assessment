import type { ReactNode } from 'react'
import type { PokemonSummary } from '../../shared/api/types'
import { Link } from 'react-router'
import { ErrorState } from '../../shared/ui/ErrorState'
import { LoadingStatus } from '../../shared/ui/LoadingStatus'
import { Pagination } from '../../shared/ui/Pagination'
import { PAGE_SIZES, usePaging } from '../../shared/usePaging'
import { CatalogFilter } from './components/CatalogFilter'
import { PokemonCard } from './components/PokemonCard'
import { useDocumentTitle } from '../../shared/useDocumentTitle'
import { useCatalogPage } from './queries'

const gridClass = 'grid grid-cols-[repeat(auto-fill,minmax(min(15rem,100%),1fr))] gap-4'
const emptyClass = 'px-4 py-8 text-center text-muted'

interface Props {
  /** Per-card extras (a mark, a checkbox) supplied by the app, so this feature does not know about collections. */
  renderCardExtras?: (pokemon: PokemonSummary) => ReactNode
  /** A toolbar above the results; it gets the Pokémon of the current page and stays mounted while paging. */
  renderToolbar?: (pageItems: PokemonSummary[]) => ReactNode
}

export function CatalogPage({ renderCardExtras, renderToolbar }: Props) {
  const { page, apiPage, size, filter, hrefFor, hrefWithoutFilter, setSize, setFilter } =
    usePaging()
  const query = useCatalogPage(apiPage, size, filter)
  useDocumentTitle('Catalog')

  function renderResults() {
    if (query.isError)
      return <ErrorState error={query.error} onRetry={() => void query.refetch()} />
    if (query.isPending) {
      return (
        <>
          <LoadingStatus />
          <ul className={gridClass} aria-hidden="true">
            {Array.from({ length: size }, (_, index) => (
              <li key={index} className="h-68 animate-shimmer rounded-md skeleton-shimmer" />
            ))}
          </ul>
        </>
      )
    }
    if (query.data.items.length === 0 && filter) {
      return (
        <div className={emptyClass}>
          <h2 className="text-fg">No Pokémon match “{filter}”</h2>
          <Link to={hrefWithoutFilter}>Show all Pokémon</Link>
        </div>
      )
    }
    if (query.data.items.length === 0) {
      return (
        <div className={emptyClass}>
          <h2 className="text-fg">No Pokémon on this page</h2>
          <Link to={hrefFor(1)}>Go to the first page</Link>
        </div>
      )
    }
    return (
      <>
        <ul
          className={`${gridClass} aria-busy:opacity-60 aria-busy:transition-opacity`}
          aria-busy={query.isPlaceholderData}
        >
          {query.data.items.map((pokemon) => (
            <li key={pokemon.id}>
              <PokemonCard pokemon={pokemon} extras={renderCardExtras?.(pokemon)} />
            </li>
          ))}
        </ul>
        <Pagination
          page={page}
          totalPages={query.data.totalPages}
          size={size}
          sizes={PAGE_SIZES}
          hrefFor={hrefFor}
          onSizeChange={setSize}
        />
      </>
    )
  }

  return (
    <section>
      <h1>Catalog</h1>
      <p className="max-w-160 text-muted">
        Browse every Pokémon. Open one to see its stats and how it evolves.
      </p>
      <CatalogFilter value={filter} onChange={setFilter} />
      {renderToolbar?.(query.data?.items ?? [])}
      {renderResults()}
    </section>
  )
}
