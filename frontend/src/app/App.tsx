import { Route, Routes } from 'react-router'
import { CatalogPage } from '../features/catalog/CatalogPage'
import { Layout } from './Layout'
import { NotFoundPage } from './NotFoundPage'

export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<CatalogPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
