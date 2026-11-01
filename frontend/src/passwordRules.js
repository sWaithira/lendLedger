// Keep MIN_LENGTH in step with backend security/PasswordPolicy.java.
// Set both to 1 to remove the length rule and keep only the mix of characters.
export const MIN_LENGTH = 8

export const RULES = [
    { id: 'upper', label: 'An uppercase letter', test: (p) => /[A-Z]/.test(p) },
    { id: 'lower', label: 'A lowercase letter', test: (p) => /[a-z]/.test(p) },
    { id: 'digit', label: 'A number', test: (p) => /[0-9]/.test(p) },
    { id: 'symbol', label: 'A symbol such as ! @ # $', test: (p) => /[^A-Za-z0-9]/.test(p) },
    ...(MIN_LENGTH > 1 ? [{ id: 'len', label: `${MIN_LENGTH} or more characters`, test: (p) => p.length >= MIN_LENGTH }] : []),
]

export const isStrong = (password) => RULES.every((r) => r.test(password))