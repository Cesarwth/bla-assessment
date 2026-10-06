import { describeError } from '../api/errors'
import { Button } from './Button'

interface Props {
  error: unknown
  onRetry?: () => void
}

export function ErrorState({ error, onRetry }: Props) {
  return (
    <div
      role="alert"
      className="flex flex-wrap items-center gap-x-4 gap-y-3 rounded-md border border-danger bg-danger-surface px-4 py-4 text-fg sm:px-6"
    >
      <p className="mb-0 min-w-0 flex-[1_1_16rem] wrap-break-word">{describeError(error)}</p>
      {onRetry && (
        <Button className="border-danger" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  )
}
