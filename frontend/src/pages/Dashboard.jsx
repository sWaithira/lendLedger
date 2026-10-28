import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth.jsx'
import BarChart from '../components/BarChart.jsx'
import MonthlyChart from '../components/MonthlyChart.jsx'
import { Empty, ErrorNote, Loading, PageHead, Segmented } from '../components/ui.jsx'
import { formatDate, formatMoney, loanNumber } from '../format.js'
import { useFetch } from '../hooks.js'

const iso = (d) => d.toLocaleDateString('en-CA')

function rangeFor(range) {
    const now = new Date()
    const to = iso(now)
    if (range === 'month') return { from: iso(new Date(now.getFullYear(), now.getMonth(), 1)), to, granularity: 'day' }
    if (range === 'year') return { from: iso(new Date(now.getFullYear(), 0, 1)), to, granularity: 'month' }
    return { from: iso(new Date(now.getFullYear(), now.getMonth() - 5, 1)), to, granularity: 'month' }
}

const monthShort = new Intl.DateTimeFormat('en-GB', { month: 'short' })
const monthLong = new Intl.DateTimeFormat('en-GB', { month: 'long', year: 'numeric' })
const dayLong = new Intl.DateTimeFormat('en-GB', { weekday: 'short', day: 'numeric', month: 'short' })

function toPoints(series, granularity) {
    return series.map((p) => {
        const [y, m, d] = p.periodStart.split('-').map(Number)
        const date = new Date(y, m - 1, d)
        return granularity === 'day'
            ? { key: p.periodStart, short: String(d), long: dayLong.format(date), value: p.total }
            : { key: p.periodStart, short: monthShort.format(date), long: monthLong.format(date), value: p.total }
    })
}

export default function Dashboard() {
    const { user, can } = useAuth()
    const [range, setRange] = useState('6m')
    const [months, setMonths] = useState(6)
    const [today] = useState(() =>
        new Intl.DateTimeFormat('en-GB', { weekday: 'long', day: 'numeric', month: 'long' }).format(new Date()),
    )
    const { from, to, granularity } = rangeFor(range)

    const summary = useFetch(`/api/v1/dashboard/summary?from=${from}&to=${to}`)
    const series = useFetch(`/api/v1/dashboard/collections?from=${from}&to=${to}&granularity=${granularity}`)
    const monthly = useFetch(can('REPORT_VIEW') ? `/api/v1/dashboard/monthly?months=${months}` : null)
    const overdue = useFetch('/api/v1/overdue?size=3')

    const s = summary.data
    const p = s?.portfolio
    const period = { month: 'This month', '6m': 'Last 6 months', year: 'This year' }[range]

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
                            across {p.activeLoans} active loan{p.activeLoans === 1 ? '' : 's'} · {p.completedLoans} paid off
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
                            <span>{period.toLowerCase()}</span>
                        </div>
                        <div className="tile">
                            <small>Principal disbursed</small>
                            <strong className="money">{formatMoney(s.disbursements.principal)}</strong>
                            <span>{period.toLowerCase()}</span>
                        </div>
                        <div className="tile">
                            <small>Repayments received</small>
                            <strong className="money">{formatMoney(s.collections.total)}</strong>
                            <span>
                                {formatMoney(s.collections.interest)} interest · {formatMoney(s.collections.principal)} principal
                            </span>
                        </div>
                    </div>
                </>
            )}

            {can('REPORT_VIEW') && (
                <section className="card" aria-label="Lent and collected by month">
                    <div className="chart-head">
                        <h2>Lent and collected, by month</h2>
                        <Segmented
                            label="Months shown"
                            value={months}
                            onChange={setMonths}
                            options={[
                                { value: 6, label: '6 months' },
                                { value: 12, label: '12 months' },
                            ]}
                        />
                    </div>
                    <ErrorNote error={monthly.error} onRetry={monthly.reload} />
                    {monthly.data && <MonthlyChart points={monthly.data} label={`Lent and collected, last ${months} months`} />}
                    <p className="muted" style={{ fontSize: '0.8125rem' }}>
                        Lent is the principal given out that month. Collected is split into principal, interest and penalty. A reversed payment is taken off in the month the reversal was recorded.
                    </p>
                </section>
            )}

            <section className="card" aria-label="Collections">
                <div className="chart-head">
                    <h2>Collections</h2>
                    <Segmented
                        label="Period"
                        value={range}
                        onChange={setRange}
                        options={[
                            { value: 'month', label: 'Month' },
                            { value: '6m', label: '6 months' },
                            { value: 'year', label: 'Year' },
                        ]}
                    />
                </div>
                <ErrorNote error={series.error} onRetry={series.reload} />
                {series.data && <BarChart points={toPoints(series.data, granularity)} label={`Money collected, ${period.toLowerCase()}`} />}
                <p className="muted" style={{ fontSize: '0.8125rem' }}>
                    A reversed payment is taken off in the month the reversal was recorded.
                </p>
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
                            Payments, {period.toLowerCase()} (CSV)
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