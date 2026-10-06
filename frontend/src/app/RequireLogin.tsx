import { Navigate, Outlet, useLocation } from 'react-router'
import { useAuth } from '../shared/session/useAuth'

// Lives in app/ because it combines a feature's session with the routes of another: features never import each other.
export function RequireLogin() {
  const { user } = useAuth()
  const location = useLocation()
  if (!user)
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  return <Outlet />
}
