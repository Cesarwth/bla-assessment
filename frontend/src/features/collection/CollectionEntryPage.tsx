import { useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { isApiError } from '../../shared/api/errors'
import { formatId, formatName } from '../../shared/format'
import { EvolutionTree } from '../../shared/ui/EvolutionTree'
import { ErrorState } from '../../shared/ui/ErrorState'
import { LoadingStatus } from '../../shared/ui/LoadingStatus'
import { PokemonImage } from '../../shared/ui/PokemonImage'
import { StatBars } from '../../shared/ui/StatBars'
import { TypeChip } from '../../shared/ui/TypeChip'
import { useAuth } from '../auth/useAuth'
import { DeleteEntry } from './components/DeleteEntry'
import { EditForm } from './components/EditForm'
import styles from './CollectionEntryPage.module.css'
import { useDocumentTitle } from '../../shared/useDocumentTitle'
import { useCollectionEntry } from './queries'
import type { StoredPokemon } from '../../shared/api/types'

function NotesList({ entry }: { entry: StoredPokemon }) {
  return (
    <dl className={styles.readOnly}>
      <dt>Nickname</dt>
      <dd>{entry.nickname ? `“${entry.nickname}”` : <span className={styles.muted}>None</span>}</dd>
      <dt>Region</dt>
      <dd>{entry.region ?? <span className={styles.muted}>None</span>}</dd>
      <dt>Tags</dt>
      <dd>
        {entry.tags.length > 0 ? (
          <ul className={styles.tags} aria-label="Tags">
            {entry.tags.map((tag) => (
              <li key={tag} className={styles.tag}>
                {tag}
              </li>
            ))}
          </ul>
        ) : (
          <span className={styles.muted}>None</span>
        )}
      </dd>
    </dl>
  )
}

function NotInCollection() {
  return (
    <section className={styles.missing}>
      <h1>This Pokémon isn’t in the collection</h1>
      <p>It may have been removed, or it was never added.</p>
      <Link to="/collection">Back to the collection</Link>
    </section>
  )
}

export function CollectionEntryPage() {
  const { id } = useParams()
  const entryId = Number(id)
  const validId = Number.isInteger(entryId) && entryId >= 1
  const { user, isAdmin } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const [editing, setEditing] = useState(false)
  const query = useCollectionEntry(entryId, validId)
  useDocumentTitle(query.data ? formatName(query.data.name) : undefined)

  if (!validId) return <NotInCollection />
  if (query.isPending) return <LoadingStatus label="Loading the entry…" />
  if (query.isError) {
    if (isApiError(query.error) && query.error.code === 'STORED_POKEMON_NOT_FOUND') return <NotInCollection />
    return <ErrorState error={query.error} onRetry={() => void query.refetch()} />
  }

  const entry = query.data
  const name = formatName(entry.name)
  const isOwner = user !== null && entry.ownerId === user.id
  const canEdit = isOwner || isAdmin
  const chain = entry.evolutionChain
  const ownerLine = isOwner ? 'Added by you' : entry.ownerId === null ? 'No owner (only admins can edit it)' : 'Added by another trainer'

  return (
    <article>
      <div className={styles.layout}>
        <div className={styles.artCard} data-type={entry.types[0]}>
          <PokemonImage src={entry.imageUrl ?? entry.spriteUrl} alt={name} size={256} fallback="No image available" priority />
        </div>
        <div>
          <div className={styles.headline}>
            <h1>{name}</h1>
            <span className={styles.number}>{formatId(entry.id)}</span>
          </div>
          <div className={styles.types}>
            {entry.types.map((type) => (
              <TypeChip key={type} type={type} />
            ))}
          </div>
          <p className={styles.owner}>{ownerLine}</p>

          <section className={`${styles.notes} ${styles.section}`} aria-labelledby="notes-heading">
            <h2 id="notes-heading">Trainer notes</h2>
            {canEdit && editing ? (
              <EditForm
                entry={entry}
                onReload={() => void query.refetch()}
                onSaved={() => navigate(`/pokemon/${entry.id}`, { state: { saved: name } })}
                onCancel={() => setEditing(false)}
              />
            ) : (
              <>
                <NotesList entry={entry} />
                {canEdit ? (
                  <button type="button" className={styles.editButton} onClick={() => setEditing(true)}>
                    Edit notes
                  </button>
                ) : user ? (
                  entry.ownerId === null ? null : (
                    <p className={styles.muted}>You can’t edit this entry because another trainer added it.</p>
                  )
                ) : (
                  <Link to="/login" state={{ from: location.pathname }}>
                    Log in to edit your entries
                  </Link>
                )}
              </>
            )}
          </section>

          {isAdmin && <DeleteEntry entry={entry} />}
        </div>
      </div>

      <section className={styles.section} aria-labelledby="stats-heading">
        <h2 id="stats-heading">Base stats</h2>
        <StatBars stats={entry.stats} />
      </section>

      <section className={styles.section} aria-label="Evolution">
        <h2>Evolution</h2>
        {chain && chain.evolvesTo.length > 0 ? (
          <EvolutionTree chain={chain} currentId={entry.id} />
        ) : (
          <p className={styles.muted}>This Pokémon does not evolve.</p>
        )}
      </section>

      <p className={styles.footer}>
        <Link to={`/pokemon/${entry.id}`}>See it in the catalog</Link> · Last synced{' '}
        <time dateTime={entry.syncedAt}>{new Intl.DateTimeFormat('en', { dateStyle: 'medium' }).format(new Date(entry.syncedAt))}</time>
      </p>
    </article>
  )
}
