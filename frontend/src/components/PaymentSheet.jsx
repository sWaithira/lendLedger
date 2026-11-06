import { useEffect, useState } from 'react'
import { api, describeError } from '../api.js'
import { formatDate, formatMoney, todayIso } from '../format.js'
import { Button, Field, Sheet } from './ui.jsx'

const METHODS = [
    { value: 'CASH', label: 'Cash' },
    { value: 'MPESA_MANUAL', label: 'M-Pesa' },
    { value: 'BANK', label: 'Bank transfer' },
    { value: 'MANUAL', label: 'Other' },
]
const COMPONENT_LABEL = { PENALTY: 'Penalty', INTEREST: 'Interest', PRINCIPAL: 'Principal' }

export default function PaymentSheet({ loan, onClose, onDone }) {
    const [amount, setAmount] = useState('')
    const [paymentDate, setPaymentDate] = useState(todayIso())
    const [method, setMethod] = useState('CASH')
    const [reference, setReference] = useState('')
    const [idempotencyKey] = useState(() => crypto.randomUUID())
    const [result, setResult] = useState({ key: '', preview: null, error: '' })
    const [error, setError] = useState('')
    const [busy, setBusy] = useState(false)

    const ready = Number(amount) > 0 && Boolean(paymentDate)
    const inputKey = JSON.stringify([amount, paymentDate])
    const current = ready && result.key === inputKey ? result : { preview: null, error: '' }
    const { preview, error: previewError } = current

    useEffect(() => {
        if (!ready) return undefined
        let cancelled = false
        const timer = setTimeout(async () => {
            try {
                const p = await api.post(`/api/v1/loans/${loan.id}/repayments/preview`, {
                    amount: Number(amount),
                    paymentDate,
                })
                if (!cancelled) setResult({ key: inputKey, preview: p, error: '' })
            } catch (err) {
                const owed = err.body?.outstanding != null ? ` Owed on that date: ${formatMoney(err.body.outstanding)}.` : ''
                if (!cancelled) setResult({ key: inputKey, preview: null, error: describeError(err) + owed })
            }
        }, 350)
        return () => {
            cancelled = true
            clearTimeout(timer)
        }
    }, [ready, inputKey, loan.id, amount, paymentDate])

    async function submit(e) {
        e.preventDefault()
        setBusy(true)
        setError('')
        try {
            const res = await api.post(
                `/api/v1/loans/${loan.id}/repayments`,
                { amount: Number(amount), paymentDate, method, reference: reference.trim() || undefined },
                { 'Idempotency-Key': idempotencyKey },
            )
            onDone(res.replayed ? 'That payment had already been recorded.' : 'Payment recorded.')
        } catch (err) {
            setError(describeError(err))
            setBusy(false)
        }
    }

    return (
        <Sheet
            title="Record payment"
            onClose={onClose}
            footer={
                <>
                    <Button variant="secondary" type="button" onClick={onClose}>
                        Cancel
                    </Button>
                    <Button type="submit" form="record-payment" busy={busy} disabled={!preview}>
                        Record payment
                    </Button>
                </>
            }
        >
            <form id="record-payment" onSubmit={submit}>
                {error && (
                    <div className="notice error" role="alert">
                        {error}
                    </div>
                )}
                <Field label="Amount (KES)" hint={`Still owed today: ${formatMoney(loan.outstanding.total)}`}>
                    <input
                        type="number"
                        step="0.01"
                        min="0.01"
                        value={amount}
                        onChange={(e) => setAmount(e.target.value)}
                        required
                        autoFocus
                    />
                </Field>
                <div style={{ margin: '-10px 0 18px' }}>
                    <Button
                        variant="plain"
                        type="button"
                        onClick={() => {
                            setAmount(String(loan.outstanding.total))
                            setPaymentDate(todayIso())
                        }}
                    >
                        Pay the full balance
                    </Button>
                </div>
                <Field label="Date received">
                    <input type="date" max={todayIso()} min={loan.issueDate} value={paymentDate} onChange={(e) => setPaymentDate(e.target.value)} required />
                </Field>
                <Field label="How was it paid?">
                    <select value={method} onChange={(e) => setMethod(e.target.value)}>
                        {METHODS.map((m) => (
                            <option key={m.value} value={m.value}>
                                {m.label}
                            </option>
                        ))}
                    </select>
                </Field>
                <Field label="Reference (optional)" hint="e.g. M-Pesa code">
                    <input value={reference} onChange={(e) => setReference(e.target.value)} maxLength={100} autoComplete="off" />
                </Field>

                <div className="alloc" aria-live="polite">
                    <h3 className="section-title" style={{ margin: '0 0 8px' }}>
                        Where the money goes
                    </h3>
                    {previewError && (
                        <div className="notice error" role="alert">
                            {previewError}
                        </div>
                    )}
                    {!preview && !previewError && <p className="muted">Enter an amount to see how it will be applied.</p>}
                    {preview && (
                        <>
                            <dl className="kv">
                                {preview.allocations.map((a) => (
                                    <Line key={a.component} label={COMPONENT_LABEL[a.component]} value={formatMoney(a.amount)} />
                                ))}
                                <dt className="total">Still owed after this</dt>
                                <dd className="total money">{formatMoney(preview.after.total)}</dd>
                            </dl>
                            {preview.settlesLoan && (
                                <div className="notice info" style={{ marginTop: 14 }}>
                                    This payment clears the loan.
                                </div>
                            )}
                            <p className="muted" style={{ marginTop: 10, fontSize: '0.8125rem' }}>
                                Dated {formatDate(preview.paymentDate)}. Interest and penalties are paid before principal.
                            </p>
                        </>
                    )}
                </div>
            </form>
        </Sheet>
    )
}

function Line({ label, value }) {
    return (
        <>
            <dt>{label}</dt>
            <dd className="money">{value}</dd>
        </>
    )
}