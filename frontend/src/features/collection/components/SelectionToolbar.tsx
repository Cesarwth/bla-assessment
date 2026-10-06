import type { PokemonSummary } from '../../../shared/api/types'
import { formatName } from '../../../shared/format'
import { ErrorState } from '../../../shared/ui/ErrorState'
import { useAuth } from '../../auth/useAuth'
import { useCollectionIds, useSyncSelection } from '../queries'
import { MAX_SELECTION } from '../SelectionContext'
import { useSelection } from '../useSelection'
import styles from './SelectionToolbar.module.css'

/**
 * Selection for the catalog, trainers only: the "Select Pokémon" switch (with "Select all on this page" while it is
 * on), the result of the last add, and the tray with everything chosen so far. `items` are the Pokémon on screen.
 */
export function SelectionToolbar({ items }: { items: PokemonSummary[] }) {
  const { user } = useAuth()
  const selection = useSelection()
  // Nothing is asked until there are cards on screen: the page may still be loading or failed.
  const collected = useCollectionIds(user !== null && items.length > 0)
  const sync = useSyncSelection()
  if (!user) return null

  const selectable = items.filter((pokemon) => !collected.data?.includes(pokemon.id))
  const count = selection.items.length
  const report = sync.data
  const showSwitch = items.length > 0 || selection.active || count > 0

  function add() {
    sync.mutate(selection.ids, { onSuccess: selection.clear })
  }

  return (
    <>
      <section className={styles.toolbar} aria-label="Select Pokémon to add">
        {showSwitch && (
          <div className={styles.row}>
            <button
              type="button"
              className={selection.active ? styles.switchOn : styles.switchOff}
              aria-pressed={selection.active}
              onClick={() => selection.setActive(!selection.active)}
            >
              {selection.active ? 'Done selecting' : 'Select Pokémon'}
            </button>
            {selection.active && (
              <button
                type="button"
                className={styles.secondary}
                disabled={selectable.length === 0 || selection.isFull || !collected.data}
                onClick={() => selection.addMany(selectable)}
              >
                Select all on this page
              </button>
            )}
          </div>
        )}
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
      {count > 0 && (
        <section className={styles.tray} aria-label="Your selection">
          <div className={styles.row}>
            <p className={styles.count} role="status">
              {count} selected
            </p>
            <button
              type="button"
              className={styles.primary}
              disabled={sync.isPending}
              onClick={add}
            >
              {sync.isPending ? 'Adding…' : `Add ${count} to my collection`}
            </button>
            <button
              type="button"
              className={styles.secondary}
              disabled={sync.isPending}
              onClick={selection.clear}
            >
              Clear
            </button>
          </div>
          <ul className={styles.chips} aria-label="Selected Pokémon">
            {selection.items.map((pokemon) => (
              <li key={pokemon.id} className={styles.chip}>
                {formatName(pokemon.name)}
                <button
                  type="button"
                  className={styles.remove}
                  aria-label={`Remove ${formatName(pokemon.name)} from the selection`}
                  disabled={sync.isPending}
                  onClick={() => selection.remove(pokemon.id)}
                >
                  <span aria-hidden="true">×</span>
                </button>
              </li>
            ))}
          </ul>
          {selection.isFull && (
            <p className={styles.hint}>
              You can add up to {MAX_SELECTION} Pokémon at a time. Add these first, then select
              more.
            </p>
          )}
          {sync.isError && <ErrorState error={sync.error} onRetry={add} />}
        </section>
      )}
    </>
  )
}
