import { type FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../lib/api';
import { useAuth } from '../auth/AuthContext';
import { BRAND } from '../lib/brand';

export function LoginPage() {
  const { setSession } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const res = await api<{
        token: string;
        role: string;
        portalRoles?: string[];
        partyStatus: string;
        partyType: string;
        partyPublicId: string;
        fullName: string;
        firstLogin?: boolean;
      }>('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({ email, password }),
      });
      setSession({
        token: res.token,
        role: res.role,
        portalRoles: res.portalRoles || [],
        partyStatus: res.partyStatus,
        partyType: res.partyType,
        partyPublicId: res.partyPublicId,
        fullName: res.fullName,
        firstLogin: !!res.firstLogin,
      });
      if (res.role === 'PLATFORM_ADMIN') {
        navigate('/admin');
      } else if (res.firstLogin) {
        navigate('/change-password');
      } else if (res.partyStatus === 'DRAFT' || res.partyStatus === 'REJECTED') {
        navigate('/onboarding');
      } else {
        navigate('/dashboard');
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="page page--auth">
      <div className="container">
        <div className="panel panel--auth">
          <div className="auth-brand">
            <div className="brand-mark" aria-hidden><span /></div>
            <div>
              <div className="brand-text">{BRAND.short}</div>
              <div className="portal-brand-sub">{BRAND.tagline}</div>
            </div>
          </div>
          <div className="badge">SECURE ACCESS</div>
          <h2 className="panel-title">Login</h2>
          <p className="muted">
            After you submit your application, use the temporary password emailed to you.
            You will be asked to change it on first login.
          </p>
          {error && <div className="alert alert-error">{error}</div>}
          <form className="form-grid" onSubmit={onSubmit}>
            <div className="form-row">
              <label>Email</label>
              <input required type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
            </div>
            <div className="form-row">
              <label>Password</label>
              <input required type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
            </div>
            <button className="btn btn-primary" disabled={loading} type="submit">
              {loading ? 'Signing in…' : 'Login'}
            </button>
          </form>
          <p className="muted" style={{ marginTop: '1.25rem' }}>
            New applicant? <Link to="/signup" className="link-accent">Start onboarding</Link>
          </p>
          <p className="muted" style={{ fontSize: '0.8rem' }}>
            Dev admin: admin@dfscorporate.local / Admin@123
          </p>
        </div>
      </div>
    </div>
  );
}
