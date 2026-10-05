import { createTheme } from '@mui/material/styles'
import tokens from './tokens.json'
export const makeNbrTheme = (mode: 'dark' | 'light' = 'dark') => {
 const c = tokens.colors[mode]
 return createTheme({ palette: { mode, primary: { main: c.primary, contrastText: c.onCta }, secondary: { main: c.accent }, background: { default: c.background, paper: c.surface }, text: { primary: c.textPrimary, secondary: c.textSecondary }, divider: c.border, success: { main: c.success }, warning: { main: c.warning }, error: { main: c.error } }, typography: { fontFamily: tokens.typography.fontFamily }, shape: { borderRadius: 12 }, components: { MuiButtonBase: { styleOverrides: { root: { '&.Mui-focusVisible': { outline: `3px solid ${c.focus}`, outlineOffset: 4 } } } } } })
}
