import { useState } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { describeError } from '../api.js'
import { useAuth } from '../auth.jsx'
import { Button, Field } from '../components/ui.jsx'

export default function Login() {
    const { user, login } = useAuth()
    const location = useLocation()
    const [email, setEmail] = useState('')
    const [password, setPassword] = useState('')
    const [error, setError] = useState('')
    const [busy, setBusy] = useState(false)

    if (user) return <Navigate to={location.state?.from?.pathname || '/'} replace />

    async function submit(e) {
        e.preventDefault()
        setBusy(true)
        setError('')
        try {
            await login(email.trim(), password)
        } catch (err) {
            setError(
                err.status === 401
                    ? 'That email and password don’t match. After five wrong attempts, sign-in pauses for 15 minutes.'
                    : describeError(err),
            )
        } finally {
            setBusy(false)
        }
    }

    return (
        <div className="signin">
            <form className="panel" onSubmit={submit}>
                <h1>Sign in to LendLedger</h1>
                <p className="lede">Loans, repayments and your portfolio in one place.</p>
                {error && (
                    <div className="notice error" role="alert">
                        {error}
                    </div>
                )}
                <Field label="Email">
                    <input type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} required autoFocus />
                </Field>
                <Field label="Password">
                    <input type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
                </Field>
                <Button type="submit" busy={busy}>
                    Sign in
                </Button>
                {import.meta.env.DEV && (
                    <p className="muted" style={{ marginTop: 20, fontSize: '0.875rem' }}>
                        Demo (development only): owner@demo.lendledger.test or officer@demo.lendledger.test, password Demo#2026pass
                    </p>
                )}
            </form>
        </div>
    )
}