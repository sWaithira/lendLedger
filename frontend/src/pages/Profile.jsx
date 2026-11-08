import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api, describeError } from '../api.js'
import { useAuth } from '../auth.jsx'
import PasswordChecklist from '../components/PasswordChecklist.jsx'
import PasswordInput from '../components/PasswordInput.jsx'
import { Button, Field, PageHead } from '../components/ui.jsx'
import { ROLE_LABEL } from '../format.js'
import { isStrong } from '../passwordRules.js'

function Section({ id, title, sub, open, onToggle, flush = false, children }) {
    return (
        <section className={`acc-item ${open ? 'open' : ''}`}>
            <button type="button" className="acc-head" aria-expanded={open} aria-controls={`acc-${id}`} onClick={() => onToggle(id)}>
                <span className="grow">
                    <span className="acc-title">{title}</span>
                    <span className="acc-sub-line muted">{sub}</span>
                </span>
                <span className="chev" aria-hidden="true">
                    ›
                </span>
            </button>
            {open && (
                <div id={`acc-${id}`} className={`acc-body ${flush ? 'flush' : ''}`}>
                    {children}
                </div>
            )}
        </section>
    )
}

export default function Profile() {
    const { user, can, logout, markPasswordChanged } = useAuth()
    const navigate = useNavigate()
    const forced = Boolean(user.mustChangePassword)
    const hasBusiness = can('SETTINGS_MANAGE') || can('REPORT_VIEW') || can('LOAN_CREATE')
    const [section, setSection] = useState(forced || !hasBusiness ? 'account' : 'business')
    const [current, setCurrent] = useState('')
    const [next, setNext] = useState('')
    const [again, setAgain] = useState('')
    const [error, setError] = useState('')
    const [done, setDone] = useState(false)
    const [busy, setBusy] = useState(false)

    const items = [
        can('SETTINGS_MANAGE') && { to: '/settings', title: 'Settings', sub: 'Lending rules and products' },
        can('REPORT_VIEW') && { to: '/reports', title: 'Reports', sub: 'Download your data as CSV files' },
        can('LOAN_CREATE') && { to: '/import', title: 'Import from Excel', sub: 'Add many borrowers and loans at once' },
        can('REPORT_VIEW') && { to: '/performance', title: 'Performance', sub: 'How the business did over any months', phoneOnly: true },
    ].filter(Boolean)

    const toggle = (id) => setSection((s) => (s === id ? '' : id))
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
            markPasswordChanged()
            if (forced) {
                navigate('/', { replace: true })
            } else {
                setDone(true)
                setSection('account')
            }
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
            {done && (
                <div className="notice info" role="status">
                    Your password was changed.
                </div>
            )}

            <Section id="account" title="Account" sub="Your details and password" open={section === 'account'} onToggle={toggle}>
                <div className="acc-form">
                    <dl className="kv">
                        <dt>Name</dt>
                        <dd>{user.name}</dd>
                        <dt>Email</dt>
                        <dd>{user.email}</dd>
                        <dt>Role</dt>
                        <dd>{ROLE_LABEL[user.role] || user.role}</dd>
                    </dl>
                    <h3 className="acc-sub">Change password</h3>
                    {error && (
                        <div className="notice error" role="alert">
                            {error}
                        </div>
                    )}
                    <form onSubmit={submit}>
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
                </div>
            </Section>

            {!forced && items.length > 0 && (
                <Section id="business" title="Business" sub="Settings, reports and import" open={section === 'business'} onToggle={toggle} flush>
                    {items.map((i) => (
                        <Link key={i.to} to={i.to} className={`row ${i.phoneOnly ? 'phone-only' : ''}`}>
                            <div className="grow">
                                <div className="title">{i.title}</div>
                                <div className="sub">{i.sub}</div>
                            </div>
                            <span className="chev" aria-hidden="true">
                                ›
                            </span>
                        </Link>
                    ))}
                </Section>
            )}
            <div className="profile-signout">
                <Button variant="secondary" onClick={logout}>
                    Sign out
                </Button>
            </div>
        </>
    )
}