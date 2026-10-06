import { useId } from 'react'
import { Link } from 'react-router'
import styles from './Pagination.module.css'

interface Props {
  /** 1-based, as shown to people. */
  page: number
  totalPages: number
  size: number
  sizes: readonly number[]
  hrefFor: (page: number) => string
  onSizeChange: (size: number) => void
}

export function Pagination({ page, totalPages, size, sizes, hrefFor, onSizeChange }: Props) {
  const sizeId = useId()
  return (
    <nav aria-label="Pagination" className={styles.pagination}>
      <div className={styles.steps}>
        {page > 1 ? (
          <Link className={styles.step} to={hrefFor(page - 1)} aria-label="Previous page">
            Previous
          </Link>
        ) : (
          <span className={styles.step} aria-disabled="true">
            Previous
          </span>
        )}
        <p className={styles.position}>
          Page {page} of {totalPages}
        </p>
        {page < totalPages ? (
          <Link className={styles.step} to={hrefFor(page + 1)} aria-label="Next page">
            Next
          </Link>
        ) : (
          <span className={styles.step} aria-disabled="true">
            Next
          </span>
        )}
      </div>
      <div className={styles.size}>
        <label htmlFor={sizeId}>Per page</label>
        <select
          id={sizeId}
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
