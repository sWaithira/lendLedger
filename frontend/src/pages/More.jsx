import { Link } from 'react-router-dom'
import { useAuth } from '../auth.jsx'
import { Button, PageHead } from '../components/ui.jsx'

/** The phone bottom bar holds five items; the rest live here. */
export default function More() {
    const { user, can, logout } = useAuth()
    const items = [
        can('REPORT_VIEW') && { to: '/performance', title: 'Performance', sub: 'How the business did over any months' },
        can('LOAN_CREATE') && { to: '/import', title: 'Import from Excel', sub: 'Add many borrowers and loans at once' },
        can('SETTINGS_MANAGE') && { to: '/settings', title: 'Settings', sub: 'Lending rules and products' },
    ].filter(Boolean)

    return (
        <>
            <PageHead title="More" subtitle={user.name} />
            {items.length > 0 && (
                <div className="list">
                    {items.map((i) => (
                        <Link key={i.to} to={i.to} className="row">
                            <div className="grow">
                                <div className="title">{i.title}</div>
                                <div className="sub">{i.sub}</div>
                            </div>
                            <span className="chev" aria-hidden="true">
                                ›
                            </span>
                        </Link>
                    ))}
                </div>
            )}
            <div style={{ marginTop: 24 }}>
                <Button variant="secondary" onClick={logout}>
                    Sign out
                </Button>
            </div>
        </>
    )
}