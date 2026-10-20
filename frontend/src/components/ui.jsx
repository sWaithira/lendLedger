import { useEffect, useRef } from 'react'
import { STATUS } from '../format.js'

export function Button({ variant = 'primary', busy = false, children, ...rest }) {
    return (
        <button className={`btn ${variant}`} {...rest} disabled={busy || rest.disabled}>
            {busy ? 'Please wait…' : children}
        </button>
    )
}

export function Field({ label, hint, error, children }) {
    return (
        <label className="field">
            <span>{label}</span>
            {children}
            {hint && <small>{hint}</small>}
            {error && <small className="err">{error}</small>}
        </label>
    )
}

export function Badge({ tone = 'gray', children }) {
    return <span className={`badge ${tone}`}>{children}</span>
}

export function StatusBadge({ status }) {
    const s = STATUS[status] || { label: status, tone: 'gray' }
    return <Badge tone={s.tone}>{s.label}</Badge>
}

export function Segmented({ label, value, onChange, options }) {
    return (
        <div className="seg" role="radiogroup" aria-label={label}>
            {options.map((o) => (
                <button
                    key={o.value}
                    type="button"
                    role="radio"
                    aria-checked={value === o.value}
                    className={value === o.value ? 'on' : ''}
                    onClick={() => onChange(o.value)}
                >
                    {o.label}
                </button>
            ))}
        </div>
    )
}
export function ViewToggle({ value, onChange }) {
    return (
        <div className="seg icons" role="radiogroup" aria-label="Layout">
            {[
                ['list', 'List'],
                ['cards', 'Cards'],
            ].map(([v, label]) => (
                <button
                    key={v}
                    type="button"
                    role="radio"
                    aria-checked={value === v}
                    aria-label={label}
                    title={label}
                    className={value === v ? 'on' : ''}
                    onClick={() => onChange(v)}
                >
                    <Icon name={v === 'list' ? 'list' : 'grid'} />
                </button>
            ))}
        </div>
    )
}

export function PageHead({ title, subtitle, actions }) {
    return (
        <div className="page-head">
            <div>
                <h1>{title}</h1>
                {subtitle && <p>{subtitle}</p>}
            </div>
            {actions && <div>{actions}</div>}
        </div>
    )
}

export function Empty({ title, children }) {
    return (
        <div className="empty">
            <strong>{title}</strong>
            {children}
        </div>
    )
}

export function Loading({ rows = 4 }) {
    return (
        <div className="list" aria-busy="true" aria-label="Loading">
            {Array.from({ length: rows }, (_, i) => (
                <div key={i} className="row">
                    <div className="skeleton grow" />
                </div>
            ))}
        </div>
    )
}

export function ErrorNote({ error, onRetry }) {
    if (!error) return null
    return (
        <div className="notice error" role="alert">
            {error.message}{' '}
            {onRetry && (
                <button className="btn plain" type="button" onClick={onRetry}>
                    Try again
                </button>
            )}
        </div>
    )
}

/** A modal sheet: Escape closes it, focus stays inside, and focus returns to where it came from. */
export function Sheet({ title, onClose, children, footer }) {
    const dialog = useRef(null)
    const closeRef = useRef(onClose)
    useEffect(() => {
        closeRef.current = onClose
    })

    useEffect(() => {
        const previous = document.activeElement
        const el = dialog.current
        el.querySelector('input, textarea, select')?.focus()
        document.body.style.overflow = 'hidden'

        function onKey(e) {
            if (e.key === 'Escape') closeRef.current()
            if (e.key !== 'Tab') return
            const items = [...el.querySelectorAll('a[href], button:not([disabled]), input, select, textarea')]
            if (!items.length) return
            const first = items[0]
            const last = items[items.length - 1]
            if (e.shiftKey && document.activeElement === first) {
                e.preventDefault()
                last.focus()
            } else if (!e.shiftKey && document.activeElement === last) {
                e.preventDefault()
                first.focus()
            }
        }
        document.addEventListener('keydown', onKey)
        return () => {
            document.removeEventListener('keydown', onKey)
            document.body.style.overflow = ''
            previous?.focus?.()
        }
    }, [])

    return (
        <div className="sheet-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
            <div className="sheet" role="dialog" aria-modal="true" aria-labelledby="sheet-title" ref={dialog}>
                <header>
                    <h2 id="sheet-title">{title}</h2>
                    <button className="icon-btn" type="button" aria-label="Close" onClick={onClose}>
                        ×
                    </button>
                </header>
                <div className="sheet-body">{children}</div>
                {footer && <footer>{footer}</footer>}
            </div>
        </div>
    )
}

const ICONS = {
    people: 'M16 11a3 3 0 1 0-3-3 3 3 0 0 0 3 3Zm-8 0a3 3 0 1 0-3-3 3 3 0 0 0 3 3Zm0 2c-2.3 0-7 1.2-7 3.5V19h14v-2.5C15 14.2 10.3 13 8 13Zm8 0c-.3 0-.6 0-1 .1 1.2.8 2 1.9 2 3.4V19h6v-2.5c0-2.3-4.7-3.5-7-3.5Z',
    loans: 'M4 5h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2Zm8 3.5A3.5 3.5 0 1 0 15.5 12 3.5 3.5 0 0 0 12 8.5ZM4.5 8v2a2 2 0 0 0 2-2Zm0 8h2a2 2 0 0 0-2-2Zm15 0v-2a2 2 0 0 0-2 2Zm0-8h-2a2 2 0 0 0 2 2Z',
    chart: 'M4 20V10h4v10H4Zm6 0V4h4v16h-4Zm6 0v-7h4v7h-4Z',
    list: 'M4 6h16v2H4V6Zm0 5h16v2H4v-2Zm0 5h16v2H4v-2Z',
    grid: 'M4 4h7v7H4V4Zm9 0h7v7h-7V4ZM4 13h7v7H4v-7Zm9 0h7v7h-7v-7Z',
    alert: 'M12 2 1 21h22L12 2Zm1 14h-2v-2h2v2Zm0-4h-2V9h2v3Z',
}

export function Icon({ name }) {
    return (
        <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
            <path d={ICONS[name]} />
        </svg>
    )
}