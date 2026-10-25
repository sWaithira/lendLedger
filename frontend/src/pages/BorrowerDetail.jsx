import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, describeError } from '../api.js'
import { useAuth } from '../auth.jsx'
import LoanList from '../components/LoanList.jsx'
import { Badge, Button, Empty, ErrorNote, Field, Loading, PageHead, Sheet } from '../components/ui.jsx'
import { formatDate } from '../format.js'
import { useFetch } from '../hooks.js'

export default function BorrowerDetail() {
    const { id } = useParams()
    const { can } = useAuth()
    const borrower = useFetch(`/api/v1/borrowers/${id}`)
    const loans = useFetch(`/api/v1/loans?borrowerId=${id}&size=50`)
    const [actionError, setActionError] = useState(null)
    const [busy, setBusy] = useState(false)
    const [editing, setEditing] = useState(false)

    const b = borrower.data
    const archived = b?.status === 'ARCHIVED'
    const hasActiveLoans = loans.data?.items.some((l) => l.status === 'ACTIVE') ?? false

    async function toggleArchive() {
        setBusy(true)
        setActionError(null)
        try {
            await api.post(`/api/v1/borrowers/${id}/${archived ? 'restore' : 'archive'}`)
            borrower.reload()
        } catch (err) {
            setActionError({ message: describeError(err) })
        } finally {
            setBusy(false)
        }
    }

    return (
        <>
            <Link className="back" to="/borrowers">
                ‹ Borrowers
            </Link>
            <ErrorNote error={borrower.error} onRetry={borrower.reload} />
            <ErrorNote error={actionError} />
            {borrower.loading && !b && <Loading rows={2} />}
            {b && (
                <>
                    <PageHead
                        title={b.fullName}
                        subtitle={`Added ${formatDate(b.createdAt.slice(0, 10))}${archived ? ' · Archived' : ''}`}
                        actions={
                            can('LOAN_CREATE') &&
                            !archived && (
                                <Link className="btn primary" to={`/loans/new?borrowerId=${b.id}`}>
                                    New loan
                                </Link>
                            )
                        }
                    />

                    <h2 className="section-title">Contact and ID</h2>
                    <div className="card">
                        <dl className="kv">
                            <dt>Phone</dt>
                            <dd className="num">{b.phone}</dd>
                            <dt>Email</dt>
                            <dd>{b.email || 'Not given'}</dd>
                            <dt>ID number</dt>
                            <dd>
                                {b.nationalId ? (
                                    <>
                                        <span className="num">{b.nationalId}</span>{' '}
                                        <Badge tone={b.kycStatus === 'VERIFIED' ? 'green' : 'orange'}>{b.kycStatus === 'VERIFIED' ? 'Verified' : 'Not verified'}</Badge>
                                    </>
                                ) : (
                                    'Not given'
                                )}
                            </dd>
                        </dl>
                        {b.nationalId && b.kycStatus !== 'VERIFIED' && (
                            <p className="muted" style={{ marginTop: 12, fontSize: '0.8125rem' }}>
                                The ID number is stored as entered. It has not been checked against any record.
                            </p>
                        )}
                        {can('BORROWER_WRITE') && (
                            <div style={{ marginTop: 16 }}>
                                <Button variant="secondary" onClick={() => setEditing(true)}>
                                    Edit details
                                </Button>
                            </div>
                        )}
                    </div>
                    {b.note && (
                        <>
                            <h2 className="section-title">Note</h2>
                            <div className="card">
                                <p>{b.note}</p>
                            </div>
                        </>
                    )}

                    <h2 className="section-title">Loans</h2>
                    {loans.loading && !loans.data && <Loading rows={2} />}
                    {loans.data && loans.data.items.length === 0 && (
                        <div className="list">
                            <Empty title="No loans yet">Create the first loan for this borrower.</Empty>
                        </div>
                    )}
                    {loans.data && loans.data.items.length > 0 && <LoanList loans={loans.data.items} showBorrower={false} />}

                    {can('BORROWER_WRITE') && (
                        <div style={{ marginTop: 32 }}>
                            <Button variant={archived ? 'secondary' : 'danger'} busy={busy} disabled={!archived && hasActiveLoans} onClick={toggleArchive}>
                                {archived ? 'Restore borrower' : 'Archive borrower'}
                            </Button>
                            <p className="muted" style={{ marginTop: 8, fontSize: '0.875rem' }}>
                                {archived
                                    ? 'Archived borrowers are hidden from the main list and cannot be given new loans. Their history is kept.'
                                    : hasActiveLoans
                                        ? 'You can archive this borrower once their active loans are paid off or cancelled.'
                                        : 'Archiving hides this borrower from the main list. Their loans and payments are kept.'}
                            </p>
                        </div>
                    )}
                </>
            )}
            {editing && b && (
                <EditBorrowerSheet
                    borrower={b}
                    onClose={() => setEditing(false)}
                    onSaved={() => {
                        setEditing(false)
                        borrower.reload()
                    }}
                />
            )}
        </>
    )
}

function EditBorrowerSheet({ borrower, onClose, onSaved }) {
    const [form, setForm] = useState({
        fullName: borrower.fullName,
        phone: borrower.phone,
        email: borrower.email || '',
        nationalId: borrower.nationalId || '',
        note: borrower.note || '',
    })
    const [error, setError] = useState('')
    const [busy, setBusy] = useState(false)
    const set = (key) => (e) => setForm({ ...form, [key]: e.target.value })

    async function submit(e) {
        e.preventDefault()
        setBusy(true)
        setError('')
        try {
            await api.put(`/api/v1/borrowers/${borrower.id}`, {
                fullName: form.fullName,
                phone: form.phone,
                email: form.email.trim() || undefined,
                nationalId: form.nationalId.trim() || undefined,
                note: form.note,
            })
            onSaved()
        } catch (err) {
            setError(describeError(err))
            setBusy(false)
        }
    }

    return (
        <Sheet
            title="Edit details"
            onClose={onClose}
            footer={
                <>
                    <Button variant="secondary" type="button" onClick={onClose}>
                        Cancel
                    </Button>
                    <Button type="submit" form="edit-borrower" busy={busy}>
                        Save
                    </Button>
                </>
            }
        >
            <form id="edit-borrower" onSubmit={submit}>
                {error && (
                    <div className="notice error" role="alert">
                        {error}
                    </div>
                )}
                <Field label="Full name">
                    <input value={form.fullName} onChange={set('fullName')} required maxLength={200} autoComplete="off" />
                </Field>
                <Field label="Phone">
                    <input type="tel" value={form.phone} onChange={set('phone')} required maxLength={30} autoComplete="off" />
                </Field>
                <Field label="Email (optional)">
                    <input type="email" value={form.email} onChange={set('email')} maxLength={200} autoComplete="off" />
                </Field>
                <Field label="ID number (optional)" hint="Stored as entered. It is not verified.">
                    <input value={form.nationalId} onChange={set('nationalId')} maxLength={40} autoComplete="off" />
                </Field>
                <Field label="Note (optional)">
                    <textarea value={form.note} onChange={set('note')} maxLength={1000} />
                </Field>
            </form>
        </Sheet>
    )
}