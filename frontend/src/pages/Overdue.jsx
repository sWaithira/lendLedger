import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Button, Empty, ErrorNote, Loading, PageHead, Segmented } from '../components/ui.jsx'
import { formatDate, formatMoney } from '../format.js'
import { useFetch } from '../hooks.js'

const PAGE_SIZE = 12

export default function Overdue() {
    const [minDays, setMinDays] = useState('1')
    const [page, setPage] = useState(0)
    const { data, error, loading, reload } = useFetch(`/api/v1/overdue?minDays=${minDays}&page=${page}&size=${PAGE_SIZE}`)
    const from = data ? data.page * data.size + 1 : 0
    const to = data ? data.page * data.size + data.items.length : 0

    return (
        <>
            <PageHead
                title="Overdue"
                subtitle="Longest overdue first. Call, then record what they say."
                actions={
                    <a className="btn secondary" href={`/api/v1/exports/overdue.csv?minDays=${minDays}`} download>
                        Download CSV
                    </a>
                }
            />
            <div className="toolbar">
                <Segmented
                    label="How overdue"
                    value={minDays}
                    onChange={(v) => {
                        setMinDays(v)
                        setPage(0)
                    }}
                    options={[
                        { value: '1', label: 'All' },
                        { value: '30', label: '30+ days' },
                        { value: '90', label: '90+ days' },
                    ]}
                />
            </div>
            <ErrorNote error={error} onRetry={reload} />
            {loading && !data && <Loading />}
            {data && data.items.length === 0 && (
                <div className="list">
                    <Empty title="All caught up">No loans match. Good news.</Empty>
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
                                            <span className="late">{o.daysOverdue} days overdue</span> · due {formatDate(o.dueDate)} ·{' '}
                                            {o.lastPaymentDate ? `last payment ${formatDate(o.lastPaymentDate)}` : 'no payments yet'}
                                        </div>
                                    </div>
                                    <div className="money title">{formatMoney(o.outstanding.total)}</div>
                                </Link>
                                {o.borrowerPhone && (
                                    <a className="btn secondary" href={`tel:+${o.borrowerPhone}`} aria-label={`Call ${o.borrowerName}`}>
                                        Call
                                    </a>
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
        </>
    )
}