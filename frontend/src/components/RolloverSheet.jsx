import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, describeError } from '../api.js'
import { formatMoney, todayIso } from '../format.js'
import { Button, Field, Sheet } from './ui.jsx'

/** Rolls the remaining principal into a new loan. Interest and penalties must already be paid on the chosen date. */
export default function RolloverSheet({ loan, defaultDate, onClose }) {
    const navigate = useNavigate()
    const [date, setDate] = useState(defaultDate || todayIso())
    const [term, setTerm] = useState(String(loan.termPeriods))
    const [error, setError] = useState('')
    const [busy, setBusy] = useState(false)

    async function submit(e) {
        e.preventDefault()
        setBusy(true)
        setError('')
        try {
            const created = await api.post(`/api/v1/loans/${loan.id}/rollover`, {
                rolloverDate: date,
                termPeriods: Number(term),
            })
            onClose()
            navigate(`/loans/${created.id}`)
        } catch (err) {
            setError(describeError(err))
            setBusy(false)
        }
    }

    return (
        <Sheet
            title="Roll over the principal"
            onClose={onClose}
            footer={
                <>
                    <Button variant="secondary" type="button" onClick={onClose}>
                        Cancel
                    </Button>
                    <Button type="submit" form="roll-over" busy={busy}>
                        Roll over
                    </Button>
                </>
            }
        >
            <form id="roll-over" onSubmit={submit}>
                {error && (
                    <div className="notice error" role="alert">
                        {error}
                    </div>
                )}
                <div className="notice info">
                    The remaining principal of <strong>{formatMoney(loan.outstanding.principal)}</strong> becomes a new loan for{' '}
                    {loan.borrowerName}, under your current rules. This loan is closed. Interest and penalties must already be paid on the
                    date below. This cannot be undone.
                </div>
                <Field label="Rollover date" hint="Usually the day the interest was paid.">
                    <input type="date" max={todayIso()} value={date} onChange={(e) => setDate(e.target.value)} required />
                </Field>
                <Field label="Term of the new loan (months)">
                    <input type="number" min="1" max="60" step="1" value={term} onChange={(e) => setTerm(e.target.value)} required />
                </Field>
            </form>
        </Sheet>
    )
}