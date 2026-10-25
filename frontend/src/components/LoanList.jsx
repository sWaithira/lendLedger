import { Link } from 'react-router-dom'
import { formatDate, formatMoney, loanNumber } from '../format.js'
import { StatusBadge } from './ui.jsx'

export default function LoanList({ loans, showBorrower = true, view = 'list' }) {
    if (view === 'cards') {
        return (
            <div className="cards">
                {loans.map((l) => (
                    <Link key={l.id} to={`/loans/${l.id}`} className="card-item">
                        <div className="card-top">
                            <span className="title">{showBorrower ? l.borrowerName : `Loan of ${formatMoney(l.principal)}`}</span>
                            <StatusBadge status={l.displayStatus} />
                        </div>
                        <div className="card-amount money">{formatMoney(l.outstanding.total)}</div>
                        <div className="sub">still owed{showBorrower ? ` · lent ${formatMoney(l.principal)}` : ''}</div>
                        <div className="sub">
                            {loanNumber(l.loanNumber)} · Due {formatDate(l.dueDate)}
                            {l.daysOverdue > 0 ? ` · ${l.daysOverdue} days overdue` : ''}
                        </div>
                    </Link>
                ))}
            </div>
        )
    }
    return (
        <div className="list">
            {loans.map((l) => (
                <Link key={l.id} to={`/loans/${l.id}`} className="row">
                    <div className="grow">
                        <div className="title">{showBorrower ? l.borrowerName : formatMoney(l.principal)}</div>
                        <div className="sub">
                            {loanNumber(l.loanNumber)} · {showBorrower ? `${formatMoney(l.principal)} · ` : ''}
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