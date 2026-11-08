import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api, describeError } from '../api.js'
import { useAuth } from '../auth.jsx'
import { Button, Empty, ErrorNote, Field, Loading, PageHead, Segmented, Sheet, ViewToggle } from '../components/ui.jsx'
import { initials } from '../format.js'
import { useDebounced, useFetch, useStoredState } from '../hooks.js'

const PAGE_SIZE = 12

export default function Borrowers() {
    const { can } = useAuth()
    const navigate = useNavigate()
    const [q, setQ] = useState('')
    const [status, setStatus] = useState('ACTIVE')
    const [page, setPage] = useState(0)
    const [creating, setCreating] = useState(false)
    const [view, setView] = useStoredState('view:borrowers', 'list')
    const term = useDebounced(q)
    const { data, error, loading, reload } = useFetch(
        `/api/v1/borrowers?status=${status}&page=${page}&size=${PAGE_SIZE}&q=${encodeURIComponent(term)}`,
    )

    const from = data ? data.page * data.size + 1 : 0
    const to = data ? data.page * data.size + data.items.length : 0

    return (
        <>
            <PageHead
                title="Borrowers"
                subtitle="The people you lend to."
                actions={
                    can('BORROWER_WRITE') && (
                        <span className="head-actions">
                            {can('LOAN_CREATE') && (
                                <Link className="btn secondary" to="/import">
                                    Import from Excel
                                </Link>
                            )}
                            <Button onClick={() => setCreating(true)}>New borrower</Button>
                        </span>
                    )
                }
            />

            <div className="toolbar">
                <div className="grow">
                    <input
                        className="search"
                        type="search"
                        placeholder="Search by name or phone"
                        aria-label="Search borrowers"
                        value={q}
                        onChange={(e) => {
                            setQ(e.target.value)
                            setPage(0)
                        }}
                    />
                </div>
                <Segmented
                    label="Show"
                    value={status}
                    onChange={(v) => {
                        setStatus(v)
                        setPage(0)
                    }}
                    options={[
                        { value: 'ACTIVE', label: 'Active' },
                        { value: 'ARCHIVED', label: 'Archived' },
                    ]}
                />
                <ViewToggle value={view} onChange={setView} />
            </div>

            <ErrorNote error={error} onRetry={reload} />
            {loading && !data && <Loading />}
            {data && data.items.length === 0 && (
                <div className="list">
                    <Empty title={term ? 'No matches' : 'No borrowers yet'}>
                        {term ? 'Try a different name or number.' : 'Add your first borrower to start lending.'}
                    </Empty>
                </div>
            )}
            {data && data.items.length > 0 && (
                <>
                    {view === 'cards' ? (
                        <div className="cards">
                            {data.items.map((b) => (
                                <Link key={b.id} to={`/borrowers/${b.id}`} className="card-item card-person">
                                    <div className="avatar" aria-hidden="true">
                                        {initials(b.fullName)}
                                    </div>
                                    <div className="title">{b.fullName}</div>
                                    <div className="sub num">{b.phone}</div>
                                </Link>
                            ))}
                        </div>
                    ) : (
                        <div className="list">
                            {data.items.map((b) => (
                                <Link key={b.id} to={`/borrowers/${b.id}`} className="row">
                                    <div className="avatar" aria-hidden="true">
                                        {initials(b.fullName)}
                                    </div>
                                    <div className="grow">
                                        <div className="title">{b.fullName}</div>
                                        <div className="sub num">{b.phone}</div>
                                    </div>
                                    <span className="chev" aria-hidden="true">
                    ›
                  </span>
                                </Link>
                            ))}
                        </div>
                    )}
                    <div className="pager">
            <span className="num">
              {from}–{to} of {data.totalItems}
            </span>
                        <span>
              <Button variant="plain" disabled={page === 0} onClick={() => setPage(page - 1)}>
                Previous
              </Button>
              <Button variant="plain" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>
                Next
              </Button>
            </span>
                    </div>
                </>
            )}

            {creating && (
                <NewBorrowerSheet
                    onClose={() => setCreating(false)}
                    onCreated={(b) => {
                        setCreating(false)
                        navigate(`/borrowers/${b.id}`)
                    }}
                />
            )}
        </>
    )
}

/** Creating a borrower whose phone number already exists is allowed, but needs a stated reason (F-005). */
function NewBorrowerSheet({ onClose, onCreated }) {
    const [form, setForm] = useState({ fullName: '', phone: '', email: '', nationalId: '', note: '' })
    const [matches, setMatches] = useState(null)
    const [reason, setReason] = useState('')
    const [error, setError] = useState('')
    const [busy, setBusy] = useState(false)

    const set = (key) => (e) => {
        setForm({ ...form, [key]: e.target.value })
        if (key === 'phone') setMatches(null)
    }

    async function submit(e) {
        e.preventDefault()
        setBusy(true)
        setError('')
        try {
            const created = await api.post('/api/v1/borrowers', {
                ...form,
                email: form.email.trim() || undefined,
                nationalId: form.nationalId.trim() || undefined,
                duplicateReason: matches ? reason : undefined,
            })
            onCreated(created)
        } catch (err) {
            if (err.code === 'possible_duplicate') setMatches(err.body.matches)
            else setError(describeError(err))
        } finally {
            setBusy(false)
        }
    }

    return (
        <Sheet
            title="New borrower"
            onClose={onClose}
            footer={
                <>
                    <Button variant="secondary" type="button" onClick={onClose}>
                        Cancel
                    </Button>
                    <Button type="submit" form="new-borrower" busy={busy} disabled={matches && !reason.trim()}>
                        {matches ? 'Save anyway' : 'Save borrower'}
                    </Button>
                </>
            }
        >
            <form id="new-borrower" onSubmit={submit}>
                {error && (
                    <div className="notice error" role="alert">
                        {error}
                    </div>
                )}
                <Field label="Full name">
                    <input value={form.fullName} onChange={set('fullName')} required maxLength={200} autoComplete="off" />
                </Field>
                <Field label="Phone" hint="For example 0712 345 678 or +254 712 345 678">
                    <input type="tel" value={form.phone} onChange={set('phone')} required maxLength={30} autoComplete="off" />
                </Field>
                <Field label="Email (optional)" hint="Lets you email them when a payment is late.">
                    <input type="email" value={form.email} onChange={set('email')} maxLength={200} autoComplete="off" />
                </Field>
                <Field label="ID number">
                    <input required value={form.nationalId} onChange={set('nationalId')} maxLength={40} autoComplete="off" />
                </Field>
                <Field label="Note (optional)">
                    <textarea value={form.note} onChange={set('note')} maxLength={1000} />
                </Field>
                {matches && (
                    <div className="notice warn" role="alert">
                        <strong>This number already belongs to someone.</strong>
                        <ul>
                            {matches.map((m) => (
                                <li key={m.id}>
                                    <a href={`/borrowers/${m.id}`} target="_blank" rel="noreferrer">
                                        {m.fullName}
                                    </a>
                                </li>
                            ))}
                        </ul>
                        <div style={{ marginTop: 12 }}>
                            <Field label="Why is this a different person?">
                                <textarea value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} required />
                            </Field>
                        </div>
                    </div>
                )}
            </form>
        </Sheet>
    )
}