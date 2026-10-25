import { useState } from 'react'
import { formatDate, formatMoney } from '../format.js'
import { Button, Field, Sheet } from './ui.jsx'

/** Opens the person's own phone, WhatsApp or mail app with the message ready. Nothing is sent from LendLedger. */
export default function ContactSheet({ item, onClose }) {
    const first = item.borrowerName.split(' ')[0]
    const [message, setMessage] = useState(
        `Hello ${first}, this is a friendly reminder that ${formatMoney(item.outstanding.total)} is overdue on your loan (it was due on ${formatDate(item.dueDate)}). Please pay as soon as you can. Thank you.`,
    )
    const phone = item.borrowerPhone
    const text = encodeURIComponent(message)

    return (
        <Sheet
            title={`Contact ${first}`}
            onClose={onClose}
            footer={
                <Button variant="secondary" type="button" onClick={onClose}>
                    Done
                </Button>
            }
        >
            <Field label="Message" hint="Edit it before you send.">
                <textarea value={message} onChange={(e) => setMessage(e.target.value)} maxLength={480} />
            </Field>
            <div className="list">
                <a className="row" href={`tel:+${phone}`}>
                    <span className="grow title">Call</span>
                    <span className="sub num">+{phone}</span>
                </a>
                <a className="row" href={`sms:+${phone}?&body=${text}`}>
                    <span className="grow title">Text message</span>
                    <span className="sub">SMS</span>
                </a>
                <a className="row" href={`https://wa.me/${phone}?text=${text}`} target="_blank" rel="noreferrer">
                    <span className="grow title">WhatsApp</span>
                    <span className="sub">Opens in a new tab</span>
                </a>
                {item.borrowerEmail && (
                    <a className="row" href={`mailto:${item.borrowerEmail}?subject=${encodeURIComponent('Loan payment reminder')}&body=${text}`}>
                        <span className="grow title">Email</span>
                        <span className="sub">{item.borrowerEmail}</span>
                    </a>
                )}
            </div>
            {!item.borrowerEmail && (
                <p className="muted" style={{ margin: '14px 0 8px', fontSize: '0.8125rem' }}>
                    To email {first}, add an email address to their profile.
                </p>
            )}
        </Sheet>
    )
}