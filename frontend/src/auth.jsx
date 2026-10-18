import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api, setUnauthorizedHandler } from './api.js'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
    const [user, setUser] = useState(null)
    const [loading, setLoading] = useState(true)

    useEffect(() => {
        // If the session expires mid-use, any 401 sends the person back to the sign-in screen.
        setUnauthorizedHandler(() => setUser(null))
        api
            .get('/api/v1/me')
            .then(setUser)
            .catch(() => setUser(null))
            .finally(() => setLoading(false))
    }, [])

    const login = useCallback(async (email, password) => {
        await api.login(email, password)
        setUser(await api.get('/api/v1/me'))
    }, [])

    const logout = useCallback(async () => {
        try {
            await api.logout()
        } finally {
            setUser(null)
        }
    }, [])

    const can = useCallback((permission) => !!user && user.permissions.includes(permission), [user])

    const value = useMemo(() => ({ user, loading, login, logout, can }), [user, loading, login, logout, can])
    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
    return useContext(AuthContext)
}