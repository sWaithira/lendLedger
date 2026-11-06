import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth.jsx'
import MonthlyChart from '../components/MonthlyChart.jsx'
import { Button, Empty, ErrorNote, Loading, PageHead } from '../components/ui.jsx'
import { formatDate, formatMoney, loanNumber } from '../format.js'
import { useFetch } from '../hooks.js'

const iso = (d) => d.toLocaleDateString('en-CA')
const monthLong = new Intl.DateTimeFormat('en-GB', { month: 'long', year: 'numeric' })

/** First day of a month ('YYYY-MM-DD') moved by delta months. */
function shiftMonth(monthIso, delta) {
    const [y, m] = monthIso.split('-').map(Number)
    return iso(new Date(y, m - 1 + delta, 1))
}

/** The dates a month covers. The current month ends today. */
function monthRange(monthIso, now) {
    const [y, m] = monthIso.split('-').map(Number)
    const last = new Date(y, m, 0)
    return { from: monthIso, to: iso(last > now ? now : last) }
}

export default function Dashboard() {
    const { user, can } = useAuth()
    const [now] = useState(() => new Date())
    const thisMonth = iso(new Date(now.getFullYear(), now.getMonth(), 1))
    const [month, setMonth] = useState(thisMonth)
    const [today] = useState(() =>
        new Intl.DateTimeFormat('en-GB', { weekday: 'long', day: 'numeric', month: 'long' }).format(new Date()),
    )
    const { from, to } = monthRange(month, now)
    const [y, m] = month.split('-').map(Number)
    const monthLabel = monthLong.format(new Date(y, m - 1, 1))
    const isCurrent = month === thisMonth

    const summary = useFetch(`/api/v1/dashboard/summary?from=${from}&to=${to}`)
    const monthly = useFetch(can('REPORT_VIEW') ? `/api/v1/dashboard/monthly?months=6&end=${to}` : null)
    const overdue = useFetch('/api/v1/overdue?size=3')

    const s = summary.data
    const p = s?.portfolio

    return (
        <>
            <PageHead title="Overview" subtitle={`${today} · Welcome back, ${user.name.split(' ')[0]}`} />
            <div className="month-nav" role="group" aria-label="Month shown">
                <Button variant="secondary" aria-label="Previous month" onClick={() => setMonth(shiftMonth(month, -1))}>
                    ‹
                </Button>
                <strong aria-live="polite">{monthLabel}</strong>
                <Button variant="secondary" aria-label="Next month" disabled={isCurrent} onClick={() => setMonth(shiftMonth(month, 1))}>
                    ›
                </Button>
            </div>
            <ErrorNote error={summary.error} onRetry={summary.reload} />
            {summary.loading && !s && <Loading rows={3} />}

            {s && (
                <>
                    <section className="card hero-card" aria-label="Portfolio">
                        <small className="muted">Owed to you</small>
                        <div className="hero money">{formatMoney(p.outstanding.total)}</div>
                        <p className="muted">
                            across {p.activeLoans} active loan{p.activeLoans === 1 ? '' : 's'}
                        </p>
                        <div className="stats">
                            <div className="stat">
                                <small>Principal</small>
                                <strong className="money">{formatMoney(p.outstanding.principal)}</strong>
                            </div>
                            <div className="stat">
                                <small>Interest</small>
                                <strong className="money">{formatMoney(p.outstanding.interest)}</strong>
                            </div>
                            {p.outstanding.penalty > 0 && (
                                <div className="stat">
                                    <small>Late penalty</small>
                                    <strong className="money">{formatMoney(p.outstanding.penalty)}</strong>
                                </div>
                            )}
                        </div>
                    </section>

                    <div className="tiles">
                        <Link to="/overdue" className={`tile ${p.overdueLoans > 0 ? 'alert' : ''}`}>
                            <small>Overdue</small>
                            <strong className="money">{formatMoney(p.overdueOutstanding.total)}</strong>
                            <span>
                                {p.overdueLoans === 0 ? 'Nothing overdue' : `${p.overdueLoans} loan${p.overdueLoans === 1 ? '' : 's'} · see who to call`}
                            </span>
                        </Link>
                        <div className="tile">
                            <small>Due within 7 days</small>
                            <strong className="money">{p.dueSoonLoans}</strong>
                            <span>loan{p.dueSoonLoans === 1 ? '' : 's'}</span>
                        </div>
                        <div className="tile">
                            <small>Loans issued</small>
                            <strong className="money">{s.disbursements.loans}</strong>
                        </div>
                        <div className="tile">
                            <small>Principal disbursed</small>
                            <strong className="money">{formatMoney(s.disbursements.principal)}</strong>
                        </div>
                        <div className="tile">
                            <small>Repayments received</small>
                            <strong className="money">{formatMoney(s.collections.total)}</strong>
                        </div>
                    </div>
                </>
            )}

            {can('REPORT_VIEW') && (
                <section className="card" aria-label="Money lent and received by month">
                    <div className="chart-head">
                        <h2>Money lent vs money received</h2>
                    </div>
                    <ErrorNote error={monthly.error} onRetry={monthly.reload} />
                    {monthly.data && <MonthlyChart points={monthly.data} label={`Six months to ${monthLabel}`} />}
                </section>
            )}

            <h2 className="section-title">Needs attention</h2>
            {overdue.data && overdue.data.items.length === 0 && (
                <div className="list">
                    <Empty title="All caught up">No loans are overdue.</Empty>
                </div>
            )}
            {overdue.data && overdue.data.items.length > 0 && (
                <div className="list">
                    {overdue.data.items.map((o) => (
                        <Link key={o.loanId} to={`/loans/${o.loanId}`} className="row">
                            <div className="grow">
                                <div className="title">{o.borrowerName}</div>
                                <div className="sub">
                                    {loanNumber(o.loanNumber)} · {o.fixedFee ? `Month ${o.penaltyMonth + 1}` : `${o.daysOverdue} days overdue`} · due{' '}
                                    {formatDate(o.dueDate)}
                                </div>
                            </div>
                            <div className="money title">{formatMoney(o.outstanding.total)}</div>
                            <span className="chev" aria-hidden="true">
                                ›
                            </span>
                        </Link>
                    ))}
                    <Link to="/overdue" className="row more">
                        See everyone who is overdue
                    </Link>
                </div>
            )}

            {can('REPORT_VIEW') && (
                <>
                    <h2 className="section-title">Reports</h2>
                    <div className="card downloads">
                        <a className="btn secondary" href="/api/v1/exports/loans.csv" download>
                            All loans (CSV)
                        </a>
                        <a className="btn secondary" href={`/api/v1/exports/repayments.csv?from=${from}&to=${to}`} download>
                            Payments, {monthLabel} (CSV)
                        </a>
                        <a className="btn secondary" href="/api/v1/exports/overdue.csv" download>
                            Overdue (CSV)
                        </a>
                    </div>
                </>
            )}
        </>
    )
}