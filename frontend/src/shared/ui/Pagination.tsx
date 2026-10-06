import { useId } from 'react'
import { Link } from 'react-router'
import { buttonClass } from './buttonClass'
import { fieldClass } from './fieldClass'

interface Props {
  /** 1-based, as shown to people. */
  page: number
  totalPages: number
  size: number
  sizes: readonly number[]
  hrefFor: (page: number) => string
  onSizeChange: (size: number) => void
}

const step = buttonClass('secondary')

export function Pagination({ page, totalPages, size, sizes, hrefFor, onSizeChange }: Props) {
  const sizeId = useId()
  return (
    <nav
      aria-label="Pagination"
      className="mt-6 flex flex-wrap items-center justify-between gap-x-6 gap-y-3"
    >
      <div className="flex items-center gap-3">
        {page > 1 ? (
          <Link className={step} to={hrefFor(page - 1)} aria-label="Previous page">
            Previous
          </Link>
        ) : (
          <span className={`${step} text-muted`} aria-disabled="true">
            Previous
          </span>
        )}
        <p className="mb-0 text-center text-muted tabular-nums">
          Page {page} of {totalPages}
        </p>
        {page < totalPages ? (
          <Link className={step} to={hrefFor(page + 1)} aria-label="Next page">
            Next
          </Link>
        ) : (
          <span className={`${step} text-muted`} aria-disabled="true">
            Next
          </span>
        )}
      </div>
      <div className="flex items-center gap-2">
        <label htmlFor={sizeId}>Per page</label>
        <select
          id={sizeId}
          className={`${fieldClass} px-2 py-1`}
          value={size}
          onChange={(event) => onSizeChange(Number(event.target.value))}
        >
          {sizes.map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
      </div>
    </nav>
  )
}
