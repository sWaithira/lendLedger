import { useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { api, describeError } from '../api.js'
import { useAuth } from '../auth.jsx'
import { ProductFields, RuleExample, RuleFields } from '../components/LenderForms.jsx'
import PasswordInput from '../components/PasswordInput.jsx'
import { Button, Field } from '../components/ui.jsx'
import { emptyRule, productToPayload, ruleToPayload } from '../rules.js'

const STEPS = ['Your business', 'What you lend', 'Your lending rules']

/** A new lender creates their account in three steps and sets the rules they lend by. They then sign in themselves. */
export default function Register() {
    const { user } = useAuth()
    const [step, setStep] = useState(0)
    const [account, setAccount] = useState({ businessName: '', ownerName: '', email: '', password: '', accessCode: '' })
    const [product, setProduct] = useState({ name: 'Monthly Loan', minPrincipal: '1000', maxPrincipal: '100000', maxTermPeriods: '6' })
    const [rule, setRule] = useState(emptyRule)
    const [createdFor, setCreatedFor] = useState(null)
    const [error, setError] = useState('')
    const [busy, setBusy] = useState(false)

    if (user) return <Navigate to="/dashboard" replace />

    if (createdFor) {
        return (
            <div className="signin">
                <div className="panel">
                    <h1>Your account is ready</h1>
                    <p className="lede">
                        {createdFor.businessName} has been created. Sign in with <strong>{createdFor.email}</strong> to start lending.
                    </p>
                    <Link className="btn primary" to="/login" state={{ email: createdFor.email, created: true }}>
                        Continue to sign in
                    </Link>
                </div>
            </div>
        )
    }

    const setField = (key) => (e) => setAccount({ ...account, [key]: e.target.value })
    const last = step === STEPS.length - 1

    function goTo(next) {
        setError('')
        setStep(next)
        window.scrollTo(0, 0)
    }

    async function submit(e) {
        e.preventDefault()
        if (!last) {
            goTo(step + 1)
            return
        }
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
            setCreatedFor({ businessName: account.businessName.trim(), email: account.email.trim() })
        } catch (err) {
            setError(describeError(err))
            setBusy(false)
        }
    }

    const nav = (
        <div className="step-nav">
            {step > 0 && (
                <button type="button" className="btn" onClick={() => goTo(step - 1)}>
                    Back
                </button>
            )}
            {last ? (
                <Button type="submit" busy={busy}>
                    Create account
                </Button>
            ) : (
                <Button type="submit">Next</Button>
            )}
        </div>
    )

    return (
        <div className="register">
            <header className="register-head">
                <h1>Create your lender account</h1>
                <p className="lede">Tell us how you lend. These rules apply only to your business, and you can change them later.</p>
            </header>
            <div className="step-bar" aria-hidden="true">
                {STEPS.map((s, i) => (
                    <span key={s} className={i <= step ? 'on' : ''} />
                ))}
            </div>
            <p className="step-label" aria-live="polite">
                Step {step + 1} of {STEPS.length} · {STEPS[step]}
            </p>
            <form onSubmit={submit}>
                {error && (
                    <div className="notice error" role="alert">
                        {error}
                    </div>
                )}

                {step === 0 && (
                    <div className="card">
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
                            <PasswordInput
                                value={account.password}
                                onChange={setField('password')}
                                required
                                minLength={10}
                                maxLength={100}
                                autoComplete="new-password"
                            />
                        </Field>
                        <Field label="Access code (if you were given one)">
                            <input value={account.accessCode} onChange={setField('accessCode')} maxLength={100} autoComplete="off" />
                        </Field>
                        {nav}
                        <p className="muted" style={{ marginTop: 12, textAlign: 'center' }}>
                            Already registered? <Link to="/login">Sign in</Link>
                        </p>
                    </div>
                )}

                {step === 1 && (
                    <div className="card">
                        <ProductFields value={product} onChange={setProduct} />
                        {nav}
                    </div>
                )}

                {step === 2 && (
                    <div className="split">
                        <div className="card">
                            <RuleFields value={rule} onChange={setRule} />
                            {nav}
                        </div>
                        <div>
                            <RuleExample rule={rule} sticky />
                        </div>
                    </div>
                )}
            </form>
        </div>
    )
}