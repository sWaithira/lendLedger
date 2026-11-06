import { Link, useParams } from 'react-router-dom'
import { ErrorNote, Loading, PageHead, StatusBadge } from '../components/ui.jsx'
import { formatDate, formatMoney, percent } from '../format.js'
import { useFetch } from '../hooks.js'

export default function LoanDetail() {
    const { id } = useParams()
    const { data: loan, error, loading, reload } = useFetch(`/api/v1/loans/${id}`)

    return (
        <>
            <Link className="back" to={loan ? `/borrowers/${loan.borrowerId}` : '/loans'}>
                ‹ {loan ? loan.borrowerName : 'Loans'}
            </Link>
            <ErrorNote error={error} onRetry={reload} />
            {loading && !loan && <Loading rows={3} />}
            {loan && (
                <>
                    <PageHead title={formatMoney(loan.principal)} subtitle={`${loan.productName} · Issued ${formatDate(loan.issueDate)}`} actions={<StatusBadge status={loan.displayStatus} />} />
                    <div className="card">
                        <small className="muted">Still owed</small>
                        <div className="hero money">{formatMoney(loan.outstanding.total)}</div>
                        <div className="stats">
                            <div className="stat">
                                <small>Principal</small>
                                <strong className="money">{formatMoney(loan.outstanding.principal)}</strong>
                            </div>
                            <div className="stat">
                                <small>Interest</small>
                                <strong className="money">{formatMoney(loan.outstanding.interest)}</strong>
                            </div>
                            <div className="stat">
                                <small>Penalty</small>
                                <strong className="money">{formatMoney(loan.outstanding.penalty)}</strong>
                            </div>
                        </div>
                    </div>
                    <div className="card">
                        <dl className="kv">
                            <dt>Due date</dt>
                            <dd>{formatDate(loan.dueDate)}</dd>
                            <dt>Term</dt>
                            <dd>{loan.termPeriods} month{loan.termPeriods === 1 ? '' : 's'}</dd>
                            <dt>Interest</dt>
                            <dd>{percent(loan.ratePerPeriod)} per month</dd>
                            {loan.daysOverdue > 0 && (
                                <>
                                    <dt>Overdue</dt>
                                    <dd>{loan.daysOverdue} days</dd>
                                </>
                            )}
                        </dl>
                    </div>
                </>
            )}
        </>
    )
}