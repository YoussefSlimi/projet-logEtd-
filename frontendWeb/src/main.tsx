import React from 'react'
import { useEffect } from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ThemeProvider, CssBaseline } from '@mui/material'
import i18n from './i18n'
import './styles/app.css'
import { theme } from './theme'
import { AuthProvider } from './auth/AuthContext'
import { PublicOnly, RequireAuth } from './auth/Guards'
import { AccessDeniedPage, LoginPage, RegisterPage, RoleLanding } from './pages/AccessPages'

const queryClient = new QueryClient()

function LanguageDirection() {
  useEffect(() => {
    const setDirection = (language: string) => { const rtl = language === 'ar'; document.documentElement.lang = rtl ? 'ar' : 'fr'; document.documentElement.dir = rtl ? 'rtl' : 'ltr' }
    setDirection(i18n.language)
    i18n.on('languageChanged', setDirection)
    return () => { i18n.off('languageChanged', setDirection) }
  }, [])
  return null
}

function App() {
  return <QueryClientProvider client={queryClient}><ThemeProvider theme={theme}><CssBaseline/><BrowserRouter><AuthProvider><LanguageDirection/><Routes>
    <Route element={<PublicOnly/>}><Route path="/login" element={<LoginPage/>}/><Route path="/register" element={<RegisterPage/>}/></Route>
    <Route path="/access-denied" element={<AccessDeniedPage/>}/>
    <Route element={<RequireAuth roles={['TENANT']}/> }><Route path="/tenant" element={<RoleLanding role="TENANT"/>}/></Route>
    <Route element={<RequireAuth roles={['OWNER']}/> }><Route path="/owner" element={<RoleLanding role="OWNER"/>}/></Route>
    <Route element={<RequireAuth roles={['ADMIN']}/> }><Route path="/admin" element={<RoleLanding role="ADMIN"/>}/></Route>
    <Route path="*" element={<Navigate to="/login" replace/>}/>
  </Routes></AuthProvider></BrowserRouter></ThemeProvider></QueryClientProvider>
}

ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><App/></React.StrictMode>)
