import { type FormEvent, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { api } from '../lib/api';
import { useAuth } from '../auth/AuthContext';

export function VerifyOtpPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const { setSession } = useAuth();
  const state = (location.state || {}) as { email?: string; devOtpHint?: string };
  const [email, setEmail] = useState(state.email || '');
  const [code, setCode] = useState(state.devOtpHint || '');
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
        partyStatus: string;
        partyType: string;
        partyPublicId: string;
      }>('/api/auth/verify-otp', {
        method: 'POST',
        body: JSON.stringify({ email, code }),
      });
      setSession({
        token: res.token,
        role: res.role,
        partyStatus: res.partyStatus,
        partyType: res.partyType,
        partyPublicId: res.partyPublicId,
      });
      navigate('/onboarding');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Verification failed');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="page">
      <div className="container">
        <div className="panel panel--auth">
          <div className="badge">EMAIL VERIFICATION</div>
          <h2 className="panel-title">Enter OTP</h2>
          {state.devOtpHint && (
            <div className="alert alert-info">
              Local mode: OTP is <strong>{state.devOtpHint}</strong> (also in backend logs).
            </div>
          )}
          {error && <div className="alert alert-error">{error}</div>}
          <form className="form-grid" onSubmit={onSubmit}>
            <div className="form-row">
              <label>Email</label>
              <input required type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
            </div>
            <div className="form-row">
              <label>OTP code</label>
              <input required value={code} onChange={(e) => setCode(e.target.value)} placeholder="6-digit code" />
            </div>
            <button className="btn btn-primary" disabled={loading} type="submit">
              {loading ? 'Verifying…' : 'Verify & Continue'}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}
