import { useState } from 'react'
import { formatMoney } from '../format.js'

const W = 640
const H = 260
const PAD = { top: 14, bottom: 30, left: 54, right: 8 }
const monthShort = new Intl.DateTimeFormat('en-GB', { month: 'short' })
const monthLong = new Intl.DateTimeFormat('en-GB', { month: 'long', year: 'numeric' })
const compact = new Intl.NumberFormat('en-KE', { notation: 'compact', maximumFractionDigits: 1 })

const parse = (iso) => {
    const [y, m, d] = iso.split('-').map(Number)
    return new Date(y, m - 1, d)
}

/** A round top value and step for the money axis. Drawing only; amounts come from the server. */
function scale(max) {
    const raw = max / 4 || 1
    const pow = 10 ** Math.floor(Math.log10(raw))
    const f = raw / pow
    const step = (f <= 1 ? 1 : f <= 2 ? 2 : f <= 5 ? 5 : 10) * pow
    return { step, top: Math.ceil(max / step) * step || step }
}

/**
 * Months along the bottom, money up the side. Two bars per month: Lent (blue) and Received (green).
 * The gap between them is what has not come back yet. A hidden table gives screen readers the same numbers.
 */
export default function MonthlyChart({ points, label }) {
    const [active, setActive] = useState(null)
    const innerW = W - PAD.left - PAD.right
    const innerH = H - PAD.top - PAD.bottom
    const base = PAD.top + innerH
    const max = Math.max(0, ...points.map((p) => Math.max(p.principalLent, p.collected)))
    const { step, top } = scale(max)
    const ticks = []
    for (let v = 0; v <= top; v += step) ticks.push(v)
    const barH = (v) => (Math.max(v, 0) / top) * innerH

    const slot = innerW / Math.max(points.length, 1)
    const bar = Math.min(slot * 0.32, 40)
    const group = bar * 2 + 4
    const shown = active != null ? points[active] : null

    return (
        <figure className="chart">
            <figcaption className="chart-readout" aria-live="polite">
                {shown ? (
                    <>
                        <span className="muted">{monthLong.format(parse(shown.monthStart))}</span>{' '}
                        <strong className="money">Lent {formatMoney(shown.principalLent)}</strong>{' · '}
                        <strong className="money">Received {formatMoney(shown.collected)}</strong>
                    </>
                ) : (
                    <span className="muted">{label}</span>
                )}
            </figcaption>
            <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label={label} className="chart-svg">
                {ticks.map((v) => {
                    const y = base - barH(v)
                    return (
                        <g key={v}>
                            <line x1={PAD.left} x2={W - PAD.right} y1={y} y2={y} className="mc-grid" />
                            <text x={PAD.left - 8} y={y + 4} textAnchor="end" className="mc-tick">
                                {compact.format(v)}
                            </text>
                        </g>
                    )
                })}
                {points.map((p, i) => {
                    const gx = PAD.left + i * slot + (slot - group) / 2
                    const series = [
                        ['mc-lent', p.principalLent],
                        ['mc-received', p.collected],
                    ]
                    return (
                        <g
                            key={p.monthStart}
                            tabIndex={0}
                            onMouseEnter={() => setActive(i)}
                            onMouseLeave={() => setActive(null)}
                            onFocus={() => setActive(i)}
                            onBlur={() => setActive(null)}
                            aria-label={`${monthLong.format(parse(p.monthStart))}: lent ${formatMoney(p.principalLent)}, received ${formatMoney(p.collected)}`}
                        >
                            <rect x={PAD.left + i * slot} y={PAD.top} width={slot} height={innerH} fill="transparent" />
                            {series.map(([cls, v], k) => {
                                const h = barH(v)
                                return h > 0 ? (
                                    <rect key={cls} x={gx + k * (bar + 4)} y={base - Math.max(h, 2)} width={bar} height={Math.max(h, 2)} rx="3" className={cls} />
                                ) : null
                            })}
                            <text x={gx + group / 2} y={H - 10} textAnchor="middle" className="chart-label">
                                {monthShort.format(parse(p.monthStart))}
                            </text>
                        </g>
                    )
                })}
            </svg>
            <div className="mc-legend" aria-hidden="true">
                <span className="mc-key"><i className="mc-lent" /> Money lent</span>
                <span className="mc-key"><i className="mc-received" /> Money received</span>
                <span>Amounts in KES</span>
            </div>
            <table className="sr-only">
                <caption>{label}</caption>
                <thead>
                <tr>
                    <th scope="col">Month</th>
                    <th scope="col">Loans issued</th>
                    <th scope="col">Lent</th>
                    <th scope="col">Received</th>
                </tr>
                </thead>
                <tbody>
                {points.map((p) => (
                    <tr key={p.monthStart}>
                        <th scope="row">{monthLong.format(parse(p.monthStart))}</th>
                        <td>{p.loansIssued}</td>
                        <td>{formatMoney(p.principalLent)}</td>
                        <td>{formatMoney(p.collected)}</td>
                    </tr>
                ))}
                </tbody>
            </table>
        </figure>
    )
}