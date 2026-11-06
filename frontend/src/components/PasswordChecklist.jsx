import { RULES } from '../passwordRules.js'

/** Shows which password rules are met: a filled dot and green text when met. */
export default function PasswordChecklist({ password }) {
    return (
        <ul className="pw-rules" aria-label="Password requirements">
            {RULES.map((r) => {
                const ok = r.test(password)
                return (
                    <li key={r.id} className={ok ? 'ok' : ''} aria-label={`${r.label}: ${ok ? 'met' : 'not met'}`}>
                        <i aria-hidden="true" /> {r.label}
                    </li>
                )
            })}
        </ul>
    )
}