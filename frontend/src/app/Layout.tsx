import { Outlet } from 'react-router'
import { Header } from './Header'
import styles from './Layout.module.css'

export function Layout() {
  return (
    <div className={styles.shell}>
      <a className={styles.skipLink} href="#main">
        Skip to content
      </a>
      <Header />
      <main id="main" className={styles.main} tabIndex={-1}>
        <Outlet />
      </main>
      <footer className={styles.footer}>
        Data from <a href="https://pokeapi.co">PokéAPI</a>. Pokémon is a trademark of Nintendo; this is a learning project.
      </footer>
    </div>
  )
}
