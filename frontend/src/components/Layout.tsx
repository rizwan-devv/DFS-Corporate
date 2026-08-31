import { Link, NavLink, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { Footer } from './Footer';

export function Layout() {
  const { session, logout } = useAuth();
  const location = useLocation();
  const opsMode = location.pathname.startsWith('/admin') && session?.role === 'PLATFORM_ADMIN';
  const isMerchant = !!session && session.role !== 'PLATFORM_ADMIN';
  const canStartOnboarding = !session;

  return (
    <div className={`app-shell${opsMode ? ' ops-mode' : ''}`}>
      <header className="nav">
        <div className={`nav-inner ${opsMode ? 'ops-shell' : 'container'}`} style={opsMode ? { width: 'min(1440px, calc(100% - 2rem))', margin: '0 auto' } : undefined}>
          <Link to={opsMode ? '/admin' : '/'} className="brand">
            <div className="brand-mark"><span /></div>
            <span className="brand-text">{opsMode ? 'DFS BACKOFFICE' : 'DFS CORPORATE'}</span>
          </Link>
          <nav className="nav-links" aria-label="Main">
            {!opsMode && (
              <>
                <NavLink to="/" end>Home</NavLink>
                <NavLink to="/getting-started">Getting Started</NavLink>
                {canStartOnboarding && <NavLink to="/signup">Onboard</NavLink>}
              </>
            )}
            {session?.role === 'PLATFORM_ADMIN' && <NavLink to="/admin">Backoffice</NavLink>}
            {isMerchant && <NavLink to="/dashboard">Dashboard</NavLink>}
            {isMerchant && session?.partyStatus !== 'ACTIVE' && (
              <NavLink to="/onboarding">My Application</NavLink>
            )}
            {session && <NavLink to="/profile">Profile</NavLink>}
          </nav>
          <div className="nav-actions">
            {session ? (
              <>
                <Link
                  to="/profile"
                  className="nav-user nav-user-link"
                  title="Open profile"
                >
                  {session.fullName || session.role}
                </Link>
                <button className="btn btn-ghost btn-sm" type="button" onClick={logout}>
                  Logout
                </button>
              </>
            ) : (
              <Link className="btn btn-primary btn-sm" to="/login">Login</Link>
            )}
          </div>
        </div>
        {!opsMode && (
          <nav className="nav-mobile container" aria-label="Mobile">
            <NavLink to="/" end>Home</NavLink>
            <NavLink to="/getting-started">Start</NavLink>
            {canStartOnboarding && <NavLink to="/signup">Onboard</NavLink>}
            {session?.role === 'PLATFORM_ADMIN' && <NavLink to="/admin">Backoffice</NavLink>}
            {isMerchant && <NavLink to="/dashboard">Dashboard</NavLink>}
            {isMerchant && session?.partyStatus !== 'ACTIVE' && (
              <NavLink to="/onboarding">Application</NavLink>
            )}
            {session && <NavLink to="/profile">Profile</NavLink>}
          </nav>
        )}
      </header>

      <main className="app-main">
        <Outlet />
      </main>

      {!opsMode && <Footer />}
    </div>
  );
}
