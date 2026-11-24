export type Role = 'TENANT' | 'OWNER' | 'ADMIN'
export type User = { id: number; phone: string; email: string; fullName: string; role: Role; status: string }
export type AuthResponse = { accessToken: string; refreshToken: string; tokenType: 'Bearer'; expiresIn: number; user: User }
export type ApiError = { code?: string; message?: string; details?: Array<{ field?: string; issue?: string }>; timestamp?: string }

const API_BASE = '/api/v1'

export async function apiRequest<T>(path: string, init: RequestInit = {}, token?: string): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const response = await fetch(`${API_BASE}${path}`, { ...init, headers })
  if (response.status === 204) return undefined as T
  const body = await response.json().catch(() => undefined)
  if (!response.ok) {
    if (response.status === 401) window.dispatchEvent(new Event('logetd:unauthorized'))
    if (response.status === 403) window.dispatchEvent(new Event('logetd:forbidden'))
    throw Object.assign(new Error((body as ApiError | undefined)?.message || 'La demande a échoué.'), { status: response.status, apiError: body as ApiError | undefined })
  }
  return body as T
}

export const authApi = {
  login: (identifier: string, password: string) => apiRequest<AuthResponse>('/auth/login', { method: 'POST', body: JSON.stringify({ identifier, password }) }),
  register: (data: { phone: string; email: string; password: string; fullName: string; role: Exclude<Role, 'ADMIN'> }) => apiRequest<User>('/auth/register', { method: 'POST', body: JSON.stringify(data) }),
  logout: (refreshToken: string) => apiRequest<void>('/auth/logout', { method: 'POST', headers: { Authorization: `Bearer ${refreshToken}` } }),
}
