import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useSession } from '../auth/SessionContext';
import { login, openInvite } from '../lib/api';

export function LoginPage() {
  const { setSession, session } = useSession();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const [phone, setPhone] = useState('');
  const [pin, setPin] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const inviteToken = params.get('token');

  useEffect(() => {
    if (session?.status === 'KYC_COMPLETED') {
      navigate('/done', { replace: true });
      return;
    }
    if (session?.sessionToken && session.status !== 'KYC_COMPLETED') {
      navigate('/flow', { replace: true });
    }
  }, [session, navigate]);

  useEffect(() => {
    if (!inviteToken) return;
    let cancelled = false;
    setLoading(true);
    setError('');
    openInvite(inviteToken)
      .then((s) => {
        if (cancelled) return;
        setSession(s);
        navigate(s.status === 'KYC_COMPLETED' ? '/done' : '/flow', { replace: true });
      })
      .catch((e) => {
        if (!cancelled) setError(e instanceof Error ? e.message : 'Invalid invite link');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [inviteToken, setSession, navigate]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const s = await login(phone, pin);
      setSession(s);
      navigate(s.status === 'KYC_COMPLETED' ? '/done' : '/flow', { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="shell">
      <div className="brand-row animate-in">
        <div className="brand-mark" aria-hidden><span /></div>
        <span className="brand-text">DFS CORPORATE</span>
      </div>

      <header className="hero-block animate-in animate-in-delay-1">
        <span className="badge">PARTNER KYC</span>
        <h1>Identity check</h1>
        <p className="sub">Biometric / video verification for your entity application.</p>
      </header>

      {error && <div className="alert error">{error}</div>}
      {inviteToken && loading && <p className="loading-line">Opening secure invite…</p>}

      {!inviteToken && (
        <form className="card animate-in animate-in-delay-2" onSubmit={onSubmit}>
          <label>
            Phone (user ID)
            <input
              inputMode="tel"
              autoComplete="tel"
              required
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              placeholder="03XXXXXXXXX"
            />
          </label>
          <label>
            Temporary PIN
            <input
              inputMode="numeric"
              autoComplete="one-time-code"
              required
              value={pin}
              onChange={(e) => setPin(e.target.value)}
              placeholder="6-digit PIN from email"
            />
          </label>
          <button className="btn btn-primary" type="submit" disabled={loading}>
            {loading ? 'Signing in…' : 'Continue'}
          </button>
          <p className="hint">Use the phone + PIN from your invite email, or open the email deep link.</p>
        </form>
      )}
    </div>
  );
}
