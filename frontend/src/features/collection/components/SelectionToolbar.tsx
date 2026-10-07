import type { PokemonSummary } from '../../../shared/api/types'
import { formatName } from '../../../shared/format'
import { Button } from '../../../shared/ui/Button'
import { ErrorState } from '../../../shared/ui/ErrorState'
import { RemovableChip } from '../../../shared/ui/RemovableChip'
import { useAuth } from '../../../shared/session/useAuth'
import { useCollectionIds, useSyncSelection } from '../queries'
import { MAX_SELECTION } from '../SelectionContext'
import { useSelection } from '../useSelection'

const rowClass = 'flex flex-wrap items-center gap-3'

export function SelectionToolbar({ items }: { items: PokemonSummary[] }) {
  const { user } = useAuth()
  const selection = useSelection()
  // Nothing is asked until there are cards on screen: the page may still be loading or failed.
  const collected = useCollectionIds(user !== null && items.length > 0)
  const sync = useSyncSelection(selection.removeMany)
  if (!user) return null

  const selectable = items.filter((pokemon) => !collected.data?.includes(pokemon.id))
  const count = selection.items.length
  const report = sync.data
  const showSwitch = items.length > 0 || selection.active || count > 0
  const visible = showSwitch || report !== undefined

  function add() {
    sync.mutate(selection.ids)
  }

  return (
    <>
      <section
        className={
          visible
            ? 'mb-4 rounded-md border border-border bg-surface/80 px-4 py-3 backdrop-blur'
            : 'sr-only'
        }
        aria-label={visible ? 'Select Pokémon to add' : undefined}
      >
        {showSwitch && (
          <div className={rowClass}>
            <Button
              variant={selection.active ? 'primary' : 'outline'}
              aria-pressed={selection.active}
              onClick={() => selection.setActive(!selection.active)}
            >
              {selection.active ? 'Done selecting' : 'Select Pokémon'}
            </Button>
            {selection.active && (
              <Button
                disabled={selectable.length === 0 || selection.isFull || !collected.data}
                onClick={() => selection.addMany(selectable)}
              >
                Select all on this page
              </Button>
            )}
          </div>
        )}
        {/* Always in the page, so the report is announced when it arrives. */}
        <div role="status" className={report ? 'mt-2' : undefined}>
          {report && (
            <>
              <p className="mt-2 mb-0">Added {report.created} Pokémon to your collection.</p>
              {report.updated > 0 && (
                <p className="mt-2 mb-0">{report.updated} were already there and were refreshed.</p>
              )}
              {report.abortedReason && (
                <p className="mt-2 mb-0">Adding stopped early: {report.abortedReason}</p>
              )}
              {report.failures.length > 0 && (
                <ul className="mt-2 list-disc pl-6 text-sm text-muted" aria-label="Not added">
                  {report.failures.map((failure) => (
                    <li key={failure.identifier}>
                      {failure.identifier}: {failure.reason}
                    </li>
                  ))}
                </ul>
              )}
            </>
          )}
        </div>
      </section>
      {count > 0 && (
        <section
          className="mb-4 rounded-md border border-primary bg-surface px-4 py-3 max-sm:sticky max-sm:bottom-3 max-sm:z-2 max-sm:shadow-md"
          aria-label="Your selection"
        >
          <div className={rowClass}>
            <p className="mb-0 font-semibold" role="status">
              {count} selected
            </p>
            <Button variant="primary" disabled={sync.isPending} onClick={add}>
              {sync.isPending ? 'Adding…' : `Add ${count} to my collection`}
            </Button>
            <Button disabled={sync.isPending} onClick={selection.clear}>
              Clear
            </Button>
          </div>
          <ul
            className="mt-3 flex max-h-24 flex-wrap gap-2 overflow-y-auto sm:max-h-28"
            aria-label="Selected Pokémon"
          >
            {selection.items.map((pokemon) => (
              <RemovableChip
                key={pokemon.id}
                removeLabel={`Remove ${formatName(pokemon.name)} from the selection`}
                disabled={sync.isPending}
                onRemove={() => selection.remove(pokemon.id)}
              >
                {formatName(pokemon.name)}
              </RemovableChip>
            ))}
          </ul>
          {selection.isFull && (
            <p className="mt-2 mb-0 text-sm text-muted">
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
