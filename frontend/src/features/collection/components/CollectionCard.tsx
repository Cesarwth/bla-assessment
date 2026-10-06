import { Link } from 'react-router'
import { formatName } from '../../../shared/format'
import type { StoredPokemonSummary } from '../../../shared/api/types'
import { PokemonCardFrame } from '../../../shared/ui/PokemonCardFrame'
import { TagList } from '../../../shared/ui/TagList'
import { TypeChip } from '../../../shared/ui/TypeChip'

export function CollectionCard({ entry }: { entry: StoredPokemonSummary }) {
  const name = formatName(entry.name)
  return (
    <PokemonCardFrame
      type={entry.types[0]}
      id={entry.id}
      name={name}
      imageSrc={entry.imageUrl ?? entry.spriteUrl}
      title={
        <Link
          className="text-(--type-ink) no-underline after:absolute after:inset-0 after:content-[''] focus-visible:outline-(--type-ink)"
          to={`/collection/${entry.id}`}
        >
          {name}
        </Link>
      }
    >
      <div className="flex flex-wrap gap-2">
        {entry.types.map((type) => (
          <TypeChip key={type} type={type} />
        ))}
      </div>
      {entry.nickname && <p className="mb-0 italic wrap-break-word">“{entry.nickname}”</p>}
      {entry.region && <p className="mb-0 text-sm text-muted wrap-break-word">{entry.region}</p>}
      {entry.tags.length > 0 && <TagList tags={entry.tags} />}
    </PokemonCardFrame>
  )
}
