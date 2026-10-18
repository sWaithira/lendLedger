import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, describeError } from '../api.js'
import { useAuth } from '../auth.jsx'
import LoanList from '../components/LoanList.jsx'
import { Button, Empty, ErrorNote, Loading, PageHead } from '../components/ui.jsx'
import { formatDate } from '../format.js'
import { useFetch } from '../hooks.js'

export default function BorrowerDetail() {
    const { id } = useParams()
    const { can } = useAuth()
    const borrower = useFetch(`/api/v1/borrowers/${id}`)
    const loans = useFetch(`/api/v1/loans?borrowerId=${id}&size=50`)
    const [actionError, setActionError] = useState(null)
    const [busy, setBusy] = useState(false)

    const b = borrower.data
    const archived = b?.status === 'ARCHIVED'

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
                        subtitle={`${b.phone} · Added ${formatDate(b.createdAt.slice(0, 10))}${archived ? ' · Archived' : ''}`}
                        actions={
                            can('LOAN_CREATE') &&
                            !archived && (
                                <Link className="btn primary" to={`/loans/new?borrowerId=${b.id}`}>
                                    New loan
                                </Link>
                            )
                        }
                    />
                    {b.note && (
                        <div className="card">
                            <p>{b.note}</p>
                        </div>
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
                            <Button variant={archived ? 'secondary' : 'danger'} busy={busy} onClick={toggleArchive}>
                                {archived ? 'Restore borrower' : 'Archive borrower'}
                            </Button>
                        </div>
                    )}
                </>
            )}
        </>
    )
}