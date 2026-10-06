import { Link } from 'react-router'
import type { EvolutionNode } from '../api/types'
import { formatName } from '../format'
import { PokemonImage } from './PokemonImage'

interface BranchProps {
  node: EvolutionNode
  currentId: number
  /** 0 for the first Pokémon of the chain. */
  depth: number
}

function Branch({ node, currentId, depth }: BranchProps) {
  return (
    <li className="flex flex-col gap-1">
      {node.requirements.map((requirement) => (
        <span key={requirement} className="text-xs font-semibold text-muted">
          {requirement}
        </span>
      ))}
      <Link
        className="inline-flex min-h-11 max-w-full items-center gap-3 self-start rounded-full border border-border bg-surface py-2 pr-4 pl-2 font-semibold text-fg no-underline hover:bg-surface-muted aria-[current=page]:border-primary aria-[current=page]:ring-2 aria-[current=page]:ring-primary"
        to={`/pokemon/${node.id}`}
        aria-current={node.id === currentId ? 'page' : undefined}
      >
        <span className="grid size-12 shrink-0 place-items-center">
          {/* decorative: the link text already names the Pokémon */}
          <PokemonImage src={node.spriteUrl} alt="" size={48} fallback="" />
        </span>
        <span className="min-w-0 wrap-break-word">{formatName(node.name)}</span>
      </Link>
      {node.evolvesTo.length > 0 && (
        // Branches hang off a vertical line; from md up the first branching reads left to right instead.
        <ul
          className={`mt-3 grid gap-4 border-l-2 border-border pl-6 ${depth === 0 ? 'md:ml-8 md:border-l-0 md:pl-0' : ''}`}
        >
          {node.evolvesTo.map((next) => (
            <Branch key={next.id} node={next} currentId={currentId} depth={depth + 1} />
          ))}
        </ul>
      )}
    </li>
  )
}

/** Recursive on purpose: branching evolutions (Eevee has eight) are just several children. */
export function EvolutionTree({ chain, currentId }: { chain: EvolutionNode; currentId: number }) {
  return (
    <ul>
      <Branch node={chain} currentId={currentId} depth={0} />
    </ul>
  )
}
