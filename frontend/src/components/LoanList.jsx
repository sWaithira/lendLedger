import { Link } from 'react-router-dom'
import { formatDate, formatMoney } from '../format.js'
import { StatusBadge } from './ui.jsx'

/** Loans as an inset list: who, how much is still owed, when it falls due, and how it is doing. */
export default function LoanList({ loans, showBorrower = true }) {
    return (
        <div className="list">
            {loans.map((l) => (
                <Link key={l.id} to={`/loans/${l.id}`} className="row">
                    <div className="grow">
                        <div className="title">{showBorrower ? l.borrowerName : formatMoney(l.principal)}</div>
                        <div className="sub">
                            {showBorrower ? `${formatMoney(l.principal)} · ` : ''}
                            Due {formatDate(l.dueDate)}
                            {l.daysOverdue > 0 ? ` · ${l.daysOverdue} days overdue` : ''}
                        </div>
                    </div>
                    <div className="right">
                        <div className="money title">{formatMoney(l.outstanding.total)}</div>
                        <StatusBadge status={l.displayStatus} />
                    </div>
                    <span className="chev" aria-hidden="true">
            ›
          </span>
                </Link>
            ))}
        </div>
    )
}