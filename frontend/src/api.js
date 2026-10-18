// All server calls go through here: JSON, session cookie, CSRF token, and readable errors.

export class ApiError extends Error {
    constructor(status, body) {
        super(
            (body && body.message) ||
            (status === 0 ? "Can't reach the server. Check your connection and try again." : `Something went wrong (${status}).`),
        )
        this.status = status
        this.code = body && body.error
        this.body = body || {}
    }
}

let onUnauthorized = () => {}
export function setUnauthorizedHandler(fn) {
    onUnauthorized = fn
}

function xsrfToken() {
    const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/)
    return match ? decodeURIComponent(match[1]) : ''
}

async function request(method, path, { json, form, headers: extra } = {}) {
    const headers = { Accept: 'application/json', ...extra }
    let body

    if (method !== 'GET') {
        // Spring sets the XSRF-TOKEN cookie on any response; make sure we have one before the first write.
        if (!xsrfToken()) await fetch('/api/v1/me', { credentials: 'same-origin' }).catch(() => {})
        headers['X-XSRF-TOKEN'] = xsrfToken()
    }
    if (json !== undefined) {
        headers['Content-Type'] = 'application/json'
        body = JSON.stringify(json)
    }
    if (form) {
        headers['Content-Type'] = 'application/x-www-form-urlencoded'
        body = new URLSearchParams(form).toString()
    }

    let res
    try {
        res = await fetch(path, { method, headers, body, credentials: 'same-origin' })
    } catch {
        throw new ApiError(0, null)
    }

    const text = await res.text()
    let data = null
    if (text) {
        try {
            data = JSON.parse(text)
        } catch {
            data = null
        }
    }
    if (!res.ok) {
        if (res.status === 401 && !path.endsWith('/auth/login')) onUnauthorized()
        throw new ApiError(res.status, data)
    }
    return data
}

export const api = {
    get: (path) => request('GET', path),
    post: (path, json, headers) => request('POST', path, { json, headers }),
    put: (path, json) => request('PUT', path, { json }),
    login: (email, password) => request('POST', '/api/v1/auth/login', { form: { email, password } }),
    logout: () => request('POST', '/api/v1/auth/logout'),
}

/** Turns any error into one sentence a person can act on. */
export function describeError(err) {
    if (err.code === 'validation_failed' && err.body.fields) return Object.values(err.body.fields).join('. ')
    if (err.status === 403) return err.body.message || "You don't have permission to do that."
    return err.message
}