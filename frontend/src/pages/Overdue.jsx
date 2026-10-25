import { useState } from 'react'
import { Link } from 'react-router-dom'
import ContactSheet from '../components/ContactSheet.jsx'
import { Button, Empty, ErrorNote, Loading, PageHead } from '../components/ui.jsx'
import { formatDate, formatMoney, loanNumber } from '../format.js'
import { useFetch } from '../hooks.js'

const PAGE_SIZE = 12

/** Loans become overdue only after the lender's own grace period ends. Longest overdue first. */
export default function Overdue() {
    const [page, setPage] = useState(0)
    const [contact, setContact] = useState(null)
    const { data, error, loading, reload } = useFetch(`/api/v1/overdue?page=${page}&size=${PAGE_SIZE}`)
    const from = data ? data.page * data.size + 1 : 0
    const to = data ? data.page * data.size + data.items.length : 0

    return (
        <>
            <PageHead
                title="Overdue"
                subtitle="Past the grace period, longest first. Contact them, then record what they say."
                actions={
                    <a className="btn secondary" href="/api/v1/exports/overdue.csv" download>
                        Download CSV
                    </a>
                }
            />
            <ErrorNote error={error} onRetry={reload} />
            {loading && !data && <Loading />}
            {data && data.items.length === 0 && (
                <div className="list">
                    <Empty title="All caught up">Nothing is past its grace period. Good news.</Empty>
                </div>
            )}
            {data && data.items.length > 0 && (
                <>
                    <div className="list">
                        {data.items.map((o) => (
                            <div key={o.loanId} className="row static">
                                <Link to={`/loans/${o.loanId}`} className="row-main">
                                    <div className="grow">
                                        <div className="title">{o.borrowerName}</div>
                                        <div className="sub">
                                            {loanNumber(o.loanNumber)} · <span className="late">{o.daysOverdue} days overdue</span> · due {formatDate(o.dueDate)} ·{' '}
                                            {o.lastPaymentDate ? `last payment ${formatDate(o.lastPaymentDate)}` : 'no payments yet'}
                                        </div>
                                    </div>
                                    <div className="money title">{formatMoney(o.outstanding.total)}</div>
                                </Link>
                                {(o.borrowerPhone || o.borrowerEmail) && (
                                    <Button variant="secondary" aria-label={`Contact ${o.borrowerName}`} onClick={() => setContact(o)}>
                                        Contact
                                    </Button>
                                )}
                            </div>
                        ))}
                    </div>
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
            {contact && <ContactSheet item={contact} onClose={() => setContact(null)} />}
        </>
    )
}