import { useState } from 'react'
import { useAuth } from '../auth.jsx'
import { Empty, PageHead } from '../components/ui.jsx'

const iso = (d) => d.toLocaleDateString('en-CA')
const ym = (d) => iso(d).slice(0, 7)

/** The dates a month covers. The current month ends today. */
function monthRange(month, now) {
    const [y, m] = month.split('-').map(Number)
    const last = new Date(y, m, 0)
    return { from: `${month}-01`, to: iso(last > now ? now : last) }
}

export default function Reports() {
    const { can } = useAuth()
    const [now] = useState(() => new Date())
    const [month, setMonth] = useState(() => ym(now))

    if (!can('REPORT_VIEW')) {
        return (
            <>
                <PageHead title="Reports" subtitle="Download your data as CSV files." />
                <div className="list">
                    <Empty title="Not available">You don’t have access to reports.</Empty>
                </div>
            </>
        )
    }

    const range = month ? monthRange(month, now) : null

    return (
        <>
            <PageHead title="Reports" subtitle="Download your data as CSV files." />
            <div className="list">
                <div className="row static">
                    <div className="grow">
                        <div className="title">All loans</div>
                        <div className="sub">Every loan with what is still owed today</div>
                    </div>
                    <a className="btn secondary" href="/api/v1/exports/loans.csv" download>
                        Download
                    </a>
                </div>
                <div className="row static">
                    <div className="grow">
                        <div className="title">Overdue loans</div>
                        <div className="sub">Loans past their due date, with phone numbers</div>
                    </div>
                    <a className="btn secondary" href="/api/v1/exports/overdue.csv" download>
                        Download
                    </a>
                </div>
                <div className="row static">
                    <div className="grow">
                        <div className="title">Payments</div>
                        <div className="sub">Money received in the month you choose</div>
                        <input className="month-pick" type="month" aria-label="Month" max={ym(now)} value={month} onChange={(e) => setMonth(e.target.value)} />
                    </div>
                    {range && (
                        <a className="btn secondary" href={`/api/v1/exports/repayments.csv?from=${range.from}&to=${range.to}`} download>
                            Download
                        </a>
                    )}
                </div>
            </div>
        </>
    )
}