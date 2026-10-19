import { useState } from 'react'
import { formatMoney } from '../format.js'

const W = 640
const H = 220
const PAD = { top: 12, bottom: 28, side: 6 }

/**
 * A calm bar chart: one colour, no gridlines, the value shown on hover or focus, and a hidden table so a
 * screen reader gets the same numbers. Negative bars (a reversal in a later month) hang below the zero line.
 * The scaling here is only for drawing; every amount comes from the server.
 */
export default function BarChart({ points, label }) {
    const [active, setActive] = useState(null)
    const values = points.map((p) => p.value)
    const max = Math.max(0, ...values)
    const min = Math.min(0, ...values)
    const span = max - min || 1
    const innerH = H - PAD.top - PAD.bottom
    const y = (v) => PAD.top + ((max - v) / span) * innerH
    const zero = y(0)
    const slot = (W - PAD.side * 2) / Math.max(points.length, 1)
    const barW = Math.min(slot * 0.62, 56)
    const every = Math.ceil(points.length / 8)
    const shown = active != null ? points[active] : null

    return (
        <figure className="chart">
            <figcaption className="chart-readout" aria-live="polite">
                {shown ? (
                    <>
                        <span className="muted">{shown.long}</span> <strong className="money">{formatMoney(shown.value)}</strong>
                    </>
                ) : (
                    <span className="muted">{label}</span>
                )}
            </figcaption>
            <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label={label} className="chart-svg">
                <line x1="0" x2={W} y1={zero} y2={zero} className="chart-zero" />
                {points.map((p, i) => {
                    const x = PAD.side + i * slot + (slot - barW) / 2
                    const top = Math.min(y(p.value), zero)
                    const h = Math.max(Math.abs(y(p.value) - zero), p.value === 0 ? 0 : 2)
                    return (
                        <g
                            key={p.key}
                            tabIndex={0}
                            onMouseEnter={() => setActive(i)}
                            onMouseLeave={() => setActive(null)}
                            onFocus={() => setActive(i)}
                            onBlur={() => setActive(null)}
                            aria-label={`${p.long}: ${formatMoney(p.value)}`}
                        >
                            <rect x={PAD.side + i * slot} y={PAD.top} width={slot} height={innerH} fill="transparent" />
                            <rect x={x} y={top} width={barW} height={h} rx="6" className={p.value < 0 ? 'bar neg' : active === i ? 'bar on' : 'bar'} />
                            {i % every === 0 && (
                                <text x={x + barW / 2} y={H - 8} textAnchor="middle" className="chart-label">
                                    {p.short}
                                </text>
                            )}
                        </g>
                    )
                })}
            </svg>
            <table className="sr-only">
                <caption>{label}</caption>
                <tbody>
                {points.map((p) => (
                    <tr key={p.key}>
                        <th scope="row">{p.long}</th>
                        <td>{formatMoney(p.value)}</td>
                    </tr>
                ))}
                </tbody>
            </table>
        </figure>
    )
}