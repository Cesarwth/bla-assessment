import { Link } from 'react-router'
import { formatName } from '../../../shared/format'
import type { StoredPokemonSummary } from '../../../shared/api/types'
import { Card } from '../../../shared/ui/Card'
import { PokemonImage } from '../../../shared/ui/PokemonImage'
import { TagList } from '../../../shared/ui/TagList'
import { TypeChip } from '../../../shared/ui/TypeChip'

export function CollectionCard({ entry }: { entry: StoredPokemonSummary }) {
  const name = formatName(entry.name)
  return (
    <Card as="article" interactive className="relative flex h-full flex-col gap-3 p-4">
      <div className="flex items-center gap-3">
        <div className="size-20 flex-none rounded-full type-glow" data-type={entry.types[0]}>
          <PokemonImage src={entry.spriteUrl} alt={name} size={80} fallback="No image" />
        </div>
        <div className="min-w-0">
          <h2 className="mb-0 text-lg wrap-break-word">
            <Link
              className="text-fg no-underline after:absolute after:inset-0 after:rounded-md after:content-['']"
              to={`/collection/${entry.id}`}
            >
              {name}
            </Link>
          </h2>
          {entry.nickname && (
            <p className="mb-0 text-muted italic wrap-break-word">“{entry.nickname}”</p>
          )}
        </div>
      </div>
      <div className="flex flex-wrap gap-2">
        {entry.types.map((type) => (
          <TypeChip key={type} type={type} />
        ))}
      </div>
      {entry.region && <p className="mb-0 text-sm wrap-break-word">{entry.region}</p>}
      {entry.tags.length > 0 && <TagList tags={entry.tags} />}
    </Card>
  )
}
