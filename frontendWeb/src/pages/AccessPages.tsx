import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { Alert, Button, MenuItem, TextField } from '@mui/material'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../auth/AuthContext'
import type { Role } from '../api/client'

type LoginValues = { identifier: string; password: string }
type RegisterValues = { fullName: string; phone: string; email: string; password: string; role: 'TENANT' | 'OWNER' }

function AppHeader() {
  const { t, i18n } = useTranslation()
  const toggleLanguage = () => { void i18n.changeLanguage(i18n.language === 'fr' ? 'ar' : 'fr') }
  return <header className="access-header"><div className="access-header__inner"><Link className="wordmark" to="/login" aria-label="logEtd">logEtd</Link><span className="header-context">{t('header')}</span><button className="language-toggle" type="button" onClick={toggleLanguage} aria-label={t('language')}>{t('language')}</button></div></header>
}
function pageFor(role: Role) { return role === 'ADMIN' ? '/admin' : role === 'OWNER' ? '/owner' : '/tenant' }

export function LoginPage() {
  const { t } = useTranslation()
  const { user, login } = useAuth(); const navigate = useNavigate(); const location = useLocation()
  const [serverError, setServerError] = useState('')
  const notice = (location.state as { notice?: string } | null)?.notice
  const loginSchema = z.object({ identifier: z.string().trim().min(1, t('invalidIdentifier')), password: z.string().min(1, t('invalidPassword')) })
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<LoginValues>({ resolver: zodResolver(loginSchema) })
  if (user) return <Navigate to={pageFor(user.role)} replace />
  const submit = handleSubmit(async ({ identifier, password }) => {
    setServerError('')
    try { const loggedIn = await login(identifier, password); navigate((location.state as { from?: string } | null)?.from || pageFor(loggedIn.role), { replace: true }) }
    catch (error) { setServerError(error instanceof Error ? error.message : 'Connexion impossible. Réessayez.') }
  })
  return <div className="access-shell"><AppHeader/><main className="access-main"><motion.section className="access-copy" initial={false} aria-labelledby="login-title"><h1 id="login-title">{t('loginTitle')}</h1><p>{t('loginIntro')}</p><div className="access-note"><strong>logEtd</strong><span>{t('connection')}</span></div></motion.section><section className="access-form" aria-labelledby="form-title"><h2 id="form-title">{t('login')}</h2><p className="access-form__intro">{t('loginHint')}</p><form className="fields" onSubmit={submit} noValidate>
    <TextField label={t('identifier')} autoComplete="username" fullWidth {...register('identifier')} error={!!errors.identifier} helperText={errors.identifier?.message || ' '} inputProps={{ 'aria-label': t('identifier') }} />
    <TextField label={t('password')} type="password" autoComplete="current-password" fullWidth {...register('password')} error={!!errors.password} helperText={errors.password?.message || ' '} inputProps={{ 'aria-label': t('password') }} />
    {serverError && <Alert severity="error" role="alert">{serverError}</Alert>}
    <Button className="primary-action" type="submit" variant="contained" disabled={isSubmitting} aria-busy={isSubmitting}>{isSubmitting ? `${t('login')}…` : t('login')}</Button>
    {notice && <Alert severity="success" role="status">{notice === 'Compte créé. Connectez-vous avec vos identifiants.' ? t('successRegister') : notice}</Alert>}
  </form><p className="form-foot">{t('newAccount')} <Link to="/register">{t('createAccount')}</Link></p></section></main></div>
}

export function RegisterPage() {
  const { t } = useTranslation()
  const { user, register: createAccount } = useAuth(); const navigate = useNavigate()
  const [serverError, setServerError] = useState(''); const [created, setCreated] = useState(false)
  const registerSchema = z.object({ fullName: z.string().trim().min(2, t('invalidName')), phone: z.string().trim().min(6, t('invalidPhone')), email: z.email(t('invalidEmail')), password: z.string().min(8, t('shortPassword')).max(72, t('longPassword')), role: z.enum(['TENANT', 'OWNER']) })
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<RegisterValues>({ resolver: zodResolver(registerSchema), defaultValues: { role: 'TENANT' } })
  if (user) return <Navigate to={pageFor(user.role)} replace />
  const submit = handleSubmit(async (data) => {
    setServerError('')
    try { await createAccount(data); setCreated(true); navigate('/login', { replace: true, state: { notice: 'Compte créé. Connectez-vous avec vos identifiants.' } }) }
    catch (error) { setServerError(error instanceof Error ? error.message : 'Inscription impossible. Réessayez.') }
  })
  return <div className="access-shell"><AppHeader/><main className="access-main"><section className="access-copy"><h1>{t('registerTitle')}</h1><p>{t('registerIntro')}</p></section><section className="access-form" aria-labelledby="register-title"><h2 id="register-title">{t('createAccount')}</h2><p className="access-form__intro">{t('required')}</p><form className="fields" onSubmit={submit} noValidate>
    <TextField label={t('fullName')} autoComplete="name" fullWidth {...register('fullName')} error={!!errors.fullName} helperText={errors.fullName?.message || ' '} />
    <TextField label={t('phone')} autoComplete="tel" fullWidth {...register('phone')} error={!!errors.phone} helperText={errors.phone?.message || ' '} />
    <TextField label={t('email')} type="email" autoComplete="email" fullWidth {...register('email')} error={!!errors.email} helperText={errors.email?.message || ' '} />
    <TextField label={t('password')} type="password" autoComplete="new-password" fullWidth {...register('password')} error={!!errors.password} helperText={errors.password?.message || t('passwordHint')} />
    <TextField select label={t('accountType')} fullWidth defaultValue="TENANT" {...register('role')} error={!!errors.role} helperText={errors.role?.message || ' ' }><MenuItem value="TENANT">{t('tenant')}</MenuItem><MenuItem value="OWNER">{t('owner')}</MenuItem></TextField>
    {serverError && <Alert severity="error" role="alert">{serverError}</Alert>}
    {created && <Alert severity="success" role="status">Compte créé.</Alert>}
    <Button className="primary-action" type="submit" variant="contained" disabled={isSubmitting} aria-busy={isSubmitting}>{isSubmitting ? `${t('createAccount')}…` : t('register')}</Button>
  </form><p className="form-foot">{t('existingAccount')} <Link to="/login">{t('login')}</Link></p></section></main></div>
}

export function AccessDeniedPage() { const navigate = useNavigate(); const { t } = useTranslation(); return <div className="access-shell"><AppHeader/><main className="container denied"><h1>{t('deniedTitle')}</h1><p>{t('deniedIntro')}</p><button className="text-button" type="button" onClick={() => navigate(-1)}>{t('back')}</button></main></div> }

export function RoleLanding({ role }: { role: Role }) { const { user, logout } = useAuth(); const { t } = useTranslation(); return <div className="access-shell"><AppHeader/><main className="container denied"><h1>{role === 'ADMIN' ? t('adminSpace') : role === 'OWNER' ? t('ownerSpace') : t('tenantSpace')}</h1><p>{user?.fullName}. {t('upcoming')}</p><button className="text-button" type="button" onClick={() => void logout()}>{t('logout')}</button></main></div> }
