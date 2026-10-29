import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api, describeError } from '../api.js'
import { Badge, Button, Field, PageHead } from '../components/ui.jsx'

const STATUS = {
    NEW: { label: 'Will be added', tone: 'green' },
    CREATED: { label: 'Added', tone: 'green' },
    EXISTING: { label: 'Already yours', tone: 'gray' },
    SKIPPED: { label: 'Skipped', tone: 'gray' },
    ERROR: { label: 'Fix this', tone: 'red' },
}
// Problems first, so nothing important is buried in a long list.
const ORDER = { ERROR: 0, NEW: 1, CREATED: 1, SKIPPED: 2, EXISTING: 3 }
const SHOWN = 50

/** Upload an Excel file, check every row, confirm, then see a report. Nothing is saved until you confirm. */
export default function ImportData() {
    const [file, setFile] = useState(null)
    const [fileKey, setFileKey] = useState(0)
    const [preview, setPreview] = useState(null)
    const [report, setReport] = useState(null)
    const [error, setError] = useState('')
    const [busy, setBusy] = useState(false)
    const [showAll, setShowAll] = useState(false)

    function reset() {
        setFile(null)
        setPreview(null)
        setReport(null)
        setError('')
        setShowAll(false)
        setFileKey((k) => k + 1)
    }

    function chooseFile(e) {
        setFile(e.target.files?.[0] || null)
        setPreview(null)
        setReport(null)
        setError('')
    }

    function send(path) {
        const form = new FormData()
        form.append('file', file)
        return api.upload(path, form)
    }

    async function check(e) {
        e.preventDefault()
        if (!file) return
        setBusy(true)
        setError('')
        try {
            setPreview(await send('/api/v1/imports/preview'))
            setShowAll(false)
        } catch (err) {
            setError(describeError(err))
        } finally {
            setBusy(false)
        }
    }

    async function confirm() {
        setBusy(true)
        setError('')
        try {
            setReport(await send('/api/v1/imports/confirm'))
            setPreview(null)
            setShowAll(false)
        } catch (err) {
            setError(describeError(err))
        } finally {
            setBusy(false)
        }
    }

    const result = report || preview
    const s = result?.summary
    const rows = result ? [...result.rows].sort((a, b) => (ORDER[a.status] ?? 9) - (ORDER[b.status] ?? 9)) : []
    const visible = showAll ? rows : rows.slice(0, SHOWN)
    const toAdd = preview ? preview.summary.borrowersNew + preview.summary.loansNew : 0

    return (
        <>
            <Link className="back" to="/borrowers">
                ‹ Borrowers
            </Link>
            <PageHead title="Import from Excel" subtitle="Add many borrowers and loans at once. You check everything before anything is saved." />

            {error && (
                <div className="notice error" role="alert">
                    {error}
                </div>
            )}

            {!report && (
                <form className="card" onSubmit={check}>
                    <p style={{ marginTop: 0 }}>
                        <a className="btn secondary" href="/api/v1/imports/template" download>
                            Download the template
                        </a>
                    </p>
                    <Field label="Your filled-in file (.xlsx)">
                        <input key={fileKey} type="file" accept=".xlsx" onChange={chooseFile} />
                    </Field>
                    <Button type="submit" busy={busy} disabled={!file}>
                        Check file
                    </Button>
                </form>
            )}

            {s && (
                <>
                    <h2 className="section-title">{report ? 'Import finished' : 'What we found'}</h2>
                    <div className="tiles">
                        <div className="tile">
                            <small>{report ? 'Borrowers added' : 'New borrowers'}</small>
                            <strong className="money">{s.borrowersNew}</strong>
                        </div>
                        <div className="tile">
                            <small>{report ? 'Loans added' : 'New loans'}</small>
                            <strong className="money">{s.loansNew}</strong>
                        </div>
                        <div className="tile">
                            <small>Already yours</small>
                            <strong className="money">{s.borrowersExisting}</strong>
                        </div>
                        <div className="tile">
                            <small>Skipped</small>
                            <strong className="money">{s.borrowersSkipped + s.loansSkipped}</strong>
                        </div>
                        <div className={`tile ${s.errors > 0 ? 'alert' : ''}`}>
                            <small>Need fixing</small>
                            <strong className="money">{s.errors}</strong>
                        </div>
                    </div>

                    {preview?.alreadyImported && (
                        <div className="notice error" role="alert">
                            This exact file has already been imported. Change it or choose another file.
                        </div>
                    )}
                    {preview && !preview.alreadyImported && toAdd === 0 && (
                        <div className="notice info">There is nothing new to add from this file.</div>
                    )}
                    {preview && !preview.alreadyImported && toAdd > 0 && (
                        <div className="card">
                            <Button busy={busy} onClick={confirm}>
                                Add {preview.summary.borrowersNew} borrower{preview.summary.borrowersNew === 1 ? '' : 's'} and{' '}
                                {preview.summary.loansNew} loan{preview.summary.loansNew === 1 ? '' : 's'}
                            </Button>
                            {preview.summary.errors > 0 && (
                                <p className="muted" style={{ marginTop: 12, fontSize: '0.875rem' }}>
                                    Rows that need fixing are skipped. Fix them in Excel and upload again; only the new rows will be added.
                                </p>
                            )}
                        </div>
                    )}

                    <div className="list">
                        {visible.map((r) => {
                            const st = STATUS[r.status] || { label: r.status, tone: 'gray' }
                            const detail = r.status === 'NEW' || r.status === 'CREATED' ? '' : ` · ${r.message}`
                            return (
                                <div key={`${r.sheet}-${r.row}`} className="row static">
                                    <div className="grow">
                                        <div className="title">{r.label}</div>
                                        <div className="sub">
                                            {r.sheet} · row {r.row}
                                            {detail}
                                        </div>
                                    </div>
                                    <Badge tone={st.tone}>{st.label}</Badge>
                                </div>
                            )
                        })}
                    </div>
                    {rows.length > SHOWN && !showAll && (
                        <Button variant="plain" onClick={() => setShowAll(true)}>
                            Show all {rows.length} rows
                        </Button>
                    )}

                    <div className="card downloads" style={{ marginTop: 16 }}>
                        {report && (
                            <>
                                <Link className="btn primary" to="/borrowers">
                                    View borrowers
                                </Link>
                                <Link className="btn secondary" to="/loans">
                                    View loans
                                </Link>
                            </>
                        )}
                        <Button variant="secondary" onClick={reset}>
                            {report ? 'Import another file' : 'Choose a different file'}
                        </Button>
                    </div>
                </>
            )}
        </>
    )
}