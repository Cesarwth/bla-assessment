import { formatLabel } from '../format'
import styles from './TypeChip.module.css'

export function TypeChip({ type }: { type: string }) {
  return (
    <span className={styles.chip} data-type={type}>
      <span className={styles.dot} aria-hidden="true" />
      {formatLabel(type)}
    </span>
  )
}
