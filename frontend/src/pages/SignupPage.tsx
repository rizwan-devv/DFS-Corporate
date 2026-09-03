import { type FormEvent, useEffect, useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { api, type PartyType } from '../lib/api';
import { useAuth } from '../auth/AuthContext';

type PartyTypeOption = { code: PartyType; label: string };

export function SignupPage() {
  const navigate = useNavigate();
  const { session } = useAuth();
  const [types, setTypes] = useState<PartyTypeOption[]>([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [form, setForm] = useState({
    partyType: 'MERCHANT' as PartyType,
    fullName: '',
    businessName: '',
    email: '',
    phone: '',
  });

  useEffect(() => {
    api<PartyTypeOption[]>('/api/party-types')
      .then((data) => {
        setTypes(data.length ? data : [{ code: 'MERCHANT', label: 'Corporate (Master Wallet)' }]);
        if (data[0]?.code) setForm((f) => ({ ...f, partyType: data[0].code }));
      })
      .catch(() =>
        setTypes([{ code: 'MERCHANT', label: 'Corporate (Master Wallet)' }]),
      );
  }, []);

  if (session?.role === 'PLATFORM_ADMIN') return <Navigate to="/admin" replace />;
  if (session) return <Navigate to="/dashboard" replace />;

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const res = await api<{ email: string; devOtpHint?: string }>('/api/auth/signup', {
        method: 'POST',
        body: JSON.stringify(form),
      });
      navigate('/verify-otp', {
        state: { email: res.email, devOtpHint: res.devOtpHint },
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Signup failed');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="page">
      <div className="container">
        <div className="panel">
          <div className="badge">CORPORATE MASTER</div>
          <h2 style={{ marginTop: 0 }}>Register corporate (master wallet)</h2>
          <p className="muted">
            Master corporate onboarding only. Franchises / child wallets join via a secure invite
            link from the parent — not by entering a parent ID here.
          </p>
          {error && <div className="alert alert-error">{error}</div>}
          <form className="form-grid" onSubmit={onSubmit}>
            {types.length > 1 && (
              <div className="form-row">
                <label>Account type</label>
                <select
                  value={form.partyType}
                  onChange={(e) => setForm({ ...form, partyType: e.target.value as PartyType })}
                >
                  {types.map((t) => (
                    <option key={t.code} value={t.code}>{t.label}</option>
                  ))}
                </select>
              </div>
            )}
            <div className="form-row">
              <label>Authorized person full name</label>
              <input required value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} />
            </div>
            <div className="form-row">
              <label>Legal / business name</label>
              <input required value={form.businessName} onChange={(e) => setForm({ ...form, businessName: e.target.value })} />
            </div>
            <div className="form-row">
              <label>Email</label>
              <input required type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
            </div>
            <div className="form-row">
              <label>Mobile number</label>
              <input required value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
            </div>
            <div className="actions">
              <button className="btn btn-primary" disabled={loading} type="submit">
                {loading ? 'Creating…' : 'Send OTP'}
              </button>
              <Link className="btn btn-ghost" to="/login">Already approved? Login</Link>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}
