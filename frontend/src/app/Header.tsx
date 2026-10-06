import { Link, NavLink } from 'react-router'
import { useAuth } from '../features/auth/useAuth'
import { Badge } from '../shared/ui/Badge'
import { Button } from '../shared/ui/Button'
import { buttonClass } from '../shared/ui/buttonClass'

const navLink =
  'inline-flex min-h-11 items-center rounded-full px-3 py-2 font-medium text-muted no-underline transition-colors hover:bg-surface-muted aria-[current=page]:bg-surface-muted aria-[current=page]:text-fg sm:min-h-0'

export function Header() {
  const { user, isAdmin, logout } = useAuth()
  return (
    <header className="sticky top-0 z-5 border-b border-border bg-surface/80 backdrop-blur-md">
      {/* Phones: brand and account share the first row, the navigation takes the second. From sm: one row. */}
      <div className="mx-auto flex max-w-content flex-wrap items-center gap-x-6 gap-y-2 px-4 py-3">
        <Link
          to="/"
          className="inline-flex min-h-11 items-center gap-2 text-xl font-extrabold text-fg no-underline sm:order-1 sm:min-h-0"
        >
          {/* An own drawing of a ball, not a trademarked logo. */}
          <svg viewBox="0 0 32 32" className="size-8 drop-shadow-sm" aria-hidden="true">
            <circle cx="16" cy="16" r="14" className="fill-surface stroke-fg" strokeWidth="2.5" />
            <path
              d="M2 16a14 14 0 0 1 28 0Z"
              className="fill-primary stroke-fg"
              strokeWidth="2.5"
            />
            <path d="M2 16h28" className="stroke-fg" strokeWidth="2.5" />
            <circle cx="16" cy="16" r="5" className="fill-surface stroke-fg" strokeWidth="2.5" />
            <circle cx="16" cy="16" r="1.8" className="fill-fg" />
          </svg>
          Pokédex
        </Link>
        <nav
          aria-label="Main"
          className="order-3 flex w-full gap-1 sm:order-2 sm:mr-auto sm:w-auto"
        >
          <NavLink to="/" end className={navLink}>
            Catalog
          </NavLink>
          {user && (
            <NavLink to="/collection" className={navLink}>
              My collection
            </NavLink>
          )}
        </nav>
        <div className="order-2 ml-auto flex min-w-0 items-center gap-3 sm:order-3 sm:ml-0">
          {user ? (
            <>
              <NavLink
                to="/profile"
                aria-label={`${user.username} (your profile)`}
                className="inline-flex min-h-11 min-w-0 items-center rounded-full px-2 font-semibold text-fg no-underline transition-colors hover:bg-surface-muted aria-[current=page]:bg-surface-muted sm:min-h-0"
              >
                <span className="max-w-28 truncate sm:max-w-48">{user.username}</span>
              </NavLink>
              {isAdmin && <Badge tone="primary">Admin</Badge>}
              <Button onClick={logout}>Log out</Button>
            </>
          ) : (
            <>
              <Link to="/login" className={buttonClass('secondary')}>
                Log in
              </Link>
              <Link to="/register" className={buttonClass('primary')}>
                Create profile
              </Link>
            </>
          )}
        </div>
      </div>
    </header>
  )
}
