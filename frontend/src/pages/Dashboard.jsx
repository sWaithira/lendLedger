import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth.jsx'
import { useFetch } from '../hooks.js'

const iso = (d) => d.toLocaleDateString('en-CA')
const monthLong = new Intl.DateTimeFormat('en-GB', { month: 'long', year: 'numeric' })

}

export default function Dashboard() {
    const { user, can } = useAuth()
    const [today] = useState(() =>
        new Intl.DateTimeFormat('en-GB', { weekday: 'long', day: 'numeric', month: 'long' }).format(new Date()),
    )

    const summary = useFetch(`/api/v1/dashboard/summary?from=${from}&to=${to}`)
    const overdue = useFetch('/api/v1/overdue?size=3')

    const s = summary.data
    const p = s?.portfolio

    return (
        <>
            <PageHead title="Overview" subtitle={`${today} · Welcome back, ${user.name.split(' ')[0]}`} />
            <ErrorNote error={summary.error} onRetry={summary.reload} />
            {summary.loading && !s && <Loading rows={3} />}

            {s && (
                <>
                    <section className="card hero-card" aria-label="Portfolio">
                        <small className="muted">Owed to you</small>
                        <div className="hero money">{formatMoney(p.outstanding.total)}</div>
                        <p className="muted">
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
                                <div className="stat">
                                    <strong className="money">{formatMoney(p.outstanding.penalty)}</strong>
                                </div>
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
                        </div>
                        <div className="tile">
                            <strong className="money">{formatMoney(s.disbursements.principal)}</strong>
                        </div>
                    </div>
                </>
            )}

                    <div className="chart-head">
                    </div>
                </section>

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