// Display formatting only. The server does all money arithmetic; the browser never adds or rounds amounts.

const money = new Intl.NumberFormat('en-KE', { style: 'currency', currency: 'KES', currencyDisplay: 'code' })
const dateFormat = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })

export const formatMoney = (value) => (value == null ? '—' : money.format(value).replace(/\u00a0/g, ' '))

export function parseDate(iso) {
    const [y, m, d] = iso.split('-').map(Number)
    return new Date(y, m - 1, d)
}

export const formatDate = (iso) => (iso ? dateFormat.format(parseDate(iso)) : '—')

export const todayIso = () => new Date().toLocaleDateString('en-CA')

export const percent = (rate) => `${Number((rate * 100).toFixed(2))}%`

export const initials = (name) =>
    name
        .split(/\s+/)
        .filter(Boolean)
        .slice(0, 2)
        .map((part) => part[0].toUpperCase())
        .join('')

export const ROLE_LABEL = { OWNER: 'Owner', LOAN_OFFICER: 'Loan officer' }

// Status is always shown as words AND a colour, never colour alone.
export const STATUS = {
    CURRENT: { label: 'On track', tone: 'green' },
    DUE_SOON: { label: 'Due soon', tone: 'orange' },
    OVERDUE: { label: 'Overdue', tone: 'red' },
    COMPLETED: { label: 'Paid off', tone: 'gray' },
    CANCELLED: { label: 'Cancelled', tone: 'gray' },
}