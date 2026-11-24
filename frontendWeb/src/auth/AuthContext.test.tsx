import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BrowserRouter } from 'react-router-dom'
import { AuthProvider } from './AuthContext'
import { LoginPage } from '../pages/AccessPages'
import { authApi } from '../api/client'

afterEach(() => { cleanup(); vi.restoreAllMocks(); localStorage.clear() })

describe('F1 authentication', () => {
  it('sends the documented login body and stores returned tokens', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ accessToken: 'access', refreshToken: 'refresh', tokenType: 'Bearer', expiresIn: 900, user: { id: 4, phone: '+21600000000', email: 'tenant@example.test', fullName: 'Etudiant', role: 'TENANT', status: 'ACTIVE' } }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    render(<BrowserRouter><AuthProvider><LoginPage/></AuthProvider></BrowserRouter>)
    await userEvent.type(screen.getByRole('textbox', { name: /e-mail ou téléphone/i }), 'tenant@example.test')
    await userEvent.type(screen.getByLabelText('Mot de passe'), 'motdepasse')
    await userEvent.click(screen.getByRole('button', { name: 'Se connecter' }))
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/v1/auth/login', expect.objectContaining({ method: 'POST' })))
    expect(JSON.parse(localStorage.getItem('logetd.session') || '{}')).toMatchObject({ accessToken: 'access', refreshToken: 'refresh' })
  })

  it('shows field validation without making an API request', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
    render(<BrowserRouter><AuthProvider><LoginPage/></AuthProvider></BrowserRouter>)
    await userEvent.click(screen.getByRole('button', { name: 'Se connecter' }))
    expect(await screen.findByText('Saisissez votre e-mail ou votre téléphone.')).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('uses the documented register and logout requests', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 9, phone: '+21600000000', email: 'owner@example.test', fullName: 'Propriétaire', role: 'OWNER', status: 'ACTIVE' }), { status: 201, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    await authApi.register({ phone: '+21600000000', email: 'owner@example.test', password: 'motdepasse', fullName: 'Propriétaire', role: 'OWNER' })
    await authApi.logout('refresh-token')
    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/auth/register', expect.objectContaining({ method: 'POST', body: JSON.stringify({ phone: '+21600000000', email: 'owner@example.test', password: 'motdepasse', fullName: 'Propriétaire', role: 'OWNER' }) }))
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/auth/logout', expect.objectContaining({ method: 'POST' }))
    expect(new Headers(fetchMock.mock.calls[1][1]?.headers).get('Authorization')).toBe('Bearer refresh-token')
  })
})
