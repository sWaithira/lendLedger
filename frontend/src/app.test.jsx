import { render, screen, within, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import axe from 'axe-core'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App.jsx'
import { AuthProvider } from './auth.jsx'

const OWNER_PERMS = ['BORROWER_READ', 'BORROWER_WRITE', 'LOAN_READ', 'LOAN_CREATE', 'REPAYMENT_POST', 'REPAYMENT_REVERSE', 'REPORT_VIEW', 'AUDIT_VIEW', 'USER_MANAGE']
const OFFICER_PERMS = ['BORROWER_READ', 'BORROWER_WRITE', 'LOAN_READ', 'LOAN_CREATE', 'REPAYMENT_POST', 'REPORT_VIEW']

const loan = {
    id: 'L1', borrowerId: 'B1', borrowerName: 'Jane Wanjiru', productId: 'P1', productName: 'Monthly Flat Loan',
    principal: 10000.0, currency: 'KES', issueDate: '2026-10-17', termPeriods: 1, dueDate: '2026-11-17', ratePerPeriod: 0.3,
    ruleVersionNumber: 1, status: 'ACTIVE', displayStatus: 'CURRENT', daysOverdue: 0, asOf: '2026-10-18',
    outstanding: { principal: 8000.0, interest: 0.0, penalty: 0.0, total: 8000.0 },
}
const payments = [
    { id: 'R1', loanId: 'L1', amount: 5000.0, paymentDate: '2026-10-17', method: 'CASH', reference: null, allocations: [{ component: 'INTEREST', amount: 3000 }, { component: 'PRINCIPAL', amount: 2000 }], reversed: false, reversalReason: null },
    { id: 'R2', loanId: 'L1', amount: 8000.0, paymentDate: '2026-10-17', method: 'MPESA_MANUAL', reference: 'ABC123XYZ', allocations: [{ component: 'PRINCIPAL', amount: 8000 }], reversed: true, reversalReason: 'Entered by mistake' },
]
const statement = { loan, entries: [
        { date: '2026-10-17', type: 'LOAN_ISSUED', description: 'Loan issued', amount: 10000, balanceAfter: 10000 },
        { date: '2026-10-17', type: 'INTEREST', description: 'Interest, term period 1', amount: 3000, balanceAfter: 13000 },
        { date: '2026-10-17', type: 'PAYMENT', description: 'Payment by CASH (INTEREST 3000.00, PRINCIPAL 2000.00)', amount: -5000, balanceAfter: 8000 },
    ] }
const summary = { asOf: '2026-10-18', from: '2026-05-01', to: '2026-10-18',
    portfolio: { activeLoans: 4, overdueLoans: 1, dueSoonLoans: 0, completedLoans: 0, outstanding: { principal: 38000, interest: 15000, penalty: 0, total: 53000 }, overdueOutstanding: { principal: 10000, interest: 12000, penalty: 0, total: 22000 } },
    collections: { total: 8000, principal: 2000, interest: 6000, penalty: 0 }, disbursements: { loans: 3, principal: 30000 } }
const series = [
    { periodStart: '2026-07-01', total: 0, principal: 0, interest: 0, penalty: 0 },
    { periodStart: '2026-09-01', total: 13000, principal: 10000, interest: 3000, penalty: 0 },
    { periodStart: '2026-10-01', total: -8000, principal: -8000, interest: 0, penalty: 0 },
]
const overdue = { items: [{ loanId: 'L9', borrowerId: 'B1', borrowerName: 'Jane Wanjiru', borrowerPhone: '254711222333', principal: 10000, issueDate: '2026-06-10', dueDate: '2026-07-10', daysOverdue: 100, overduePeriods: 3, outstanding: { principal: 10000, interest: 12000, penalty: 0, total: 22000 }, lastPaymentDate: null }], page: 0, size: 12, totalItems: 1, totalPages: 1 }

let calls
let postFailures

// A stand-in for the Spring API, built from real responses. Every request is recorded in `calls`.
function backend(perms) {
    return vi.fn(async (url, opts = {}) => {
        const method = opts.method || 'GET'
        calls.push({ method, url, headers: opts.headers, body: opts.body })
        const json = (status, body) => ({ ok: status < 400, status, text: async () => JSON.stringify(body) })
        if (url === '/api/v1/me') return json(200, { userId: 'U1', tenantId: 'T1', name: 'Demo Owner', email: 'o@x.test', role: perms.includes('USER_MANAGE') ? 'OWNER' : 'LOAN_OFFICER', permissions: perms })
        if (url === '/api/v1/loans/L1') return json(200, loan)
        if (url === '/api/v1/loans/L1/statement') return json(200, statement)
        if (url === '/api/v1/loans/L1/repayments/preview') return json(200, { paymentDate: '2026-10-18', amount: 5000, before: { principal: 8000, interest: 0, penalty: 0, total: 8000 }, allocations: [{ component: 'PRINCIPAL', amount: 5000 }], after: { principal: 3000, interest: 0, penalty: 0, total: 3000 }, settlesLoan: false })
        if (url === '/api/v1/loans/L1/repayments' && method === 'POST') {
            if (postFailures > 0) { postFailures -= 1; return json(500, { error: 'server_error', message: 'Something broke on our side.' }) }
            return json(201, { repayment: payments[0], loan, replayed: false })
        }
        if (url === '/api/v1/loans/L1/repayments') return json(200, payments)
        if (url.startsWith('/api/v1/dashboard/summary')) return json(200, summary)
        if (url.startsWith('/api/v1/dashboard/collections')) return json(200, series)
        if (url.startsWith('/api/v1/overdue')) return json(200, overdue)
        return json(404, { error: 'not_found', message: 'Not found' })
    })
}

function renderAt(path, perms = OWNER_PERMS) {
    vi.stubGlobal('fetch', backend(perms))
    return render(
        <MemoryRouter initialEntries={[path]}>
            <AuthProvider>
                <App />
            </AuthProvider>
        </MemoryRouter>,
    )
}

async function accessibilityViolations(container) {
    // Colour contrast needs real rendering, so it is audited separately from the design tokens.
    const res = await axe.run(container, { rules: { 'color-contrast': { enabled: false } } })
    return res.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.html.slice(0, 80)).join(' | ')}`)
}

beforeEach(() => {
    calls = []
    postFailures = 0
    document.cookie = 'XSRF-TOKEN=abc'
})

describe('loan detail', () => {
    it('shows what is owed, the statement, and Reverse only on the latest standing payment (owner)', async () => {
        renderAt('/loans/L1')
        expect(await screen.findByText('KES 8,000.00', { selector: '.hero' })).toBeTruthy()
        expect(screen.getAllByRole('button', { name: 'Reverse' })).toHaveLength(1)
        expect(screen.getByText(/Reversed: Entered by mistake/)).toBeTruthy()
        const table = await screen.findByRole('table', { name: 'Loan statement' })
        expect(within(table).getAllByRole('row')).toHaveLength(4)
    })

    it('hides Reverse from a loan officer', async () => {
        renderAt('/loans/L1', OFFICER_PERMS)
        await screen.findByText('KES 8,000.00', { selector: '.hero' })
        expect(screen.queryByRole('button', { name: 'Reverse' })).toBeNull()
        expect(screen.getByRole('button', { name: 'Record payment' })).toBeTruthy()
    })

    it('has no detectable accessibility violations', async () => {
        const { container } = renderAt('/loans/L1')
        await screen.findByRole('table', { name: 'Loan statement' })
        expect(await accessibilityViolations(container)).toEqual([])
    })
})

describe('record payment', () => {
    it('previews the split, then posts once with an idempotency key', async () => {
        const user = userEvent.setup()
        renderAt('/loans/L1')
        await user.click(await screen.findByRole('button', { name: 'Record payment' }))
        const dialog = await screen.findByRole('dialog', { name: 'Record payment' })
        await user.type(within(dialog).getByLabelText(/Amount/), '5000')
        expect(await within(dialog).findByText('KES 3,000.00', { selector: '.total' })).toBeTruthy()
        expect(within(dialog).getByText('Principal')).toBeTruthy()

        await user.click(within(dialog).getByRole('button', { name: 'Record payment' }))
        expect(await screen.findByText('Payment recorded.')).toBeTruthy()
        const posts = calls.filter((c) => c.method === 'POST' && c.url === '/api/v1/loans/L1/repayments')
        expect(posts).toHaveLength(1)
        expect(posts[0].headers['Idempotency-Key']).toMatch(/^.{8,}$/)
        expect(JSON.parse(posts[0].body)).toMatchObject({ amount: 5000, method: 'CASH' })
        expect(posts[0].headers['X-XSRF-TOKEN']).toBe('abc')
    })

    it('retries with the SAME idempotency key after a failure', async () => {
        const user = userEvent.setup()
        postFailures = 1
        renderAt('/loans/L1')
        await user.click(await screen.findByRole('button', { name: 'Record payment' }))
        const dialog = await screen.findByRole('dialog', { name: 'Record payment' })
        await user.type(within(dialog).getByLabelText(/Amount/), '5000')
        await within(dialog).findByText('KES 3,000.00', { selector: '.total' })
        await user.click(within(dialog).getByRole('button', { name: 'Record payment' }))
        expect(await within(dialog).findByText('Something broke on our side.')).toBeTruthy()
        await user.click(within(dialog).getByRole('button', { name: 'Record payment' }))
        await screen.findByText('Payment recorded.')
        const keys = calls
            .filter((c) => c.method === 'POST' && c.url === '/api/v1/loans/L1/repayments')
            .map((c) => c.headers['Idempotency-Key'])
        expect(keys).toHaveLength(2)
        expect(keys[0]).toBe(keys[1])
    })

    it('the payment dialog has no detectable accessibility violations', async () => {
        const user = userEvent.setup()
        renderAt('/loans/L1')
        await user.click(await screen.findByRole('button', { name: 'Record payment' }))
        const dialog = await screen.findByRole('dialog', { name: 'Record payment' })
        expect(await accessibilityViolations(dialog)).toEqual([])
    })

    it('closes on Escape and keeps focus inside the dialog', async () => {
        const user = userEvent.setup()
        renderAt('/loans/L1')
        await user.click(await screen.findByRole('button', { name: 'Record payment' }))
        const dialog = await screen.findByRole('dialog', { name: 'Record payment' })
        expect(dialog.contains(document.activeElement)).toBe(true)
        await user.tab({ shift: true })
        await user.tab({ shift: true })
        expect(dialog.contains(document.activeElement)).toBe(true)
        await user.keyboard('{Escape}')
        await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
    })
})

describe('dashboard', () => {
    it('leads with one number, flags overdue, and draws reversals as negative bars', async () => {
        const { container } = renderAt('/dashboard')
        expect(await screen.findByText('KES 53,000.00', { selector: '.hero' })).toBeTruthy()
        expect(screen.getByText(/1 loan · see who to call/)).toBeTruthy()
        await waitFor(() => expect(container.querySelectorAll('rect.bar').length).toBe(3))
        expect(container.querySelectorAll('rect.bar.neg')).toHaveLength(1)
        const table = container.querySelector('table.sr-only')
        expect(within(table).getAllByRole('row')).toHaveLength(3)
    })

    it('shows the value of a bar on hover', async () => {
        const user = userEvent.setup()
        const { container } = renderAt('/dashboard')
        await waitFor(() => expect(container.querySelectorAll('svg g[tabindex]').length).toBe(3))
        await user.hover(container.querySelectorAll('svg g[tabindex]')[1])
        expect(await screen.findByText('KES 13,000.00', { selector: '.chart-readout strong' })).toBeTruthy()
    })

    it('has no detectable accessibility violations', async () => {
        const { container } = renderAt('/dashboard')
        await waitFor(() => expect(container.querySelectorAll('rect.bar').length).toBe(3))
        await screen.findByText('Needs attention')
        expect(await accessibilityViolations(container)).toEqual([])
    })
})

describe('overdue queue', () => {
    it('offers a call link with the full international number', async () => {
        const { container } = renderAt('/overdue')
        const call = await screen.findByRole('link', { name: 'Call Jane Wanjiru' })
        expect(call.getAttribute('href')).toBe('tel:+254711222333')
        expect(screen.getByText('100 days overdue')).toBeTruthy()
        expect(await accessibilityViolations(container)).toEqual([])
    })
})