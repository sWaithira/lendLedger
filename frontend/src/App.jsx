import { Link, Navigate, NavLink, Outlet, Route, Routes, useLocation } from 'react-router-dom'
import { useAuth } from './auth.jsx'
import { Icon } from './components/ui.jsx'
import { ROLE_LABEL } from './format.js'
import Login from './pages/Login.jsx'
import Register from './pages/Register.jsx'
import Settings from './pages/Settings.jsx'
import ImportData from './pages/ImportData.jsx'
import Dashboard from './pages/Dashboard.jsx'
import Performance from './pages/Performance.jsx'
import Platform from './pages/Platform.jsx'
import Profile from './pages/Profile.jsx'
import Reports from './pages/Reports.jsx'
import Overdue from './pages/Overdue.jsx'
import Borrowers from './pages/Borrowers.jsx'
import BorrowerDetail from './pages/BorrowerDetail.jsx'
import Loans from './pages/Loans.jsx'
import LoanNew from './pages/LoanNew.jsx'
import LoanDetail from './pages/LoanDetail.jsx'

const LABELS = {
    dashboard: 'Overview',
    borrowers: 'Borrowers',
    loans: 'Loans',
    overdue: 'Overdue',
    performance: 'Performance',
    reports: 'Reports',
    settings: 'Settings',
    import: 'Import from Excel',
    profile: 'Profile',
    platform: 'Platform',
}
// These pages are reached from the Profile page.
const PARENT = { settings: 'profile', import: 'profile', reports: 'profile' }

/** The top bar: where you are, built from the address. */
function Breadcrumbs() {
    const { pathname } = useLocation()
    const parts = pathname.split('/').filter(Boolean)
    const first = parts[0] || 'dashboard'
    if (!PARENT[first]) return null
    const trail = []
    if (PARENT[first]) trail.push({ label: LABELS[PARENT[first]], to: `/${PARENT[first]}` })
    trail.push({ label: LABELS[first] || 'Page', to: `/${first}` })
    if (parts[1]) {
        const label = parts[1] === 'new' ? 'New loan' : first === 'borrowers' ? 'Borrower' : 'Loan'
        trail.push({ label })
    }
    return (
        <nav className="crumbs" aria-label="Breadcrumb">
            <ol>
                {trail.map((c, i) => (
                    <li key={c.label}>
                        {i < trail.length - 1 ? <Link to={c.to}>{c.label}</Link> : <span aria-current="page">{c.label}</span>}
                    </li>
                ))}
            </ol>
        </nav>
    )
}

function Shell() {
    const { user, logout, can } = useAuth()
    const platform = can('PLATFORM_MANAGE')
    return (
        <div className="shell">
            <aside className="sidebar">
                <div className="brand">LendLedger</div>
                <nav className="nav" aria-label="Main">
                    {platform ? (
                        <>
                            <NavLink to="/platform">
                                <Icon name="gear" /> Platform
                            </NavLink>
                            <NavLink to="/profile">
                                <Icon name="user" /> Profile
                            </NavLink>
                        </>
                    ) : (
                        <>
                            <NavLink to="/dashboard">
                                <Icon name="chart" /> Overview
                            </NavLink>
                            <NavLink to="/borrowers">
                                <Icon name="people" /> Borrowers
                            </NavLink>
                            <NavLink to="/loans">
                                <Icon name="loans" /> Loans
                            </NavLink>
                            <NavLink to="/overdue">
                                <Icon name="alert" /> Overdue
                            </NavLink>
                            {can('REPORT_VIEW') && (
                                <NavLink className="nav-desktop" to="/performance">
                                    <Icon name="chart" /> Performance
                                </NavLink>
                            )}
                            <NavLink to="/profile">
                                <Icon name="user" /> Profile
                            </NavLink>
                        </>
                    )}
                </nav>
                <div className="me">
                    <Link to="/profile">
                        <strong>{user.name}</strong>
                    </Link>
                    <small>{ROLE_LABEL[user.role] || user.role}</small>
                    <button className="btn plain me-signout" type="button" onClick={logout}>
                        Sign out
                    </button>
                </div>
            </aside>
            <main className="content">
                <Breadcrumbs />
                <Outlet />
            </main>
        </div>
    )
}

function RequireAuth() {
    const { user, loading } = useAuth()
    const location = useLocation()
    if (loading) return <div className="signin" aria-busy="true" />
    if (!user) return <Navigate to="/login" replace state={{ from: location }} />
    if (user.mustChangePassword && location.pathname !== '/profile') return <Navigate to="/profile" replace />
    return <Shell />
}

/** Where "/" goes: the operator to the console, everyone else to the overview. */
function Home() {
    const { can } = useAuth()
    return <Navigate to={can('PLATFORM_MANAGE') ? '/platform' : '/dashboard'} replace />
}

function NotFound() {
    return (
        <div className="signin">
            <div className="panel">
                <h1>Page not found</h1>
                <p className="lede">That address doesn’t exist.</p>
                <Link className="btn primary" to="/">
                    Go home
                </Link>
            </div>
        </div>
    )
}

export default function App() {
    return (
        <Routes>
            <Route path="/login" element={<Login />} />
            <Route path="/register" element={<Register />} />
            <Route element={<RequireAuth />}>
                <Route index element={<Home />} />
                <Route path="dashboard" element={<Dashboard />} />
                <Route path="performance" element={<Performance />} />
                <Route path="overdue" element={<Overdue />} />
                <Route path="borrowers" element={<Borrowers />} />
                <Route path="borrowers/:id" element={<BorrowerDetail />} />
                <Route path="loans" element={<Loans />} />
                <Route path="loans/new" element={<LoanNew />} />
                <Route path="loans/:id" element={<LoanDetail />} />
                <Route path="settings" element={<Settings />} />
                <Route path="import" element={<ImportData />} />
                <Route path="reports" element={<Reports />} />
                <Route path="more" element={<Navigate to="/profile" replace />} />
                <Route path="profile" element={<Profile />} />
                <Route path="platform" element={<Platform />} />
            </Route>
            <Route path="*" element={<NotFound />} />
        </Routes>
    )
}