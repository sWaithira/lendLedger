import { RULES } from '../passwordRules.js'

/** Shows which password rules are met, as words and a mark. */
export default function PasswordChecklist({ password }) {
    return (
        <ul className="muted" aria-label="Password requirements" style={{ listStyle: 'none', margin: '8px 0 0', padding: 0, fontSize: '0.8125rem' }}>
            {RULES.map((r) => {
                const ok = r.test(password)
                return (
                    <li key={r.id} aria-label={`${r.label}: ${ok ? 'met' : 'not met'}`}>
                        {ok ? '✓' : '○'} {r.label}
                    </li>
                )
            })}
        </ul>
    )
}