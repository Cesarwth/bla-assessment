import { Link } from 'react-router'
import type { EvolutionNode } from '../api/types'
import { formatName } from '../format'
import styles from './EvolutionTree.module.css'
import { PokemonImage } from './PokemonImage'

interface BranchProps {
  node: EvolutionNode
  currentId: number
}

function Branch({ node, currentId }: BranchProps) {
  return (
    <li className={styles.step}>
      {node.requirements.map((requirement) => (
        <span key={requirement} className={styles.requirement}>
          {requirement}
        </span>
      ))}
      <Link
        className={styles.node}
        to={`/pokemon/${node.id}`}
        aria-current={node.id === currentId ? 'page' : undefined}
      >
        <span className={styles.sprite}>
          {/* decorative: the link text already names the Pokémon */}
          <PokemonImage src={node.spriteUrl} alt="" size={48} fallback="" />
        </span>
        {formatName(node.name)}
      </Link>
      {node.evolvesTo.length > 0 && (
        <ul>
          {node.evolvesTo.map((next) => (
            <Branch key={next.id} node={next} currentId={currentId} />
          ))}
        </ul>
      )}
    </li>
  )
}

/** Recursive on purpose: branching evolutions (Eevee has eight) are just several children. */
export function EvolutionTree({ chain, currentId }: { chain: EvolutionNode; currentId: number }) {
  return (
    <ul className={styles.tree}>
      <Branch node={chain} currentId={currentId} />
    </ul>
  )
}
