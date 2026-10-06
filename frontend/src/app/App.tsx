import { Route, Routes } from 'react-router'
import { LoginPage } from '../features/auth/LoginPage'
import { RegisterPage } from '../features/auth/RegisterPage'
import { CatalogPage } from '../features/catalog/CatalogPage'
import { PokemonDetailPage } from '../features/catalog/PokemonDetailPage'
import { AddToCollection } from '../features/collection/components/AddToCollection'
import { CollectionPage } from '../features/collection/CollectionPage'
import { Layout } from './Layout'
import { NotFoundPage } from './NotFoundPage'

// Features do not import each other: this is the one place where they are put together.
export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<CatalogPage />} />
        <Route
          path="pokemon/:idOrName"
          element={<PokemonDetailPage renderActions={(pokemon) => <AddToCollection pokemonId={pokemon.id} />} />}
        />
        <Route path="collection" element={<CollectionPage />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="register" element={<RegisterPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
