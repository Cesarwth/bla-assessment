import { describeError } from '../api/errors'
import styles from './ErrorState.module.css'

interface Props {
  error: unknown
  onRetry?: () => void
}

export function ErrorState({ error, onRetry }: Props) {
  return (
    <div role="alert" className={styles.error}>
      <p className={styles.message}>{describeError(error)}</p>
      {onRetry && (
        <button type="button" className={styles.retry} onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  )
}
