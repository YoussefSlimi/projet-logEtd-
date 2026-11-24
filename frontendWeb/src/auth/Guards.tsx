import { Navigate, Outlet, useLocation } from 'react-router-dom'
import type { Role } from '../api/client'
import { useAuth } from './AuthContext'

export function RequireAuth({ roles }: { roles?: Role[] }) {
  const { user } = useAuth(); const location = useLocation()
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  if (roles && !roles.includes(user.role)) return <Navigate to="/access-denied" replace />
  return <Outlet />
}

export function PublicOnly() {
  const { user } = useAuth()
  if (!user) return <Outlet />
  return <Navigate to={user.role === 'ADMIN' ? '/admin' : user.role === 'OWNER' ? '/owner' : '/tenant'} replace />
}
