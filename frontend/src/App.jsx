import { Link, Navigate, NavLink, Outlet, Route, Routes, useLocation } from 'react-router-dom'
import { useAuth } from './auth.jsx'
import { Icon } from './components/ui.jsx'
import { ROLE_LABEL } from './format.js'
import Login from './pages/Login.jsx'
import Register from './pages/Register.jsx'
import Settings from './pages/Settings.jsx'
import Dashboard from './pages/Dashboard.jsx'
import Overdue from './pages/Overdue.jsx'
import Borrowers from './pages/Borrowers.jsx'
import BorrowerDetail from './pages/BorrowerDetail.jsx'
import Loans from './pages/Loans.jsx'
import LoanNew from './pages/LoanNew.jsx'
import LoanDetail from './pages/LoanDetail.jsx'

function Shell() {
    const { user, logout, can } = useAuth()
    return (
        <div className="shell">
            <aside className="sidebar">
                <div className="brand">LendLedger</div>
                <nav className="nav" aria-label="Main">
                    <NavLink to="/dashboard">
                        <Icon name="chart" /> Overview
                    </NavLink>
                    <NavLink to="/overdue">
                        <Icon name="alert" /> Overdue
                    </NavLink>
                    <NavLink to="/borrowers">
                        <Icon name="people" /> Borrowers
                    </NavLink>
                    <NavLink to="/loans">
                        <Icon name="loans" /> Loans
                    </NavLink>
                    {can('SETTINGS_MANAGE') && (
                        <NavLink to="/settings">
                            <Icon name="gear" /> Settings
                        </NavLink>
                    )}
                </nav>
                <div className="me">
                    <strong>{user.name}</strong>
                    <small>{ROLE_LABEL[user.role] || user.role}</small>
                    <button className="btn plain" type="button" onClick={logout}>
                        Sign out
                    </button>
                </div>
            </aside>
            <main className="content">
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
    return <Shell />
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
                <Route index element={<Navigate to="/dashboard" replace />} />
                <Route path="dashboard" element={<Dashboard />} />
                <Route path="overdue" element={<Overdue />} />
                <Route path="borrowers" element={<Borrowers />} />
                <Route path="borrowers/:id" element={<BorrowerDetail />} />
                <Route path="loans" element={<Loans />} />
                <Route path="loans/new" element={<LoanNew />} />
                <Route path="loans/:id" element={<LoanDetail />} />
                <Route path="settings" element={<Settings />} />
            </Route>
            <Route path="*" element={<NotFound />} />
        </Routes>
    )
}