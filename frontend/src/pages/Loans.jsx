import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth.jsx'
import LoanList from '../components/LoanList.jsx'
import { Button, Empty, ErrorNote, Loading, PageHead, Segmented } from '../components/ui.jsx'
import { useFetch } from '../hooks.js'

const PAGE_SIZE = 12

export default function Loans() {
    const { can } = useAuth()
    const [status, setStatus] = useState('ACTIVE')
    const [page, setPage] = useState(0)
    const { data, error, loading, reload } = useFetch(
        `/api/v1/loans?page=${page}&size=${PAGE_SIZE}${status ? `&status=${status}` : ''}`,
    )
    const from = data ? data.page * data.size + 1 : 0
    const to = data ? data.page * data.size + data.items.length : 0

    return (
        <>
            <PageHead
                title="Loans"
                subtitle="What is owed, and when."
                actions={
                    can('LOAN_CREATE') && (
                        <Link className="btn primary" to="/loans/new">
                            New loan
                        </Link>
                    )
                }
            />
            <div className="toolbar">
                <Segmented
                    label="Show"
                    value={status}
                    onChange={(v) => {
                        setStatus(v)
                        setPage(0)
                    }}
                    options={[
                        { value: 'ACTIVE', label: 'Active' },
                        { value: 'COMPLETED', label: 'Paid off' },
                        { value: '', label: 'All' },
                    ]}
                />
            </div>
            <ErrorNote error={error} onRetry={reload} />
            {loading && !data && <Loading />}
            {data && data.items.length === 0 && (
                <div className="list">
                    <Empty title="No loans here">Loans you create appear in this list.</Empty>
                </div>
            )}
            {data && data.items.length > 0 && (
                <>
                    <LoanList loans={data.items} />
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