import { Route, Routes } from 'react-router'
import { LoginPage } from '../features/auth/LoginPage'
import { RegisterPage } from '../features/auth/RegisterPage'
import { CatalogPage } from '../features/catalog/CatalogPage'
import { PokemonDetailPage } from '../features/catalog/PokemonDetailPage'
import { CatalogCardExtras } from '../features/collection/components/CatalogCardExtras'
import { SelectionToolbar } from '../features/collection/components/SelectionToolbar'
import { SelectionProvider } from '../features/collection/SelectionProvider'
import { AddToCollection } from '../features/collection/components/AddToCollection'
import { CollectionEntryPage } from '../features/collection/CollectionEntryPage'
import { CollectionPage } from '../features/collection/CollectionPage'
import { Layout } from './Layout'
import { NotFoundPage } from './NotFoundPage'
import { RequireLogin } from './RequireLogin'

// Features do not import each other: this is the one place where they are put together.
export function App() {
  return (
    <SelectionProvider>
      <Routes>
        <Route element={<Layout />}>
          <Route
            index
            element={
              <CatalogPage
                renderCardExtras={(pokemon) => <CatalogCardExtras pokemon={pokemon} />}
                renderToolbar={(items) => <SelectionToolbar items={items} />}
              />
            }
          />
          <Route
            path="pokemon/:idOrName"
            element={
              <PokemonDetailPage
                renderActions={(pokemon) => <AddToCollection pokemonId={pokemon.id} />}
              />
            }
          />
          <Route element={<RequireLogin />}>
            <Route path="collection" element={<CollectionPage />} />
            <Route path="collection/:id" element={<CollectionEntryPage />} />
          </Route>
          <Route path="login" element={<LoginPage />} />
          <Route path="register" element={<RegisterPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </SelectionProvider>
  )
}
