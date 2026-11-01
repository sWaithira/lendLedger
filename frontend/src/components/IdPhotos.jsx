import { useRef, useState } from 'react'
import { api, describeError } from '../api.js'
import { formatDate, todayIso } from '../format.js'
import { useFetch } from '../hooks.js'
import { shrinkImage } from '../imageResize.js'
import { Badge, Button, ErrorNote, Field, Loading } from './ui.jsx'

const STATUS = {
    NONE: { label: 'No ID photos', tone: 'gray' },
    ON_FILE: { label: 'On file', tone: 'orange' },
    VERIFIED: { label: 'Verified', tone: 'green' },
}

/** Front and back of the borrower's ID, with the lender's own verification and the consent date. */
export default function IdPhotos({ borrowerId, canWrite }) {
    const base = `/api/v1/borrowers/${borrowerId}/id`
    const { data, error, reload } = useFetch(base)
    const [version, setVersion] = useState(0)
    const [busy, setBusy] = useState('')
    const [message, setMessage] = useState('')
    const [removing, setRemoving] = useState('')
    const [consent, setConsent] = useState(null)
    const frontRef = useRef(null)
    const backRef = useRef(null)
    const refs = { FRONT: frontRef, BACK: backRef }

    async function run(key, call) {
        setBusy(key)
        setMessage('')
        try {
            await call()
            setVersion((v) => v + 1)
            setRemoving('')
            reload()
        } catch (err) {
            setMessage(describeError(err))
        } finally {
            setBusy('')
        }
    }

    function upload(side, file) {
        if (!file) return
        run(side, async () => {
            const blob = await shrinkImage(file)
            const form = new FormData()
            form.append('file', blob, `${side.toLowerCase()}.jpg`)
            await api.upload(`${base}/${side.toLowerCase()}`, form)
        })
    }

    if (error) return <ErrorNote error={error} onRetry={reload} />
    if (!data) return <Loading rows={1} />

    const status = STATUS[data.status] || STATUS.NONE
    const consentValue = consent ?? data.consentOn ?? ''

    return (
        <div className="card">
            <div className="card-top">
                <h3 className="section-title" style={{ margin: 0 }}>
                    ID status
                </h3>
                <Badge tone={status.tone}>{status.label}</Badge>
            </div>
            {data.status === 'VERIFIED' && (
                <p className="muted">
                    Verified by {data.verifiedByName || 'you'}
                    {data.verifiedAt ? ` on ${formatDate(data.verifiedAt.slice(0, 10))}` : ''}
                </p>
            )}
            {message && (
                <div className="notice error" role="alert">
                    {message}
                </div>
            )}

            <div className="pair" style={{ marginTop: 12 }}>
                {[
                    ['FRONT', 'Front', data.front],
                    ['BACK', 'Back', data.back],
                ].map(([side, label, has]) => {
                    const url = `${base}/${side.toLowerCase()}/image?v=${version}`
                    return (
                        <div key={side}>
                            <strong>{label}</strong>
                            {has ? (
                                <div style={{ margin: '8px 0' }}>
                                    <a href={url} target="_blank" rel="noreferrer">
                                        <img src={url} alt={`${label} of the ID`} style={{ maxWidth: '100%', maxHeight: 180, borderRadius: 12 }} />
                                    </a>
                                </div>
                            ) : (
                                <p className="muted">No photo</p>
                            )}
                            {canWrite && (
                                <div className="card-actions">
                                    <input
                                        ref={refs[side]}
                                        type="file"
                                        accept="image/*"
                                        capture="environment"
                                        hidden
                                        onChange={(e) => {
                                            upload(side, e.target.files[0])
                                            e.target.value = ''
                                        }}
                                    />
                                    <Button variant="secondary" busy={busy === side} onClick={() => refs[side].current?.click()}>
                                        {has ? 'Replace' : `Add ${label.toLowerCase()}`}
                                    </Button>
                                    {has && removing !== side && (
                                        <Button variant="danger" onClick={() => setRemoving(side)}>
                                            Remove
                                        </Button>
                                    )}
                                    {has && removing === side && (
                                        <>
                                            <Button variant="danger" busy={busy === `del${side}`} onClick={() => run(`del${side}`, () => api.delete(`${base}/${side.toLowerCase()}`))}>
                                                Yes, remove
                                            </Button>
                                            <Button variant="secondary" onClick={() => setRemoving('')}>
                                                Cancel
                                            </Button>
                                        </>
                                    )}
                                </div>
                            )}
                        </div>
                    )
                })}
            </div>

            {canWrite && data.status === 'ON_FILE' && (
                <div style={{ marginTop: 16 }}>
                    <Button busy={busy === 'verify'} onClick={() => run('verify', () => api.post(`${base}/verify`))}>
                        Verified by me
                    </Button>
                </div>
            )}

            <div style={{ marginTop: 20 }}>
                {canWrite ? (
                    <>
                        <Field label="Consent on file since" hint="The date the borrower agreed on the loan application.">
                            <input type="date" max={todayIso()} value={consentValue} onChange={(e) => setConsent(e.target.value)} />
                        </Field>
                        <Button
                            variant="secondary"
                            busy={busy === 'consent'}
                            disabled={consentValue === (data.consentOn || '')}
                            onClick={() => run('consent', () => api.post(`${base}/consent`, { consentOn: consentValue || null }))}
                        >
                            Save consent date
                        </Button>
                    </>
                ) : (
                    data.consentOn && <p className="muted">Consent on file since {formatDate(data.consentOn)}</p>
                )}
            </div>
        </div>
    )
}