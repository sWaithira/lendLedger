import { useState } from 'react'
import { api, describeError } from '../api.js'
import { useAuth } from '../auth.jsx'
import PasswordChecklist from '../components/PasswordChecklist.jsx'
import PasswordInput from '../components/PasswordInput.jsx'
import { isStrong } from '../passwordRules.js'
import { Badge, Button, Empty, ErrorNote, Field, Loading, PageHead, Sheet } from '../components/ui.jsx'
import { formatDate, todayIso } from '../format.js'
import { useFetch } from '../hooks.js'

const PLAN_LABEL = { TRIAL: 'Trial', STANDARD: 'Standard', PRO: 'Pro' }

/** The operator console: lenders, their subscriptions and access. Counts only, never borrower or loan data. */
export default function Platform() {
    const { can } = useAuth()
    const allowed = can('PLATFORM_MANAGE')
    const { data, error, loading, reload } = useFetch(allowed ? '/api/v1/platform/lenders' : null)
    const [managing, setManaging] = useState(null)
    const [creating, setCreating] = useState(false)
    const [notice, setNotice] = useState('')

    function done(message) {
        setManaging(null)
        setNotice(message)
        reload()
    }

    if (!allowed) {
        return (
            <>
                <PageHead title="Platform" subtitle="Lenders and subscriptions." />
                <div className="list">
                    <Empty title="Not available">This area is for the platform operator.</Empty>
                </div>
            </>
        )
    }

    const lenders = data || []
    const today = todayIso()
    const expired = (l) => Boolean(l.subscriptionExpiresOn) && l.subscriptionExpiresOn < today
    const suspended = lenders.filter((l) => l.status === 'SUSPENDED').length
    const lapsed = lenders.filter((l) => l.status === 'ACTIVE' && expired(l)).length
    const active = lenders.length - suspended - lapsed

    return (
        <>
            <PageHead title="Platform" subtitle="Lenders and subscriptions. You see counts only, never borrower or loan data." />
            <ErrorNote error={error} onRetry={reload} />
            {loading && !data && <Loading rows={3} />}
            {notice && (
                <div className="notice info" role="status">
                    {notice}
                </div>
            )}

            {data && (
                <>
                    <div className="card-actions" style={{ marginBottom: 16 }}>
                        <Button onClick={() => setCreating(true)}>Add lender</Button>
                    </div>
                    <div className="tiles">
                        <div className="tile">
                            <small>Lenders</small>
                            <strong className="money">{lenders.length}</strong>
                        </div>
                        <div className="tile">
                            <small>Active</small>
                            <strong className="money">{active}</strong>
                        </div>
                        <div className={`tile ${suspended > 0 ? 'alert' : ''}`}>
                            <small>Suspended</small>
                            <strong className="money">{suspended}</strong>
                        </div>
                        <div className="tile">
                            <small>Expired</small>
                            <strong className="money">{lapsed}</strong>
                        </div>
                    </div>

                    {lenders.length === 0 && (
                        <div className="list">
                            <Empty title="No lenders yet">Add your first lender to get started.</Empty>
                        </div>
                    )}
                    <div className="list">
                        {lenders.map((l) => {
                            const isExpired = l.status === 'ACTIVE' && expired(l)
                            const state =
                                l.status === 'SUSPENDED'
                                    ? { label: 'Suspended', tone: 'red' }
                                    : isExpired
                                        ? { label: 'Expired', tone: 'orange' }
                                        : { label: 'Active', tone: 'green' }
                            return (
                                <div key={l.id} className="row static">
                                    <div className="grow">
                                        <div className="title">{l.name}</div>
                                        <div className="sub">
                                            {l.ownerEmail || 'No owner'} · {l.borrowers} borrower{l.borrowers === 1 ? '' : 's'} · {l.loans} loan
                                            {l.loans === 1 ? '' : 's'}
                                        </div>
                                        <div className="sub">
                                            {PLAN_LABEL[l.plan] || l.plan} · {l.subscriptionExpiresOn ? `Expires ${formatDate(l.subscriptionExpiresOn)}` : 'No expiry'} ·{' '}
                                            {l.lastLoginAt ? `Last sign-in ${formatDate(l.lastLoginAt.slice(0, 10))}` : 'Never signed in'}
                                        </div>
                                    </div>
                                    <Badge tone={state.tone}>{state.label}</Badge>
                                    <Button variant="secondary" aria-label={`Manage ${l.name}`} onClick={() => setManaging(l)}>
                                        Manage
                                    </Button>
                                </div>
                            )
                        })}
                    </div>
                </>
            )}

            {managing && <ManageSheet lender={managing} onClose={() => setManaging(null)} onDone={done} />}
            {creating && (
                <CreateSheet
                    onClose={() => setCreating(false)}
                    onDone={(message) => {
                        setCreating(false)
                        done(message)
                    }}
                />
            )}
        </>
    )
}

function ManageSheet({ lender, onClose, onDone }) {
    const [reason, setReason] = useState('')
    const [plan, setPlan] = useState(lender.plan)
    const [expires, setExpires] = useState(lender.subscriptionExpiresOn || '')
    const [password, setPassword] = useState('')
    const [error, setError] = useState('')
    const [busy, setBusy] = useState(false)
    const base = `/api/v1/platform/lenders/${lender.id}`
    const suspended = lender.status === 'SUSPENDED'

    async function run(call, message) {
        setBusy(true)
        setError('')
        try {
            await call()
            onDone(message)
        } catch (err) {
            setError(describeError(err))
            setBusy(false)
        }
    }

    return (
        <Sheet
            title={lender.name}
            onClose={onClose}
            footer={
                <Button variant="secondary" type="button" onClick={onClose}>
                    Close
                </Button>
            }
        >
            {error && (
                <div className="notice error" role="alert">
                    {error}
                </div>
            )}

            <h3 className="section-title">Access</h3>
            <Field label="Reason (optional)">
                <input value={reason} onChange={(e) => setReason(e.target.value)} maxLength={300} />
            </Field>
            <Button
                variant={suspended ? 'secondary' : 'danger'}
                busy={busy}
                onClick={() =>
                    run(
                        () => api.post(`${base}/${suspended ? 'reactivate' : 'suspend'}`, { reason: reason.trim() || undefined }),
                        suspended ? `${lender.name} can sign in again.` : `${lender.name} is suspended. Their users are signed out on their next click.`,
                    )
                }
            >
                {suspended ? 'Reactivate lender' : 'Suspend lender'}
            </Button>

            <h3 className="section-title" style={{ marginTop: 28 }}>Subscription</h3>
            <div className="pair">
                <Field label="Plan">
                    <select value={plan} onChange={(e) => setPlan(e.target.value)}>
                        {Object.entries(PLAN_LABEL).map(([value, label]) => (
                            <option key={value} value={value}>
                                {label}
                            </option>
                        ))}
                    </select>
                </Field>
                <Field label="Expires on" hint="Empty means no expiry.">
                    <input type="date" value={expires} onChange={(e) => setExpires(e.target.value)} />
                </Field>
            </div>
            <div className="card-actions">
                <Button
                    busy={busy}
                    onClick={() => run(() => api.put(`${base}/subscription`, { plan, expiresOn: expires || null }), 'Subscription saved.')}
                >
                    Save subscription
                </Button>
                <Button variant="secondary" busy={busy} onClick={() => run(() => api.post(`${base}/extend`), 'Extended by one month.')}>
                    Extend 1 month
                </Button>
            </div>

            <h3 className="section-title" style={{ marginTop: 28 }}>Owner password</h3>
            <Field label="New password" hint="The owner must choose their own password when they next sign in.">
                <>
                    <PasswordInput value={password} onChange={(e) => setPassword(e.target.value)} maxLength={100} autoComplete="new-password" />
                    <PasswordChecklist password={password} />
                </>
            </Field>
            <Button
                variant="secondary"
                busy={busy}
                disabled={!isStrong(password)}
                onClick={() => run(() => api.post(`${base}/owner-password`, { password }), 'Owner password updated.')}
            >
                Set owner password
            </Button>
        </Sheet>
    )
}

function CreateSheet({ onClose, onDone }) {
    const [f, setF] = useState({ businessName: '', ownerName: '', email: '', password: '', plan: 'TRIAL', expiresOn: '' })
    const [error, setError] = useState('')
    const [busy, setBusy] = useState(false)
    const set = (key) => (e) => setF({ ...f, [key]: e.target.value })

    async function submit(e) {
        e.preventDefault()
        setBusy(true)
        setError('')
        try {
            await api.post('/api/v1/platform/lenders', {
                ...f,
                businessName: f.businessName.trim(),
                ownerName: f.ownerName.trim(),
                email: f.email.trim(),
                expiresOn: f.expiresOn || null,
            })
            onDone(`${f.businessName.trim()} was added. Give ${f.email.trim()} the password you set.`)
        } catch (err) {
            setError(describeError(err))
            setBusy(false)
        }
    }

    return (
        <Sheet
            title="Add lender"
            onClose={onClose}
            footer={
                <>
                    <Button variant="secondary" type="button" onClick={onClose}>
                        Cancel
                    </Button>
                    <Button type="submit" form="create-lender" busy={busy} disabled={!isStrong(f.password)}>
                        Add lender
                    </Button>
                </>
            }
        >
            {error && (
                <div className="notice error" role="alert">
                    {error}
                </div>
            )}
            <form id="create-lender" onSubmit={submit}>
                <Field label="Business name">
                    <input value={f.businessName} onChange={set('businessName')} required minLength={2} maxLength={120} autoComplete="off" />
                </Field>
                <Field label="Owner name">
                    <input value={f.ownerName} onChange={set('ownerName')} required minLength={2} maxLength={120} autoComplete="off" />
                </Field>
                <Field label="Owner email">
                    <input type="email" value={f.email} onChange={set('email')} required autoComplete="off" />
                </Field>
                <Field label="Temporary password" hint="Give it to the owner privately. They choose their own on first sign-in.">
                    <>
                        <PasswordInput value={f.password} onChange={set('password')} required maxLength={100} autoComplete="new-password" />
                        <PasswordChecklist password={f.password} />
                    </>
                </Field>
                <div className="pair">
                    <Field label="Plan">
                        <select value={f.plan} onChange={set('plan')}>
                            {Object.entries(PLAN_LABEL).map(([value, label]) => (
                                <option key={value} value={value}>
                                    {label}
                                </option>
                            ))}
                        </select>
                    </Field>
                    <Field label="Expires on" hint="Empty means no expiry.">
                        <input type="date" value={f.expiresOn} onChange={set('expiresOn')} />
                    </Field>
                </div>
            </form>
        </Sheet>
    )
}
