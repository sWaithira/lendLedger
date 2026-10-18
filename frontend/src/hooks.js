import { useEffect, useState } from 'react'
import { api } from './api.js'

/**
 * GET a path and track loading / error. Pass null to stay idle; call reload() to fetch again.
 * While a new request is in flight the previous data stays visible, so lists do not flash on every keystroke.
 */
export function useFetch(path) {
    const [tick, setTick] = useState(0)
    const key = path == null ? null : `${path}#${tick}`
    const [result, setResult] = useState({ key: null, data: null, error: null })

    useEffect(() => {
        if (key == null) return undefined
        let cancelled = false
        api
            .get(path)
            .then((data) => !cancelled && setResult({ key, data, error: null }))
            .catch((error) => !cancelled && setResult({ key, data: null, error }))
        return () => {
            cancelled = true
        }
    }, [key, path])

    const current = result.key === key
    return {
        data: path == null ? null : result.data,
        error: current ? result.error : null,
        loading: key != null && !current,
        reload: () => setTick((t) => t + 1),
    }
}

export function useDebounced(value, ms = 300) {
    const [debounced, setDebounced] = useState(value)
    useEffect(() => {
        const t = setTimeout(() => setDebounced(value), ms)
        return () => clearTimeout(t)
    }, [value, ms])
    return debounced
}