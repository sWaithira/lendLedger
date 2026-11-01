import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useAuth } from '../auth.jsx'
import PaymentSheet from '../components/PaymentSheet.jsx'
import ReverseSheet from '../components/ReverseSheet.jsx'
import RolloverSheet from '../components/RolloverSheet.jsx'
import { Badge, Button, Empty, ErrorNote, Loading, PageHead, StatusBadge } from '../components/ui.jsx'
import { formatDate, formatMoney, loanNumber, percent } from '../format.js'
import { useFetch } from '../hooks.js'

const COMPONENT_LABEL = { PENALTY: 'Penalty', INTEREST: 'Interest', PRINCIPAL: 'Principal' }
const METHOD_LABEL = { CASH: 'Cash', MPESA_MANUAL: 'M-Pesa', BANK: 'Bank transfer', MANUAL: 'Other', ROLLOVER: 'Rolled over' }

export default function LoanDetail() {
    const { id } = useParams()
    const { can } = useAuth()
    const loan = useFetch(`/api/v1/loans/${id}`)
    const payments = useFetch(`/api/v1/loans/${id}/repayments`)
    const statement = useFetch(`/api/v1/loans/${id}/statement`)
    const [paying, setPaying] = useState(false)
    const [reversing, setReversing] = useState(null)
    const [rolling, setRolling] = useState(false)
    const links = useFetch(`/api/v1/loans/${id}/rollover-links`)
    const [notice, setNotice] = useState('')

    function done(message) {
        setPaying(false)
        setReversing(null)
        setNotice(message)
        loan.reload()
        payments.reload()
        statement.reload()
    }

    const l = loan.data
    const items = payments.data || []
    // Only the latest payment that still stands can be reversed (the server enforces this too).
    const reversibleId = [...items].reverse().find((p) => !p.reversed)?.id
    // Rolling over needs at least one standing cash payment (the server checks the interest is fully paid).
    const lastStanding = [...items].reverse().find((p) => !p.reversed && p.method !== 'ROLLOVER')

    return (
        <>
            <Link className="back" to={l ? `/borrowers/${l.borrowerId}` : '/loans'}>
                ‹ {l ? l.borrowerName : 'Loans'}
            </Link>
            <ErrorNote error={loan.error} onRetry={loan.reload} />
            {loan.loading && !l && <Loading rows={3} />}
            {links.data?.rolledFrom && (
                <div className="notice info" role="status">
                    Rolled over from <Link to={`/loans/${links.data.rolledFrom.id}`}>{loanNumber(links.data.rolledFrom.loanNumber)}</Link>
                </div>
            )}
            {links.data?.rolledInto && (
                <div className="notice info" role="status">
                    The principal was rolled over into <Link to={`/loans/${links.data.rolledInto.id}`}>{loanNumber(links.data.rolledInto.loanNumber)}</Link>
                </div>
            )}
            {notice && (
                <div className="notice info" role="status">
                    {notice}
                </div>
            )}

            {l && (
                <>
                    <PageHead
                        title={`${formatMoney(l.principal)} loan`}
                        subtitle={`${loanNumber(l.loanNumber)} · ${l.borrowerName} · ${l.productName} · Issued ${formatDate(l.issueDate)}`}
                        actions={
                            <span className="head-actions">
                <StatusBadge status={l.displayStatus} />
                                {can('REPAYMENT_POST') && l.status === 'ACTIVE' && <Button onClick={() => setPaying(true)}>Record payment</Button>}
                                {can('LOAN_CREATE') && l.status === 'ACTIVE' && l.outstanding.principal > 0 && lastStanding && (
                                    <Button variant="secondary" onClick={() => setRolling(true)}>
                                        Roll over principal
                                    </Button>
                                )}
              </span>
                        }
                    />

                    <div className="card">
                        <small className="muted">{l.status === 'COMPLETED' ? 'Paid in full' : 'Still owed'}</small>
                        <div className="hero money">{formatMoney(l.outstanding.total)}</div>
                        <div className="stats">
                            <div className="stat">
                                <small>Principal</small>
                                <strong className="money">{formatMoney(l.outstanding.principal)}</strong>
                            </div>
                            <div className="stat">
                                <small>Interest</small>
                                <strong className="money">{formatMoney(l.outstanding.interest)}</strong>
                            </div>
                            {l.outstanding.penalty > 0 && (
                                <div className="stat">
                                    <small>Late penalty</small>
                                    <strong className="money">{formatMoney(l.outstanding.penalty)}</strong>
                                </div>
                            )}
                        </div>
                    </div>

                    <div className="card">
                        <dl className="kv">
                            <dt>Due date</dt>
                            <dd>{formatDate(l.dueDate)}</dd>
                            <dt>Amount due by {formatDate(l.dueDate)}</dt>
                            <dd className="money">{formatMoney(l.amountDueByDueDate)}</dd>
                            <dt>Term</dt>
                            <dd>
                                {l.termPeriods} month{l.termPeriods === 1 ? '' : 's'}
                            </dd>
                            <dt>Interest</dt>
                            <dd>{percent(l.ratePerPeriod)}{l.interestOnce ? ', charged once' : ' per month'}</dd>
                            {l.daysOverdue > 0 && (
                                <>
                                    <dt>Overdue</dt>
                                    <dd>
                                        {l.penaltyMonth > 0
                                            ? `Month ${l.penaltyMonth + 1} · due ${formatDate(l.dueDate)}`
                                            : `${l.daysOverdue} days`}
                                    </dd>
                                </>
                            )}
                        </dl>
                    </div>

                    <h2 className="section-title">Payments</h2>
                    <ErrorNote error={payments.error} onRetry={payments.reload} />
                    {payments.loading && !payments.data && <Loading rows={2} />}
                    {payments.data && items.length === 0 && (
                        <div className="list">
                            <Empty title="No payments yet">Payments you record appear here.</Empty>
                        </div>
                    )}
                    {items.length > 0 && (
                        <div className="list">
                            {[...items].reverse().map((p) => (
                                <div key={p.id} className="row static">
                                    <div className="grow">
                                        <div className="title money">
                                            {formatMoney(p.amount)} {p.reversed && <Badge tone="gray">Undone</Badge>}
                                        </div>
                                        <div className="sub">
                                            {formatDate(p.paymentDate)} · {METHOD_LABEL[p.method] || p.method}
                                            {p.reference ? ` · ${p.reference}` : ''}
                                        </div>
                                        <div className="sub">
                                            {p.allocations.map((a) => `${COMPONENT_LABEL[a.component]} ${formatMoney(a.amount)}`).join(' · ')}
                                        </div>
                                        {p.reversed && <div className="sub">Undone: {p.reversalReason}</div>}
                                    </div>
                                    {can('REPAYMENT_REVERSE') && p.id === reversibleId && p.method !== 'ROLLOVER' && (
                                        <Button variant="danger" onClick={() => setReversing(p)}>
                                            Undo payment
                                        </Button>
                                    )}
                                </div>
                            ))}
                        </div>
                    )}

                    <h2 className="section-title">Statement</h2>
                    <ErrorNote error={statement.error} onRetry={statement.reload} />
                    {statement.loading && !statement.data && <Loading rows={3} />}
                    {statement.data && (
                        <div className="list stmt" role="table" aria-label="Loan statement">
                            <div className="stmt-row stmt-head" role="row">
                                <span role="columnheader">Date</span>
                                <span role="columnheader">What happened</span>
                                <span role="columnheader" className="right">
                  Amount
                </span>
                                <span role="columnheader" className="right">
                  Balance
                </span>
                            </div>
                            {statement.data.entries.map((e, i) => (
                                <div key={i} className="stmt-row" role="row">
                  <span role="cell" className="num">
                    {formatDate(e.date)}
                  </span>
                                    <span role="cell">{e.description}</span>
                                    <span role="cell" className={`right money ${e.amount < 0 ? 'credit' : ''}`}>
                    {formatMoney(e.amount)}
                  </span>
                                    <span role="cell" className="right money">
                    {formatMoney(e.balanceAfter)}
                  </span>
                                </div>
                            ))}
                        </div>
                    )}
                </>
            )}

            {paying && <PaymentSheet loan={l} onClose={() => setPaying(false)} onDone={done} />}
            {reversing && <ReverseSheet payment={reversing} onClose={() => setReversing(null)} onDone={done} />}
            {rolling && <RolloverSheet loan={l} defaultDate={lastStanding?.paymentDate} onClose={() => setRolling(false)} />}
        </>
    )
}