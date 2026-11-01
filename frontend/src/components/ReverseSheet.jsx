import { useState } from 'react'
import { api, describeError } from '../api.js'
import { formatDate, formatMoney } from '../format.js'
import { Button, Field, Sheet } from './ui.jsx'

/** Reversing never deletes: it adds a correction and keeps the original payment on the record. */
export default function ReverseSheet({ payment, onClose, onDone }) {
    const [reason, setReason] = useState('')
    const [error, setError] = useState('')
    const [busy, setBusy] = useState(false)

    async function submit(e) {
        e.preventDefault()
        setBusy(true)
        setError('')
        try {
            await api.post(`/api/v1/repayments/${payment.id}/reverse`, { reason })
            onDone('Payment undone. The original stays on the record.')
        } catch (err) {
            setError(describeError(err))
            setBusy(false)
        }
    }

    return (
        <Sheet
            title="Undo this payment?"
            onClose={onClose}
            footer={
                <>
                    <Button variant="secondary" type="button" onClick={onClose}>
                        Keep payment
                    </Button>
                    <Button variant="danger" type="submit" form="reverse-payment" busy={busy} disabled={reason.trim().length < 3}>
                        Undo payment
                    </Button>
                </>
            }
        >
            <form id="reverse-payment" onSubmit={submit}>
                <p style={{ marginBottom: 18 }}>
                    {formatMoney(payment.amount)} received on {formatDate(payment.paymentDate)} will be taken off the loan and the balance
                    will go back up. Only the most recent payment can be reversed.
                </p>
                {error && (
                    <div className="notice error" role="alert">
                        {error}
                    </div>
                )}
                <Field label="Reason" hint="Recorded in the audit trail.">
                    <textarea value={reason} onChange={(e) => setReason(e.target.value)} minLength={3} maxLength={500} required />
                </Field>
            </form>
        </Sheet>
    )
}