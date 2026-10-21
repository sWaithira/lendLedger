import { formatMoney, percent } from './format.js'

// Lending rules as the person types them (percentages, text fields) and as the server stores them (fractions).

export const emptyRule = { ratePercent: '20', graceDays: '0', penaltyPercent: '0', penaltyCap: '' }
export const emptyProduct = { name: '', minPrincipal: '1000', maxPrincipal: '100000', maxTermPeriods: '6' }

export function ruleToPayload(rule) {
    return {
        ratePercent: Number(rule.ratePercent),
        graceDays: Number(rule.graceDays),
        penaltyPercent: Number(rule.penaltyPercent),
        penaltyCap: rule.penaltyCap === '' ? null : Number(rule.penaltyCap),
    }
}

export function productToPayload(product) {
    return {
        name: product.name.trim(),
        minPrincipal: Number(product.minPrincipal),
        maxPrincipal: Number(product.maxPrincipal),
        maxTermPeriods: Number(product.maxTermPeriods),
    }
}

/** A stored rule back into form fields. */
export function ruleToForm(rule) {
    return {
        ratePercent: String(Number((rule.ratePerPeriod * 100).toFixed(4))),
        graceDays: String(rule.graceDays),
        penaltyPercent: String(Number((rule.penaltyRatePerPeriod * 100).toFixed(4))),
        penaltyCap: rule.penaltyCap == null ? '' : String(rule.penaltyCap),
    }
}

export const penaltyText = (rule) =>
    rule.penaltyRatePerPeriod > 0
        ? `${percent(rule.penaltyRatePerPeriod)} per month overdue${rule.penaltyCap != null ? `, up to ${formatMoney(rule.penaltyCap)}` : ''}`
        : 'None'