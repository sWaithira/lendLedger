import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api, setUnauthorizedHandler } from './api.js'

const AuthContext = createContext(null)

/** /me plus whether this person still has to choose their own password. */
async function loadUser() {
    const me = await api.get('/api/v1/me')
    try {
        const account = await api.get('/api/v1/account')
        return { ...me, mustChangePassword: Boolean(account.mustChangePassword), readOnly: Boolean(account.readOnly), subscriptionEndedOn: account.subscriptionEndedOn || null }
    } catch {
        return { ...me, mustChangePassword: false }
    }
}

export function AuthProvider({ children }) {
    const [user, setUser] = useState(null)
    const [loading, setLoading] = useState(true)

    useEffect(() => {
        // If the session expires mid-use, any 401 sends the person back to the sign-in screen.
        setUnauthorizedHandler(() => setUser(null))
        loadUser()
            .then(setUser)
            .catch(() => setUser(null))
            .finally(() => setLoading(false))
    }, [])

    const login = useCallback(async (email, password) => {
        await api.login(email, password)
        setUser(await loadUser())
    }, [])

    const logout = useCallback(async () => {
        try {
            await api.logout()
        } finally {
            setUser(null)
        }
    }, [])

    const markPasswordChanged = useCallback(() => setUser((u) => (u ? { ...u, mustChangePassword: false } : u)), [])

    const can = useCallback((permission) => !!user && user.permissions.includes(permission), [user])

    const value = useMemo(
        () => ({ user, loading, login, logout, can, markPasswordChanged }),
        [user, loading, login, logout, can, markPasswordChanged],
    )
    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
    return useContext(AuthContext)
}