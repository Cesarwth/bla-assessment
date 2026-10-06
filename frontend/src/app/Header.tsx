import { Link, NavLink } from 'react-router'
import { useAuth } from '../features/auth/useAuth'
import { Badge } from '../shared/ui/Badge'
import { Button } from '../shared/ui/Button'
import { buttonClass } from '../shared/ui/buttonClass'

const navLink =
  'inline-flex min-h-11 items-center rounded-full px-3 py-2 font-semibold text-muted no-underline hover:bg-surface-muted aria-[current=page]:bg-surface-muted aria-[current=page]:text-fg sm:min-h-0'

export function Header() {
  const { user, isAdmin, logout } = useAuth()
  return (
    <header className="sticky top-0 z-5 border-b border-border bg-surface">
      {/* Phones: brand and account share the first row, the navigation takes the second. From sm: one row. */}
      <div className="mx-auto flex max-w-content flex-wrap items-center gap-x-6 gap-y-2 px-4 py-3">
        <Link
          to="/"
          className="inline-flex min-h-11 items-center gap-2 text-xl font-extrabold text-fg no-underline sm:order-1 sm:min-h-0"
        >
          <span
            className="size-6 rounded-full border-2 border-fg bg-[linear-gradient(var(--color-primary)_46%,var(--color-fg)_46%_54%,var(--color-surface)_54%)]"
            aria-hidden="true"
          />
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
              <span className="max-w-32 truncate font-semibold sm:max-w-48">{user.username}</span>
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
