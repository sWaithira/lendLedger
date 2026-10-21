import { useEffect, useState } from 'react'
import { api, describeError } from '../api.js'
import { formatMoney } from '../format.js'
import { ruleToPayload } from '../rules.js'
import { Field } from './ui.jsx'

/** Name, amount limits and longest term for a product. */
export function ProductFields({ value, onChange }) {
    const set = (key) => (e) => onChange({ ...value, [key]: e.target.value })
    return (
        <>
            <Field label="Product name">
                <input value={value.name} onChange={set('name')} required minLength={2} maxLength={80} autoComplete="off" />
            </Field>
            <div className="pair">
                <Field label="Smallest loan (KES)">
                    <input type="number" min="1" step="any" value={value.minPrincipal} onChange={set('minPrincipal')} required />
                </Field>
                <Field label="Largest loan (KES)">
                    <input type="number" min="1" step="any" value={value.maxPrincipal} onChange={set('maxPrincipal')} required />
                </Field>
            </div>
            <Field label="Longest term (months)">
                <input type="number" min="1" max="60" step="1" value={value.maxTermPeriods} onChange={set('maxTermPeriods')} required />
            </Field>
        </>
    )
}

/** The lending rules: how interest and late penalties work. */
export function RuleFields({ value, onChange }) {
    const set = (key) => (e) => onChange({ ...value, [key]: e.target.value })
    return (
        <>
            <Field label="Interest per month (%)" hint="Charged on the amount lent, for every month of the term and every month a loan is overdue.">
                <input type="number" min="0.01" max="100" step="any" value={value.ratePercent} onChange={set('ratePercent')} required />
            </Field>
            <Field label="Grace period (days)" hint="Days after the due date before a loan counts as overdue.">
                <input type="number" min="0" max="60" step="1" value={value.graceDays} onChange={set('graceDays')} required />
            </Field>
            <div className="pair">
                <Field label="Late penalty (% per month)" hint="Per full month overdue. 0 means none.">
                    <input type="number" min="0" max="100" step="any" value={value.penaltyPercent} onChange={set('penaltyPercent')} required />
                </Field>
                <Field label="Penalty limit (KES)" hint="Optional.">
                    <input type="number" min="0" step="any" value={value.penaltyCap} onChange={set('penaltyCap')} />
                </Field>
            </div>
            <p className="muted" style={{ fontSize: '0.8125rem', marginBottom: 18 }}>
                Payments are applied to penalty first, then interest, then principal.
            </p>
        </>
    )
}

/** Shows what the rules mean in money, calculated by the same engine that prices real loans. */
export function RuleExample({ rule, sticky = false }) {
    const [amount, setAmount] = useState('10000')
    const [months, setMonths] = useState('1')
    const [result, setResult] = useState({ key: '', scenarios: null, error: '' })

    const ready =
        Number(rule.ratePercent) > 0 && Number(rule.graceDays) >= 0 && Number(rule.penaltyPercent) >= 0 &&
        Number(amount) >= 1 && Number(months) >= 1 && Number(months) <= 60
    const inputKey = JSON.stringify([rule, amount, months])
    const current = ready && result.key === inputKey ? result : { scenarios: null, error: '' }

    useEffect(() => {
        if (!ready) return undefined
        let cancelled = false
        const timer = setTimeout(async () => {
            try {
                const res = await api.post('/api/v1/rules/preview', {
                    principal: Number(amount),
                    termMonths: Number(months),
                    rule: ruleToPayload(rule),
                })
                if (!cancelled) setResult({ key: inputKey, scenarios: res.scenarios, error: '' })
            } catch (err) {
                if (!cancelled) setResult({ key: inputKey, scenarios: null, error: describeError(err) })
            }
        }, 400)
        return () => {
            cancelled = true
            clearTimeout(timer)
        }
    }, [ready, inputKey, rule, amount, months])

    const worst = current.scenarios?.[current.scenarios.length - 1]

    return (
        <div className={`card ${sticky ? 'sticky' : ''}`} aria-live="polite">
            <h2 className="section-title" style={{ margin: '0 0 12px' }}>
                What your rules mean
            </h2>
            <div className="pair">
                <Field label="Example loan (KES)">
                    <input type="number" min="1" step="any" value={amount} onChange={(e) => setAmount(e.target.value)} />
                </Field>
                <Field label="For (months)">
                    <input type="number" min="1" max="60" step="1" value={months} onChange={(e) => setMonths(e.target.value)} />
                </Field>
            </div>
            {current.error && (
                <div className="notice error" role="alert">
                    {current.error}
                </div>
            )}
            {!current.scenarios && !current.error && <p className="muted">Fill in the rules to see an example.</p>}
            {current.scenarios && (
                <>
                    <dl className="kv">
                        {current.scenarios.map((s) => (
                            <Row key={s.label} label={s.label} value={formatMoney(s.total)} />
                        ))}
                    </dl>
                    {worst.penalty > 0 && (
                        <p className="muted" style={{ marginTop: 12, fontSize: '0.8125rem' }}>
                            {worst.label} includes {formatMoney(worst.penalty)} of late penalty and {formatMoney(worst.interest)} of interest.
                        </p>
                    )}
                </>
            )}
        </div>
    )
}

function Row({ label, value }) {
    return (
        <>
            <dt>{label}</dt>
            <dd className="money">{value}</dd>
        </>
    )
}