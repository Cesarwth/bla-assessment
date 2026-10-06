import type { PokemonSummary } from '../../../shared/api/types'
import { ErrorState } from '../../../shared/ui/ErrorState'
import { useAuth } from '../../auth/useAuth'
import { useCollectionIds, useSyncSelection } from '../queries'
import { MAX_SELECTION } from '../SelectionContext'
import { useSelection } from '../useSelection'
import styles from './SelectionToolbar.module.css'

/** Select / clear / add for the catalog. Trainers only; `items` are the Pokémon of the page on screen. */
export function SelectionToolbar({ items }: { items: PokemonSummary[] }) {
  const { user } = useAuth()
  // Nothing is asked until there are cards on screen: the page may still be loading or failed.
  const collected = useCollectionIds(user !== null && items.length > 0)
  const selection = useSelection()
  const sync = useSyncSelection()
  if (!user || items.length === 0 || !collected.data) return null

  const selectable = items.filter((pokemon) => !collected.data.includes(pokemon.id))
  const count = selection.ids.length
  const report = sync.data

  function add() {
    sync.mutate(selection.ids, { onSuccess: selection.clear })
  }

  return (
    <section className={styles.toolbar} aria-label="Select Pokémon to add">
      <div className={styles.row}>
        <button
          type="button"
          className={styles.secondary}
          disabled={selectable.length === 0 || selection.isFull}
          onClick={() => selection.addMany(selectable.map((pokemon) => pokemon.id))}
        >
          Select all on this page
        </button>
        <p className={styles.count} role="status">
          {count} selected
        </p>
        {count > 0 && (
          <>
            <button type="button" className={styles.primary} disabled={sync.isPending} onClick={add}>
              {sync.isPending ? 'Adding…' : `Add ${count} to my collection`}
            </button>
            <button type="button" className={styles.secondary} disabled={sync.isPending} onClick={selection.clear}>
              Clear
            </button>
          </>
        )}
      </div>
      {selection.isFull && (
        <p className={styles.hint}>
          You can add up to {MAX_SELECTION} Pokémon at a time. Add these first, then select more.
        </p>
      )}
      {sync.isError && <ErrorState error={sync.error} onRetry={add} />}
      {report && (
        <div className={styles.report} role="status">
          <p>Added {report.created} Pokémon to your collection.</p>
          {report.updated > 0 && <p>{report.updated} were already there and were refreshed.</p>}
          {report.abortedReason && <p>Adding stopped early: {report.abortedReason}</p>}
          {report.failures.length > 0 && (
            <ul className={styles.failures} aria-label="Not added">
              {report.failures.map((failure) => (
                <li key={failure.identifier}>
                  {failure.identifier}: {failure.reason}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </section>
  )
}
