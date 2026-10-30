import { useState } from 'react'
import { useAuth } from '../auth.jsx'
import PerformanceCard from '../components/PerformanceCard.jsx'
import { Empty, PageHead } from '../components/ui.jsx'

export default function Performance() {
    const { can } = useAuth()
    const [now] = useState(() => new Date())

    if (!can('REPORT_VIEW')) {
        return (
            <>
                <PageHead title="Performance" subtitle="How the business did over any months you choose." />
                <div className="list">
                    <Empty title="Not available">You don’t have access to reports.</Empty>
                </div>
            </>
        )
    }
    return (
        <>
            <PageHead title="Performance" subtitle="How the business did over any months you choose." />
            <PerformanceCard now={now} />
        </>
    )
}