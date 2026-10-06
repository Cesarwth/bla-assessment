import { Badge } from '../../../shared/ui/Badge'
import { useAuth } from '../../../shared/session/useAuth'
import { useCollectionIds } from '../queries'

/** Marks a catalog card with a Pokémon the trainer already collected. Quiet on purpose: no mark if it cannot tell. */
export function InCollectionBadge({ pokemonId }: { pokemonId: number }) {
  const { user } = useAuth()
  const ids = useCollectionIds(user !== null)
  if (!ids.data?.includes(pokemonId)) return null
  return (
    <Badge tone="success" className="absolute top-3 right-3 shadow-md">
      In my collection
    </Badge>
  )
}
