import { useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { api, describeError } from '../api.js'
import { useAuth } from '../auth.jsx'
import { ProductFields, RuleExample, RuleFields } from '../components/LenderForms.jsx'
import { Button, Field } from '../components/ui.jsx'
import { emptyRule, productToPayload, ruleToPayload } from '../rules.js'

/** A new lender creates their account and sets the rules they lend by. Everything after this is theirs alone. */
export default function Register() {
    const { user, login } = useAuth()
    const [account, setAccount] = useState({ businessName: '', ownerName: '', email: '', password: '', accessCode: '' })
    const [product, setProduct] = useState({ name: 'Monthly Loan', minPrincipal: '1000', maxPrincipal: '100000', maxTermPeriods: '6' })
    const [rule, setRule] = useState(emptyRule)
    const [error, setError] = useState('')
    const [busy, setBusy] = useState(false)

    if (user) return <Navigate to="/dashboard" replace />

    const setField = (key) => (e) => setAccount({ ...account, [key]: e.target.value })

    async function submit(e) {
        e.preventDefault()
        setBusy(true)
        setError('')
        try {
            await api.post('/api/v1/auth/register', {
                businessName: account.businessName.trim(),
                ownerName: account.ownerName.trim(),
                email: account.email.trim(),
                password: account.password,
                accessCode: account.accessCode.trim() || undefined,
                product: { ...productToPayload(product), rule: ruleToPayload(rule) },
            })
            await login(account.email.trim(), account.password)
        } catch (err) {
            setError(describeError(err))
            setBusy(false)
        }
    }

    return (
        <div className="register">
            <header className="register-head">
                <h1>Create your lender account</h1>
                <p className="lede">Tell us how you lend. These rules apply only to your business, and you can change them later.</p>
            </header>
            <form className="split" onSubmit={submit}>
                <div>
                    {error && (
                        <div className="notice error" role="alert">
                            {error}
                        </div>
                    )}
                    <div className="card">
                        <h2 className="section-title" style={{ margin: '0 0 12px' }}>
                            Your business
                        </h2>
                        <Field label="Business name">
                            <input value={account.businessName} onChange={setField('businessName')} required minLength={2} maxLength={120} autoComplete="organization" />
                        </Field>
                        <Field label="Your name">
                            <input value={account.ownerName} onChange={setField('ownerName')} required minLength={2} maxLength={120} autoComplete="name" />
                        </Field>
                        <Field label="Email" hint="You will sign in with this.">
                            <input type="email" value={account.email} onChange={setField('email')} required maxLength={200} autoComplete="username" />
                        </Field>
                        <Field label="Password" hint="At least 10 characters.">
                            <input type="password" value={account.password} onChange={setField('password')} required minLength={10} maxLength={100} autoComplete="new-password" />
                        </Field>
                        <Field label="Access code (if you were given one)">
                            <input value={account.accessCode} onChange={setField('accessCode')} maxLength={100} autoComplete="off" />
                        </Field>
                    </div>
                    <div className="card">
                        <h2 className="section-title" style={{ margin: '0 0 12px' }}>
                            What you lend
                        </h2>
                        <ProductFields value={product} onChange={setProduct} />
                    </div>
                    <div className="card">
                        <h2 className="section-title" style={{ margin: '0 0 12px' }}>
                            Your lending rules
                        </h2>
                        <RuleFields value={rule} onChange={setRule} />
                    </div>
                </div>
                <div>
                    <RuleExample rule={rule} sticky />
                    <div style={{ marginTop: 16 }}>
                        <Button type="submit" busy={busy} style={{ width: '100%' }}>
                            Create account
                        </Button>
                        <p className="muted" style={{ marginTop: 12, textAlign: 'center' }}>
                            Already registered? <Link to="/login">Sign in</Link>
                        </p>
                    </div>
                </div>
            </form>
        </div>
    )
}