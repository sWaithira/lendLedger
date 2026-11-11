import { useState } from 'react'
import { intlPhone } from '../receipt.js'
import { Button, Sheet } from './ui.jsx'

/** Send a receipt or statement by WhatsApp, SMS or email, or copy the text. */
export default function ShareSheet({ title, subject, text, phone, email, onClose }) {
    const [copied, setCopied] = useState(false)
    const intl = intlPhone(phone)
    const body = encodeURIComponent(text)

    async function copy() {
        try {
            await navigator.clipboard.writeText(text)
            setCopied(true)
        } catch {
            setCopied(false)
        }
    }

    return (
        <Sheet
            title={title}
            onClose={onClose}
            footer={
                <Button variant="secondary" type="button" onClick={onClose}>
                    Close
                </Button>
            }
        >
            <p className="muted" style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>
                {text}
            </p>
            <div className="card-actions">
                {intl && (
                    <a className="btn primary" href={`https://wa.me/${intl}?text=${body}`} target="_blank" rel="noreferrer">
                        WhatsApp
                    </a>
                )}
                {intl && (
                    <a className="btn secondary" href={`sms:+${intl}?&body=${body}`}>
                        SMS
                    </a>
                )}
                {email && (
                    <a className="btn secondary" href={`mailto:${email}?subject=${encodeURIComponent(subject)}&body=${body}`}>
                        Email
                    </a>
                )}
                <Button variant="secondary" onClick={copy}>
                    {copied ? 'Copied' : 'Copy text'}
                </Button>
            </div>
            {!intl && !email && <p className="muted">This borrower has no phone number or email on file. Copy the text and send it yourself.</p>}
        </Sheet>
    )
}
