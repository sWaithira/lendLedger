import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api, describeError } from '../api.js'
import { useAuth } from '../auth.jsx'
import PasswordChecklist from '../components/PasswordChecklist.jsx'
import PasswordInput from '../components/PasswordInput.jsx'
import { Button, Field, PageHead } from '../components/ui.jsx'
import { ROLE_LABEL } from '../format.js'
import { isStrong } from '../passwordRules.js'

export default function Profile() {
    const { user, can, logout, markPasswordChanged } = useAuth()
    const forced = Boolean(user.mustChangePassword)
    const [current, setCurrent] = useState('')
    const [next, setNext] = useState('')
    const [again, setAgain] = useState('')
    const [error, setError] = useState('')
    const [done, setDone] = useState(false)
    const [busy, setBusy] = useState(false)

    const items = [
        can('REPORT_VIEW') && { to: '/performance', title: 'Performance', sub: 'How the business did over any months' },
        can('LOAN_CREATE') && { to: '/import', title: 'Import from Excel', sub: 'Add many borrowers and loans at once' },
        can('SETTINGS_MANAGE') && { to: '/settings', title: 'Settings', sub: 'Lending rules and products' },
    ].filter(Boolean)

    const mismatch = again !== '' && again !== next
    const ready = current !== '' && isStrong(next) && next === again

    async function submit(e) {
        e.preventDefault()
        setBusy(true)
        setError('')
        try {
            await api.post('/api/v1/account/password', { currentPassword: current, newPassword: next })
            setCurrent('')
            setNext('')
            setAgain('')
            setDone(true)
            markPasswordChanged()
        } catch (err) {
            setError(describeError(err))
        } finally {
            setBusy(false)
        }
    }

    return (
        <>
            <PageHead title="Profile" subtitle={user.name} />
            {forced && (
                <div className="notice info" role="status">
                    Choose your own password to continue.
                </div>
            )}
            {done && !forced && (
                <div className="notice info" role="status">
                    Your password was changed.
                </div>
            )}

            {!forced && items.length > 0 && (
                <>
                    <h2 className="section-title">Your business</h2>
                    <div className="list">
                        {items.map((i) => (
                            <Link key={i.to} to={i.to} className="row">
                                <div className="grow">
                                    <div className="title">{i.title}</div>
                                    <div className="sub">{i.sub}</div>
                                </div>
                                <span className="chev" aria-hidden="true">
                                    ›
                                </span>
                            </Link>
                        ))}
                    </div>
                </>
            )}

            <h2 className="section-title" style={{ marginTop: 24 }}>Account</h2>
            <div className="card">
                <dl className="kv">
                    <dt>Name</dt>
                    <dd>{user.name}</dd>
                    <dt>Email</dt>
                    <dd>{user.email}</dd>
                    <dt>Role</dt>
                    <dd>{ROLE_LABEL[user.role] || user.role}</dd>
                </dl>
            </div>

            <form className="card" onSubmit={submit} style={{ marginTop: 16 }}>
                <h2 className="section-title" style={{ margin: '0 0 12px' }}>
                    Change password
                </h2>
                {error && (
                    <div className="notice error" role="alert">
                        {error}
                    </div>
                )}
                <Field label="Current password">
                    <PasswordInput value={current} onChange={(e) => setCurrent(e.target.value)} required autoComplete="current-password" />
                </Field>
                <Field label="New password">
                    <>
                        <PasswordInput value={next} onChange={(e) => setNext(e.target.value)} required maxLength={100} autoComplete="new-password" />
                        <PasswordChecklist password={next} />
                    </>
                </Field>
                <Field label="Repeat new password" hint={mismatch ? 'The two passwords do not match.' : undefined}>
                    <PasswordInput value={again} onChange={(e) => setAgain(e.target.value)} required maxLength={100} autoComplete="new-password" />
                </Field>
                <Button type="submit" busy={busy} disabled={!ready}>
                    Change password
                </Button>
            </form>

            <div style={{ marginTop: 24 }}>
                <Button variant="secondary" onClick={logout}>
                    Sign out
                </Button>
            </div>
        </>
    )
}