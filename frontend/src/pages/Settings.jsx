import { useState } from 'react'
import { api, describeError } from '../api.js'
import { useAuth } from '../auth.jsx'
import { ProductFields, RuleFields } from '../components/LenderForms.jsx'
import { Badge, Button, Empty, ErrorNote, Field, Loading, PageHead, Sheet } from '../components/ui.jsx'
import { formatDate, formatMoney, percent, todayIso } from '../format.js'
import { useFetch } from '../hooks.js'
import { emptyProduct, emptyRule, penaltyText, productToPayload, ruleToForm, ruleToPayload } from '../rules.js'

export default function Settings() {
    const { can } = useAuth()
    const allowed = can('SETTINGS_MANAGE')
    const { data, error, loading, reload } = useFetch(allowed ? '/api/v1/settings' : null)
    const [sheet, setSheet] = useState(null) // { type: 'edit' | 'new', product }
    const [notice, setNotice] = useState('')

    function saved(message) {
        setSheet(null)
        setNotice(message)
        reload()
    }

    if (!allowed) {
        return (
            <>
                <PageHead title="Settings" subtitle="Lending rules and products." />
                <div className="list">
                    <Empty title="Only the owner can change settings">Ask the owner of your lending business to update the rules.</Empty>
                </div>
            </>
        )
    }

    return (
        <>
            <PageHead
                title="Settings"
                subtitle={data ? `${data.businessName} · ${data.currency}` : 'Lending rules and products.'}
                actions={<Button onClick={() => setSheet({ type: 'new' })}>Add a product</Button>}
            />
            <ErrorNote error={error} onRetry={reload} />
            {loading && !data && <Loading rows={2} />}
            {notice && (
                <div className="notice info" role="status">
                    {notice}
                </div>
            )}
            {data && data.products.length === 0 && (
                <div className="list">
                    <Empty title="No products yet">Add a product to start lending.</Empty>
                </div>
            )}
            {data?.products.map((p) => (
                <ProductCard key={p.id} product={p} onEdit={() => setSheet({ type: 'edit', product: p })} />
            ))}

            {sheet?.type === 'edit' && <EditProductSheet product={sheet.product} onClose={() => setSheet(null)} onSaved={saved} />}
            {sheet?.type === 'new' && <NewProductSheet onClose={() => setSheet(null)} onSaved={saved} />}
        </>
    )
}

function ProductCard({ product, onEdit }) {
    const rule = product.currentRule
    const upcoming = product.history.find((h) => h.status === 'APPROVED' && h.effectiveFrom > todayIso())
    return (
        <div className="card">
            <div className="card-top">
                <h2 className="product-name">{product.name}</h2>
                <Badge tone={product.active ? 'green' : 'gray'}>{product.active ? 'Offered' : 'Not offered'}</Badge>
            </div>
            <p className="muted">
                {formatMoney(product.minPrincipal)} to {formatMoney(product.maxPrincipal)} · up to {product.maxTermPeriods} month
                {product.maxTermPeriods === 1 ? '' : 's'}
            </p>
            {rule ? (
                <dl className="kv" style={{ marginTop: 16 }}>
                    <dt>Interest</dt>
                    <dd>{percent(rule.ratePerPeriod)} per month</dd>
                    <dt>Late penalty</dt>
                    <dd>{penaltyText(rule)}</dd>
                    <dt>Grace period</dt>
                    <dd>{rule.graceDays > 0 ? `${rule.graceDays} days` : 'None'}</dd>
                    <dt>Rules since</dt>
                    <dd>
                        {rule.effectiveFrom <= '2001-01-01' ? 'You registered' : formatDate(rule.effectiveFrom)} · version {rule.versionNumber}
                    </dd>
                </dl>
            ) : (
                <div className="notice warn" style={{ marginTop: 16 }}>
                    No rules are in force today, so loans cannot be created for this product.
                </div>
            )}
            {upcoming && (
                <div className="notice info" style={{ marginTop: 16 }}>
                    A change starts on {formatDate(upcoming.effectiveFrom)}: {percent(upcoming.ratePerPeriod)} interest, penalty {penaltyText(upcoming).toLowerCase()}.
                </div>
            )}
            <div className="card-actions">
                <Button onClick={onEdit}>Edit product</Button>
            </div>
            <details className="history">
                <summary>Rule history</summary>
                <ul>
                    {product.history.map((h) => (
                        <li key={h.id}>
                            Version {h.versionNumber} · from {h.effectiveFrom <= '2001-01-01' ? 'registration' : formatDate(h.effectiveFrom)}
                            {h.effectiveTo ? ` to ${formatDate(h.effectiveTo)}` : ''}: {percent(h.ratePerPeriod)} interest, penalty {penaltyText(h).toLowerCase()}
                            {h.changeReason ? ` (${h.changeReason})` : ''}
                        </li>
                    ))}
                </ul>
            </details>
        </div>
    )
}

const RULE_KEYS = ['ratePercent', 'graceDays', 'penaltyPercent']

/** One form for the product and its rules. Changing a rule value creates a NEW rule version that starts on a date. */
function EditProductSheet({ product, onClose, onSaved }) {
    const initialRule = product.currentRule ? ruleToForm(product.currentRule) : emptyRule
    const [fields, setFields] = useState({
        name: product.name,
        minPrincipal: String(product.minPrincipal),
        maxPrincipal: String(product.maxPrincipal),
        maxTermPeriods: String(product.maxTermPeriods),
    })
    const [active, setActive] = useState(product.active)
    const [rule, setRule] = useState(initialRule)
    const [effectiveFrom, setEffectiveFrom] = useState(todayIso())
    const [reason, setReason] = useState('')
    const [error, setError] = useState('')
    const [busy, setBusy] = useState(false)

    const ruleChanged = Boolean(product.currentRule) && RULE_KEYS.some((k) => Number(rule[k]) !== Number(initialRule[k]))
    const days = Number(rule.graceDays)

    async function submit(e) {
        e.preventDefault()
        setBusy(true)
        setError('')
        let productSaved = false
        try {
            await api.put(`/api/v1/settings/products/${product.id}`, { ...productToPayload(fields), active })
            productSaved = true
            if (ruleChanged) {
                await api.post(`/api/v1/settings/products/${product.id}/rules`, {
                    rule: ruleToPayload(rule),
                    effectiveFrom,
                    reason: reason.trim() || undefined,
                })
            }
            onSaved(ruleChanged ? `Saved. The new rules apply to loans issued from ${formatDate(effectiveFrom)}.` : 'Product saved.')
        } catch (err) {
            setError(productSaved ? `Product details were saved, but the rules were not changed. ${describeError(err)}` : describeError(err))
            setBusy(false)
        }
    }

    return (
        <Sheet
            title={`Edit product: ${product.name}`}
            onClose={onClose}
            footer={
                <>
                    <Button variant="secondary" type="button" onClick={onClose}>
                        Cancel
                    </Button>
                    <Button type="submit" form="edit-product" busy={busy}>
                        Save
                    </Button>
                </>
            }
        >
            <form id="edit-product" onSubmit={submit}>
                {error && (
                    <div className="notice error" role="alert">
                        {error}
                    </div>
                )}
                <ProductFields value={fields} onChange={setFields} />
                <label className="check">
                    <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
                    <span>Offer this product for new loans</span>
                </label>

                <h3 className="section-title" style={{ marginTop: 24 }}>Lending rules</h3>
                <RuleFields value={rule} onChange={setRule} />
                {ruleChanged && (
                    <>
                        <Field label="New rules start on">
                            <input type="date" min={todayIso()} value={effectiveFrom} onChange={(e) => setEffectiveFrom(e.target.value)} required />
                        </Field>
                        <Field label="Reason (optional)">
                            <input value={reason} onChange={(e) => setReason(e.target.value)} maxLength={300} />
                        </Field>
                        <div className="notice info">
                            New rules from {formatDate(effectiveFrom)}: {rule.ratePercent}% interest, {days > 0 ? `${days} day${days === 1 ? '' : 's'} grace` : 'no grace'}, penalty{' '}
                            {rule.penaltyPercent}% of the interest each month overdue. Existing loans keep their rules.
                        </div>
                    </>
                )}
            </form>
        </Sheet>
    )
}

function NewProductSheet({ onClose, onSaved }) {
    const [fields, setFields] = useState(emptyProduct)
    const [rule, setRule] = useState(emptyRule)
    const [error, setError] = useState('')
    const [busy, setBusy] = useState(false)

    async function submit(e) {
        e.preventDefault()
        setBusy(true)
        setError('')
        try {
            await api.post('/api/v1/settings/products', { ...productToPayload(fields), rule: ruleToPayload(rule) })
            onSaved('Product added.')
        } catch (err) {
            setError(describeError(err))
            setBusy(false)
        }
    }

    return (
        <Sheet
            title="Add a product"
            onClose={onClose}
            footer={
                <>
                    <Button variant="secondary" type="button" onClick={onClose}>
                        Cancel
                    </Button>
                    <Button type="submit" form="new-product" busy={busy}>
                        Add product
                    </Button>
                </>
            }
        >
            <form id="new-product" onSubmit={submit}>
                {error && (
                    <div className="notice error" role="alert">
                        {error}
                    </div>
                )}
                <ProductFields value={fields} onChange={setFields} />
                <RuleFields value={rule} onChange={setRule} />
            </form>
        </Sheet>
    )
}