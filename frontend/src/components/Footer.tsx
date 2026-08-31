import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

export function Footer() {
  const { session } = useAuth();
  const year = new Date().getFullYear();
  const loggedIn = !!session;

  return (
    <footer className="site-footer">
      <div className="footer-glow" aria-hidden />
      <div className="container footer-inner">
        <div className="footer-brand">
          <div className="brand">
            <div className="brand-mark"><span /></div>
            DFS CORPORATE
          </div>
          <p className="footer-tagline">
            Corporate entity onboarding aligned with SBP Consolidated Customer Onboarding Framework.
          </p>
        </div>

        <div className="footer-columns">
          <div className="footer-col">
            <h4>Product</h4>
            <Link to="/getting-started">Getting Started</Link>
            {!loggedIn && <Link to="/signup">Onboard</Link>}
            {loggedIn && session.role !== 'PLATFORM_ADMIN' && <Link to="/dashboard">Dashboard</Link>}
            {!loggedIn && <Link to="/login">Login</Link>}
          </div>
          <div className="footer-col">
            <h4>Entities</h4>
            <span>Sole Proprietorship</span>
            <span>Small Business</span>
            <span>Partnership &amp; LLP</span>
          </div>
          <div className="footer-col">
            <h4>Compliance</h4>
            <span>SBP Consolidated Framework</span>
            <span>Annex-C Documentation</span>
            <span>5-day entity TAT</span>
          </div>
        </div>
      </div>

      <div className="container footer-bottom">
        <p className="muted footer-copy">
          © {year} DFS Corporate. For authorized corporate onboarding only.
        </p>
        <p className="muted footer-meta">
          Secured digital channel · Draft save 30 days · Partner KYC via mobile app
        </p>
      </div>
    </footer>
  );
}
