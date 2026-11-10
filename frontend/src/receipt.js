import { formatDate, formatMoney, loanNumber, todayIso } from './format.js'

const METHOD_WORDS = { CASH: 'cash', MPESA_MANUAL: 'M-Pesa', BANK: 'bank transfer', MANUAL: 'other', ROLLOVER: 'rollover' }

/** Turns the server's wording into plain words a borrower understands. */
function cleanDescription(text) {
    return String(text || '')
        .replace(/\s*\([^)]*\)/g, '')
        .replace(/\b(MPESA_MANUAL|CASH|BANK|MANUAL|ROLLOVER)\b/g, (m) => METHOD_WORDS[m])
        .replace(/^Interest, term period \d+$/, 'Interest')
        .trim()
}

/** Plain text a lender can send by WhatsApp, SMS or email. Built in the browser from numbers the server already sent. */
export function receiptText(loan, payment, methodLabel, isLatest) {
    const owed = Number(loan.outstanding.total)
    return [
        'PAYMENT RECEIPT',
        `Loan ${loanNumber(loan.loanNumber)} · ${loan.borrowerName}`,
        '',
        `Amount received: ${formatMoney(payment.amount)}`,
        `Date: ${formatDate(payment.paymentDate)}`,
        `Paid by: ${methodLabel}`,
        payment.reference ? `Reference: ${payment.reference}` : null,
        isLatest ? (owed > 0 ? `Balance still owed: ${formatMoney(owed)}` : 'Loan fully paid') : null,
        '',
        'Thank you.',
    ]
        .filter((line) => line !== null)
        .join('\n')
}

export function statementText(loan, entries) {
    const lines = entries.slice(-30).map((e) => {
        const amount = Number(e.amount)
        const change = amount < 0 ? `${formatMoney(Math.abs(amount))} paid` : `${formatMoney(amount)} added`
        return `${formatDate(e.date)} · ${cleanDescription(e.description)}\n${change} · Balance ${formatMoney(e.balanceAfter)}`
    })
    return [
        'LOAN STATEMENT',
        `Loan ${loanNumber(loan.loanNumber)} · ${loan.borrowerName}`,
        `As of ${formatDate(todayIso())}`,
        '',
        lines.join('\n\n'),
        '',
        `Owed today: ${formatMoney(loan.outstanding.total)}`,
    ].join('\n')
}

/** Kenyan number as digits with the 254 prefix (for wa.me and sms links), or '' when it cannot be read. */
export function intlPhone(phone) {
    const d = (phone || '').replace(/\D/g, '')
    if (d.startsWith('254') && d.length === 12) return d
    if (d.startsWith('0') && d.length === 10) return `254${d.slice(1)}`
    if (d.length === 9 && (d.startsWith('7') || d.startsWith('1'))) return `254${d}`
    return ''
}