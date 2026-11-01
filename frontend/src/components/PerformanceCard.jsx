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

const METHOD_LABEL = { CASH: 'Cash', MPESA_MANUAL: 'M-Pesa', BANK: 'Bank transfer', MANUAL: 'Other' }

const OUTCOMES = [
    ['paidOff', 'Paid off', 'pc-paid'],
    ['rolledOver', 'Rolled over', 'pc-rolled'],
    ['onTrack', 'On track', 'pc-ontrack'],
    ['overdue', 'Overdue', 'pc-overdue'],
]

const R = 48
const C = 2 * Math.PI * R

/** A ring split by what happened to the loans. The numbers come from the server; this only draws them. */
function Donut({ outcomes, total }) {
    const arcs = []
    let offset = 0
    for (const [key, , cls] of OUTCOMES) {
        const n = outcomes[key]
        if (!n) continue
        const len = (n / total) * C
        arcs.push({ key, cls, len, offset })
        offset += len
    }
    return (
        <svg className="pc-donut" viewBox="0 0 120 120" role="img" aria-label={OUTCOMES.map(([key, name]) => `${name} ${outcomes[key]}`).join(', ')}>
            <circle cx="60" cy="60" r={R} className="pc-ring" />
            {arcs.map((a) => (
                <circle
                    key={a.key}
                    cx="60"
                    cy="60"
                    r={R}
                    className={`pc-arc ${a.cls}`}
                    strokeDasharray={`${a.len} ${C - a.len}`}
                    strokeDashoffset={-a.offset}
                    transform="rotate(-90 60 60)"
                />
            ))}
            <text x="60" y="58" textAnchor="middle" className="pc-total">
                {total}
            </text>
            <text x="60" y="74" textAnchor="middle" className="pc-sub">
                loans
            </text>
        </svg>
    )
}

/** How the business did over any range of months. Every amount and count comes from the server. */
export default function PerformanceCard({ now }) {
    const thisMonth = ym(now)
    const [fromMonth, setFromMonth] = useState(() => ym(new Date(now.getFullYear(), now.getMonth() - 2, 1)))
    const [toMonth, setToMonth] = useState(thisMonth)
    const [productId, setProductId] = useState('')
    const [method, setMethod] = useState('')
    const products = useFetch('/api/v1/products')
    const valid = Boolean(fromMonth && toMonth && fromMonth <= toMonth)

    const query = new URLSearchParams({ from: `${fromMonth}-01`, to: `${toMonth}-01` })
    if (productId) query.set('productId', productId)
    if (method) query.set('method', method)
    const { data, error, reload } = useFetch(valid ? `/api/v1/dashboard/performance?${query}` : null)

    function lastMonths(count) {
        setToMonth(thisMonth)
        setFromMonth(ym(new Date(now.getFullYear(), now.getMonth() - (count - 1), 1)))
    }

    const o = data?.outcomes
    const total = o ? o.paidOff + o.rolledOver + o.onTrack + o.overdue : 0
    const methods = data?.byMethod || []
    const biggest = methods.reduce((max, m) => Math.max(max, Number(m.total)), 0)

    return (
        <section className="card pc" aria-label="Performance">
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
            <div className="pair">
                {products.data && products.data.length > 1 && (
                    <Field label="Product">
                        <select value={productId} onChange={(e) => setProductId(e.target.value)}>
                            <option value="">All products</option>
                            {products.data.map((p) => (
                                <option key={p.id} value={p.id}>
                                    {p.name}
                                </option>
                            ))}
                        </select>
                    </Field>
                )}
                <Field label="Payment method" hint="Changes money received only.">
                    <select value={method} onChange={(e) => setMethod(e.target.value)}>
                        <option value="">All methods</option>
                        {Object.entries(METHOD_LABEL).map(([value, name]) => (
                            <option key={value} value={value}>
                                {name}
                            </option>
                        ))}
                    </select>
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

                    <h3 className="section-title" style={{ marginTop: 20 }}>Received by payment method</h3>
                    {methods.length > 0 ? (
                        <ul className="pc-bars">
                            {methods.map((m) => (
                                <li key={m.method}>
                                    <span>{METHOD_LABEL[m.method] || m.method}</span>
                                    <span className="pc-lane">
                                        <span className="pc-fill" style={{ width: `${biggest > 0 ? Math.max(2, (Number(m.total) / biggest) * 100) : 0}%` }} />
                                    </span>
                                    <span className="money">{formatMoney(m.total)}</span>
                                </li>
                            ))}
                        </ul>
                    ) : (
                        <p className="muted">No payments were received in this period.</p>
                    )}

                    <h3 className="section-title" style={{ marginTop: 20 }}>What happened to these loans</h3>
                    {total > 0 ? (
                        <div className="pc-wrap">
                            <Donut outcomes={o} total={total} />
                            <ul className="pc-legend">
                                {OUTCOMES.map(([key, name, cls]) => (
                                    <li key={key}>
                                        <i className={`pc-sw ${cls}`} /> {name} <strong>{o[key]}</strong>
                                    </li>
                                ))}
                            </ul>
                        </div>
                    ) : (
                        <p className="muted">No loans were issued in this period.</p>
                    )}
                </>
            )}
        </section>
    )
}