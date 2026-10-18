import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { api, describeError } from '../api.js'
import { Button, Field, PageHead } from '../components/ui.jsx'
import { formatDate, formatMoney, percent, todayIso } from '../format.js'
import { useDebounced, useFetch } from '../hooks.js'

export default function LoanNew() {
    const navigate = useNavigate()
    const [params] = useSearchParams()
    const presetId = params.get('borrowerId')

    const preset = useFetch(presetId ? `/api/v1/borrowers/${presetId}` : null)
    const products = useFetch('/api/v1/products')

    const [picked, setPicked] = useState(undefined) // undefined = nobody chosen yet, null = person pressed "Change"
    const [productChoice, setProductChoice] = useState('')
    const [amount, setAmount] = useState('')
    const [issueDate, setIssueDate] = useState(todayIso())
    const [term, setTerm] = useState('1')

    const [result, setResult] = useState({ key: '', preview: null, error: '' })
    const [error, setError] = useState('')
    const [busy, setBusy] = useState(false)

    const borrower = picked !== undefined ? picked : preset.data
    const productId = productChoice || products.data?.[0]?.id || ''

    const ready = Boolean(productId && Number(amount) > 0 && issueDate && Number(term) >= 1 && Number(term) <= 60)
    const inputKey = JSON.stringify([productId, amount, issueDate, term])
    // A result is only shown while it still matches what is typed, so stale numbers never appear.
    const current = ready && result.key === inputKey ? result : { preview: null, error: '' }
    const { preview, error: previewError } = current

    // The server calculates the preview, so what you see is exactly what will be saved.
    useEffect(() => {
        if (!ready) return undefined
        let cancelled = false
        const timer = setTimeout(async () => {
            try {
                const p = await api.post('/api/v1/loans/preview', {
                    productId,
                    principal: Number(amount),
                    issueDate,
                    termPeriods: Number(term),
                })
                if (!cancelled) setResult({ key: inputKey, preview: p, error: '' })
            } catch (err) {
                if (!cancelled) setResult({ key: inputKey, preview: null, error: describeError(err) })
            }
        }, 400)
        return () => {
            cancelled = true
            clearTimeout(timer)
        }
    }, [ready, inputKey, productId, amount, issueDate, term])

    async function create(e) {
        e.preventDefault()
        setBusy(true)
        setError('')
        try {
            const loan = await api.post('/api/v1/loans', {
                borrowerId: borrower.id,
                productId,
                principal: Number(amount),
                issueDate,
                termPeriods: Number(term),
            })
            navigate(`/loans/${loan.id}`)
        } catch (err) {
            setError(describeError(err))
            setBusy(false)
        }
    }

    const product = products.data?.find((p) => p.id === productId)

    return (
        <>
            <Link className="back" to={borrower ? `/borrowers/${borrower.id}` : '/loans'}>
                ‹ {borrower ? borrower.fullName : 'Loans'}
            </Link>
            <PageHead title="New loan" subtitle="Check the summary, then confirm." />

            <form className="split" onSubmit={create}>
                <div className="card">
                    {error && (
                        <div className="notice error" role="alert">
                            {error}
                        </div>
                    )}
                    <Field label="Borrower">
                        <BorrowerPicker value={borrower} onChange={setPicked} />
                    </Field>
                    <Field label="Product" hint={product && `${percent(product.ratePerPeriod)} per month on the amount lent`}>
                        <select value={productId} onChange={(e) => setProductChoice(e.target.value)} required>
                            {products.data?.map((p) => (
                                <option key={p.id} value={p.id}>
                                    {p.name}
                                </option>
                            ))}
                        </select>
                    </Field>
                    <Field
                        label="Amount (KES)"
                        hint={product && `Between ${formatMoney(product.minPrincipal)} and ${formatMoney(product.maxPrincipal)}`}
                    >
                        <input type="number" inputMode="decimal" min="0" step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} required />
                    </Field>
                    <Field label="Date issued">
                        <input type="date" max={todayIso()} value={issueDate} onChange={(e) => setIssueDate(e.target.value)} required />
                    </Field>
                    <Field label="Term (months)">
                        <input type="number" min="1" max="60" step="1" value={term} onChange={(e) => setTerm(e.target.value)} required />
                    </Field>
                </div>

                <div>
                    <div className="card" aria-live="polite">
                        <h2 className="section-title" style={{ margin: '0 0 12px' }}>
                            Summary
                        </h2>
                        {previewError && (
                            <div className="notice error" role="alert">
                                {previewError}
                            </div>
                        )}
                        {!preview && !previewError && <p className="muted">Enter an amount to see what the borrower will owe.</p>}
                        {preview && (
                            <dl className="kv">
                                <dt>Amount lent</dt>
                                <dd className="money">{formatMoney(preview.principal)}</dd>
                                {preview.charges.map((c) => (
                                    <Charge key={`${c.type}-${c.kind}-${c.period}`} c={c} />
                                ))}
                                <dt>Total interest</dt>
                                <dd className="money">{formatMoney(preview.totalInterest)}</dd>
                                <dt className="total">Borrower repays</dt>
                                <dd className="total money">{formatMoney(preview.totalPayable)}</dd>
                                <dt>Due</dt>
                                <dd>{formatDate(preview.dueDate)}</dd>
                            </dl>
                        )}
                        {preview && <p className="muted" style={{ marginTop: 14, fontSize: '0.8125rem' }}>Calculated by the server using lending rules version {preview.ruleVersionNumber}.</p>}
                    </div>
                    <div style={{ marginTop: 16 }}>
                        <Button type="submit" busy={busy} disabled={!borrower || !preview} style={{ width: '100%' }}>
                            Create loan
                        </Button>
                    </div>
                </div>
            </form>
        </>
    )
}

function Charge({ c }) {
    const label = `${c.type === 'INTEREST' ? 'Interest' : 'Penalty'}, month ${c.period}`
    return (
        <>
            <dt>{label}</dt>
            <dd className="money">{formatMoney(c.amount)}</dd>
        </>
    )
}

function BorrowerPicker({ value, onChange }) {
    const [q, setQ] = useState('')
    const term = useDebounced(q)
    const { data } = useFetch(value ? null : `/api/v1/borrowers?size=6&q=${encodeURIComponent(term)}`)

    if (value) {
        return (
            <div className="picked">
                <span className="grow">{value.fullName}</span>
                <Button variant="plain" type="button" onClick={() => onChange(null)}>
                    Change
                </Button>
            </div>
        )
    }
    return (
        <div>
            <input type="search" placeholder="Search by name or phone" value={q} onChange={(e) => setQ(e.target.value)} />
            {data && data.items.length > 0 && (
                <div className="list" style={{ marginTop: 8 }}>
                    {data.items.map((b) => (
                        <button key={b.id} type="button" className="row" onClick={() => onChange(b)}>
                            <div className="grow">
                                <div className="title">{b.fullName}</div>
                                <div className="sub num">{b.phone}</div>
                            </div>
                        </button>
                    ))}
                </div>
            )}
        </div>
    )
}