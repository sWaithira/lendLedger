import { useState } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { describeError } from '../api.js'
import { useAuth } from '../auth.jsx'
import { Button, Field } from '../components/ui.jsx'
import PasswordInput from '../components/PasswordInput.jsx'
import { DEMO_EMAIL, DEMO_WHATSAPP } from '../contact.js'

export default function Login() {
    const { user, login } = useAuth()
    const location = useLocation()
    const [email, setEmail] = useState(location.state?.email || '')
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
                {location.state?.created && (
                    <div className="notice info" role="status">
                        Your account was created. Sign in to continue.
                    </div>
                )}
                {error && (
                    <div className="notice error" role="alert">
                        {error}
                    </div>
                )}
                <Field label="Email">
                    <input type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} required autoFocus={!location.state?.email} />
                </Field>
                <Field label="Password">
                    <PasswordInput
                        autoComplete="current-password"
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        required
                        autoFocus={Boolean(location.state?.email)}
                    />
                </Field>
                <Button type="submit" busy={busy}>
                    Sign in
                </Button>
                {(DEMO_WHATSAPP || DEMO_EMAIL) && (
                    <p className="muted" style={{ marginTop: 16, textAlign: 'center' }}>
                        New lender?{' '}
                        {DEMO_WHATSAPP && (
                            <a href={`https://wa.me/${DEMO_WHATSAPP}?text=${encodeURIComponent('Hello, I would like a LendLedger demo.')}`} target="_blank" rel="noreferrer">
                                Book a demo
                            </a>
                        )}
                        {DEMO_WHATSAPP && DEMO_EMAIL && ' · '}
                        {DEMO_EMAIL && <a href={`mailto:${DEMO_EMAIL}?subject=${encodeURIComponent('LendLedger demo request')}`}>Email us</a>}
                    </p>
                )}
            </form>
        </div>
    )
}