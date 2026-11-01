import { formatDate, formatMoney, loanNumber } from './format.js'

/** Plain text a lender can send by WhatsApp, SMS or email. Built in the browser from numbers the server already sent. */
export function receiptText(loan, payment, methodLabel, isLatest) {
    return [
        'Payment receipt',
        `Loan ${loanNumber(loan.loanNumber)}`,
        `Received from ${loan.borrowerName}`,
        `Amount: ${formatMoney(payment.amount)}`,
        `Date: ${formatDate(payment.paymentDate)}`,
        `Method: ${methodLabel}`,
        payment.reference ? `Reference: ${payment.reference}` : null,
        isLatest ? `Balance owed today: ${formatMoney(loan.outstanding.total)}` : null,
        'Thank you.',
    ]
        .filter(Boolean)
        .join('\n')
}

export function statementText(loan, entries) {
    const lines = entries.slice(-30).map((e) => `${formatDate(e.date)} · ${e.description} · ${formatMoney(e.amount)} · Balance ${formatMoney(e.balanceAfter)}`)
    return [`Loan statement ${loanNumber(loan.loanNumber)}`, loan.borrowerName, '', ...lines, '', `Owed today: ${formatMoney(loan.outstanding.total)}`].join('\n')
}

/** Kenyan number as digits with the 254 prefix (for wa.me and sms links), or '' when it cannot be read. */
export function intlPhone(phone) {
    const d = (phone || '').replace(/\D/g, '')
    if (d.startsWith('254') && d.length === 12) return d
    if (d.startsWith('0') && d.length === 10) return `254${d.slice(1)}`
    if (d.length === 9 && (d.startsWith('7') || d.startsWith('1'))) return `254${d}`
    return ''
}