import { cx } from './cx'

/** The trainer's tags as small labels. Text only: tags are user input and never rendered as HTML. */
export function TagList({ tags, className }: { tags: string[]; className?: string }) {
  return (
    <ul className={cx('flex flex-wrap gap-1', className)} aria-label="Tags">
      {tags.map((tag) => (
        <li key={tag} className="rounded-sm bg-surface-muted px-2 py-0.5 text-sm wrap-break-word">
          {tag}
        </li>
      ))}
    </ul>
  )
}
