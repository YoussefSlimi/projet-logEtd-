import { createTheme } from '@mui/material/styles'

export const theme = createTheme({
  palette: { mode: 'light' },
  typography: { fontFamily: 'var(--font-body)', button: { fontWeight: 650, textTransform: 'none' } },
  shape: { borderRadius: 3 },
  components: {
    MuiButton: { styleOverrides: { root: { minHeight: 48, borderRadius: 'var(--radius-input)', boxShadow: 'none', fontFamily: 'var(--font-body)' }, containedPrimary: { backgroundColor: 'var(--color-accent)', color: 'var(--color-accent-ink)', '&:hover': { backgroundColor: 'color-mix(in oklab, var(--color-accent) 88%, var(--color-ink))', boxShadow: 'none' }, '&:focus-visible': { outline: '2px solid var(--color-focus)', outlineOffset: 3 } } } },
    MuiOutlinedInput: { styleOverrides: { root: { height: 48, boxSizing: 'border-box', border: '1px solid var(--color-muted)', borderRadius: 'var(--radius-input)', backgroundColor: 'var(--color-paper)', '&:hover': { backgroundColor: 'var(--color-paper-2)' }, '& .MuiOutlinedInput-notchedOutline': { border: 0 }, '&.Mui-focused': { outline: '2px solid var(--color-focus)', outlineOffset: '-3px', borderColor: 'var(--color-ink-2)' }, '&.Mui-error': { borderColor: 'var(--color-error)' }, '&.Mui-disabled': { opacity: 0.55, cursor: 'not-allowed' } }, input: { height: '100%', boxSizing: 'border-box', padding: '0 var(--space-sm)', minHeight: 0, fontSize: '1rem' } } },
    MuiInputLabel: { styleOverrides: { root: { color: 'var(--color-ink-2)' } } },
    MuiFormHelperText: { styleOverrides: { root: { color: 'var(--color-muted)', '&.Mui-error': { color: 'var(--color-error)' } } } },
    MuiAlert: { styleOverrides: { root: { borderRadius: 'var(--radius-input)' }, standardError: { color: 'var(--color-error)', backgroundColor: 'var(--color-paper-2)' }, standardSuccess: { color: 'var(--color-success)', backgroundColor: 'var(--color-paper-2)' } } },
  },
})
