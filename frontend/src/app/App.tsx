import { Route, Routes } from 'react-router'
import { CatalogPage } from '../features/catalog/CatalogPage'
import { PokemonDetailPage } from '../features/catalog/PokemonDetailPage'
import { Layout } from './Layout'
import { NotFoundPage } from './NotFoundPage'

export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<CatalogPage />} />
        <Route path="pokemon/:idOrName" element={<PokemonDetailPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
