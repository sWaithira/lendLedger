import { useState } from 'react'
import { formatMoney } from '../format.js'
import { useFetch } from '../hooks.js'
import { Button, ErrorNote, Field } from './ui.jsx'

const ym = (d) => d.toLocaleDateString('en-CA').slice(0, 7)
const monthName = new Intl.DateTimeFormat('en-GB', { month: 'short', year: 'numeric' })
const label = (v) => {
    const [y, m] = v.split('-').map(Number)
    return monthName.format(new Date(y, m - 1, 1))
}

const OUTCOMES = [
    ['paidOff', 'Paid off', 'po-paid'],
    ['rolledOver', 'Rolled over', 'po-rolled'],
    ['onTrack', 'On track', 'po-track'],
    ['overdue', 'Overdue', 'po-overdue'],
]

/** How the business did over any range of months. Every amount and count comes from the server. */
export default function PerformanceCard({ now }) {
    const thisMonth = ym(now)
    const [fromMonth, setFromMonth] = useState(() => ym(new Date(now.getFullYear(), now.getMonth() - 2, 1)))
    const [toMonth, setToMonth] = useState(thisMonth)
    const valid = Boolean(fromMonth && toMonth && fromMonth <= toMonth)
    const { data, error, reload } = useFetch(
        valid ? `/api/v1/dashboard/performance?from=${fromMonth}-01&to=${toMonth}-01` : null,
    )

    function lastMonths(count) {
        setToMonth(thisMonth)
        setFromMonth(ym(new Date(now.getFullYear(), now.getMonth() - (count - 1), 1)))
    }

    const o = data?.outcomes
    const total = o ? o.paidOff + o.rolledOver + o.onTrack + o.overdue : 0

    return (
        <section className="card" aria-label="Performance">
            <div className="chart-head">
                <h2>Performance</h2>
            </div>
            <div className="perf-presets">
                <Button variant="secondary" onClick={() => lastMonths(3)}>3 months</Button>
                <Button variant="secondary" onClick={() => lastMonths(6)}>6 months</Button>
                <Button variant="secondary" onClick={() => lastMonths(12)}>12 months</Button>
                <Button
                    variant="secondary"
                    onClick={() => {
                        setFromMonth(`${now.getFullYear()}-01`)
                        setToMonth(thisMonth)
                    }}
                >
                    This year
                </Button>
            </div>
            <div className="pair">
                <Field label="From">
                    <input type="month" max={thisMonth} value={fromMonth} onChange={(e) => setFromMonth(e.target.value)} />
                </Field>
                <Field label="To">
                    <input type="month" max={thisMonth} value={toMonth} onChange={(e) => setToMonth(e.target.value)} />
                </Field>
            </div>
            {!valid && <p className="muted">Choose a start month that is not after the end month.</p>}
            <ErrorNote error={error} onRetry={reload} />

            {valid && data && (
                <>
                    <p className="muted" style={{ marginTop: 0 }}>
                        {fromMonth === toMonth ? label(fromMonth) : `${label(fromMonth)} to ${label(toMonth)}`}
                    </p>
                    <div className="tiles">
                        <div className="tile">
                            <small>Money lent</small>
                            <strong className="money">{formatMoney(data.principalLent)}</strong>
                        </div>
                        <div className="tile">
                            <small>Money received</small>
                            <strong className="money">{formatMoney(data.received)}</strong>
                        </div>
                        <div className="tile">
                            <small>Interest earned</small>
                            <strong className="money">{formatMoney(data.interestEarned)}</strong>
                        </div>
                        <div className="tile">
                            <small>Loans issued</small>
                            <strong className="money">{data.loansIssued}</strong>
                        </div>
                    </div>
                    <h3 className="section-title" style={{ marginTop: 20 }}>What happened to these loans</h3>
                    {total > 0 ? (
                        <>
                            <div
                                className="po-bar"
                                role="img"
                                aria-label={OUTCOMES.map(([key, name]) => `${name} ${o[key]}`).join(', ')}
                            >
                                {OUTCOMES.map(([key, , cls]) =>
                                    o[key] > 0 ? <span key={key} className={`po-seg ${cls}`} style={{ flexGrow: o[key] }} /> : null,
                                )}
                            </div>
                            <ul className="po-legend">
                                {OUTCOMES.map(([key, name, cls]) => (
                                    <li key={key}>
                                        <i className={cls} /> {name} <strong>{o[key]}</strong>
                                    </li>
                                ))}
                            </ul>
                        </>
                    ) : (
                        <p className="muted">No loans were issued in this period.</p>
                    )}
                </>
            )}
        </section>
    )
}