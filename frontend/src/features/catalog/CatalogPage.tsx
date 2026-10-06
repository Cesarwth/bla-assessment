import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { ErrorState } from '../../shared/ui/ErrorState'
import { LoadingStatus } from '../../shared/ui/LoadingStatus'
import { Pagination } from '../../shared/ui/Pagination'
import { PAGE_SIZES, usePaging } from '../../shared/usePaging'
import styles from './CatalogPage.module.css'
import { GoToBox } from './components/GoToBox'
import { PokemonCard } from './components/PokemonCard'
import { useDocumentTitle } from '../../shared/useDocumentTitle'
import { useCatalogPage } from './queries'

/** `renderBadge` lets the app add a mark to a card (e.g. "already in my collection") without this feature knowing about it. */
export function CatalogPage({ renderBadge }: { renderBadge?: (pokemonId: number) => ReactNode }) {
  const { page, apiPage, size, hrefFor, setSize } = usePaging()
  const query = useCatalogPage(apiPage, size)
  useDocumentTitle('Catalog')

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
              <PokemonCard pokemon={pokemon} badge={renderBadge?.(pokemon.id)} />
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
      <p className={styles.intro}>Browse every Pokémon. Open one to see its stats and how it evolves.</p>
      <GoToBox />
      {renderResults()}
    </section>
  )
}
