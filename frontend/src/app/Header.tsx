import { Link, NavLink } from 'react-router'
import { useAuth } from '../features/auth/useAuth'
import styles from './Header.module.css'

export function Header() {
  const { user, isAdmin, logout } = useAuth()

  return (
    <header className={styles.header}>
      <div className={styles.bar}>
        <Link to="/" className={styles.brand}>
          <span className={styles.ball} aria-hidden="true" />
          Pokédex
        </Link>
        <nav aria-label="Main" className={styles.nav}>
          <NavLink to="/" end>
            Catalog
          </NavLink>
          {user && <NavLink to="/collection">My collection</NavLink>}
        </nav>
        <div className={styles.account}>
          {user ? (
            <>
              <span className={styles.username}>{user.username}</span>
              {isAdmin && <span className={styles.badge}>Admin</span>}
              <button type="button" className={styles.button} onClick={logout}>
                Log out
              </button>
            </>
          ) : (
            <>
              <Link to="/login" className={styles.link}>
                Log in
              </Link>
              <Link to="/register" className={`${styles.link} ${styles.primary}`}>
                Create profile
              </Link>
            </>
          )}
        </div>
      </div>
    </header>
  )
}
