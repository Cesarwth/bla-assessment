import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { isApiError } from '../../shared/api/errors'
import { formatId, formatName } from '../../shared/format'
import { BackLink } from '../../shared/ui/BackLink'
import { Button } from '../../shared/ui/Button'
import { Card } from '../../shared/ui/Card'
import { EvolutionTree } from '../../shared/ui/EvolutionTree'
import { ErrorState } from '../../shared/ui/ErrorState'
import { LoadingStatus } from '../../shared/ui/LoadingStatus'
import { PokemonImage } from '../../shared/ui/PokemonImage'
import { StatBars } from '../../shared/ui/StatBars'
import { TagList } from '../../shared/ui/TagList'
import { TypeChip } from '../../shared/ui/TypeChip'
import { DeleteEntry } from './components/DeleteEntry'
import { EditForm } from './components/EditForm'
import { useDocumentTitle } from '../../shared/useDocumentTitle'
import { useCollectionEntry } from './queries'
import type { StoredPokemon } from '../../shared/api/types'

const None = () => <span className="text-muted">None</span>

function NotesList({ entry }: { entry: StoredPokemon }) {
  return (
    <dl className="mb-3 grid grid-cols-[auto_1fr] gap-x-4 gap-y-2">
      <dt className="text-muted">Nickname</dt>
      <dd className="wrap-break-word">{entry.nickname ? `“${entry.nickname}”` : <None />}</dd>
      <dt className="text-muted">Region</dt>
      <dd className="wrap-break-word">{entry.region ?? <None />}</dd>
      <dt className="text-muted">Tags</dt>
      <dd>{entry.tags.length > 0 ? <TagList tags={entry.tags} /> : <None />}</dd>
    </dl>
  )
}

function NotInCollection() {
  return (
    <section className="py-8">
      <BackLink fallbackTo="/collection" fallbackLabel="my collection" />
      <h1>This Pokémon isn’t in your collection</h1>
      <p>You may have removed it, or never added it.</p>
    </section>
  )
}

export function CollectionEntryPage() {
  const { id } = useParams()
  const entryId = Number(id)
  const validId = Number.isInteger(entryId) && entryId >= 1
  const navigate = useNavigate()
  const [editing, setEditing] = useState(false)
  const query = useCollectionEntry(entryId, validId)
  useDocumentTitle(query.data ? formatName(query.data.name) : undefined)

  if (!validId) return <NotInCollection />
  if (query.isPending) return <LoadingStatus label="Loading the entry…" />
  if (query.isError) {
    if (isApiError(query.error) && query.error.code === 'STORED_POKEMON_NOT_FOUND')
      return <NotInCollection />
    return <ErrorState error={query.error} onRetry={() => void query.refetch()} />
  }

  const entry = query.data
  const name = formatName(entry.name)
  const chain = entry.evolutionChain

  return (
    <article>
      <BackLink fallbackTo="/collection" fallbackLabel="my collection" />
      <div className="grid gap-6 md:grid-cols-[minmax(12rem,16rem)_1fr] md:items-start">
        <div
          className="mx-auto grid aspect-square w-full max-w-64 place-items-center rounded-lg border border-border p-4 type-glow-art md:max-w-none"
          data-type={entry.types[0]}
        >
          <PokemonImage
            src={entry.imageUrl ?? entry.spriteUrl}
            alt={name}
            size={256}
            fallback="No image available"
            priority
          />
        </div>
        <div>
          <div className="flex flex-wrap items-baseline gap-x-3 gap-y-2">
            <h1 className="mb-0 wrap-break-word">{name}</h1>
            <span className="text-muted">{formatId(entry.id)}</span>
          </div>
          <div className="my-3 flex flex-wrap gap-2">
            {entry.types.map((type) => (
              <TypeChip key={type} type={type} />
            ))}
          </div>

          <Card as="section" className="mt-8 px-4 py-4 sm:px-6" aria-labelledby="notes-heading">
            <h2 id="notes-heading" className="text-lg">
              Trainer notes
            </h2>
            {editing ? (
              <EditForm
                entry={entry}
                onReload={() => void query.refetch()}
                onSaved={() => navigate(`/pokemon/${entry.id}`, { state: { saved: name } })}
                onCancel={() => setEditing(false)}
              />
            ) : (
              <>
                <NotesList entry={entry} />
                <Button variant="outline" className="mt-3 px-6" onClick={() => setEditing(true)}>
                  Edit notes
                </Button>
              </>
            )}
          </Card>

          <DeleteEntry entry={entry} />
        </div>
      </div>

      <section className="mt-8" aria-labelledby="stats-heading">
        <h2 id="stats-heading" className="text-lg">
          Base stats
        </h2>
        <StatBars stats={entry.stats} />
      </section>

      <section className="mt-8" aria-label="Evolution">
        <h2 className="text-lg">Evolution</h2>
        {chain && chain.evolvesTo.length > 0 ? (
          <EvolutionTree chain={chain} currentId={entry.id} />
        ) : (
          <p className="text-muted">This Pokémon does not evolve.</p>
        )}
      </section>

      <p className="mt-6 text-sm text-muted">
        <Link to={`/pokemon/${entry.id}`}>See it in the catalog</Link> · Last synced{' '}
        <time dateTime={entry.syncedAt}>
          {new Intl.DateTimeFormat('en', { dateStyle: 'medium' }).format(new Date(entry.syncedAt))}
        </time>
      </p>
    </article>
  )
}
