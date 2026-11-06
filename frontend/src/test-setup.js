import { afterEach } from 'vitest'
import { cleanup } from '@testing-library/react'

afterEach(() => cleanup())

if (!globalThis.crypto?.randomUUID) {
    let n = 0
    Object.defineProperty(globalThis, 'crypto', {
        value: { randomUUID: () => `test-key-${++n}-0000-0000-000000000000` },
        configurable: true,
    })
}