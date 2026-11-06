import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useFetch } from '../hooks.js'

const PAGE_SIZE = 12

export default function Overdue() {
    const [page, setPage] = useState(0)
    const from = data ? data.page * data.size + 1 : 0
    const to = data ? data.page * data.size + data.items.length : 0

    return (
        <>
            <PageHead
                title="Overdue"
                actions={
                        Download CSV
                    </a>
                }
            />
            <ErrorNote error={error} onRetry={reload} />
            {loading && !data && <Loading />}
            {data && data.items.length === 0 && (
                <div className="list">
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
                                            {o.lastPaymentDate ? `last payment ${formatDate(o.lastPaymentDate)}` : 'no payments yet'}
                                        </div>
                                    </div>
                                    <div className="money title">{formatMoney(o.outstanding.total)}</div>
                                </Link>
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