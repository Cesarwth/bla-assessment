import { QueryClientProvider, type QueryClient } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { AuthProvider } from '../features/auth/AuthProvider'
import { createQueryClient } from './queryClient'

/** Everything the app needs besides the router, which main.tsx and the tests supply in their own way. */
export function AppProviders({ children, queryClient }: { children: ReactNode; queryClient?: QueryClient }) {
  const [defaultClient] = useState(() => createQueryClient())
  return (
    <QueryClientProvider client={queryClient ?? defaultClient}>
      <AuthProvider>{children}</AuthProvider>
    </QueryClientProvider>
  )
}
