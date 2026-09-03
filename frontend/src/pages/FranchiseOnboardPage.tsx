import { type FormEvent, useEffect, useState } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { api } from '../lib/api';
import { useAuth } from '../auth/AuthContext';

type InviteInfo = {
  token: string;
  usable: boolean;
  message?: string;
  status?: string;
  contactName?: string;
  email?: string;
  phone?: string;
  businessName?: string;
  entityType?: string;
  commissionRatePercent?: number;
  commissionType?: string;
  parentBusinessName?: string;
  parentTrackingId?: string;
  expiresAt?: string;
};

export function FranchiseOnboardPage() {
  const [params] = useSearchParams();
  const token = params.get('token')?.trim() || '';
  const navigate = useNavigate();
  const { session } = useAuth();
  const [invite, setInvite] = useState<InviteInfo | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [form, setForm] = useState({
    fullName: '',
    businessName: '',
    email: '',
    phone: '',
  });

  useEffect(() => {
    if (!token) {
      setLoading(false);
      setError('Missing invite token. Open the link from your invitation email.');
      return;
    }
    let cancelled = false;
    setLoading(true);
    api<InviteInfo>(`/api/public/franchise-invite/${encodeURIComponent(token)}`)
      .then((data) => {
        if (cancelled) return;
        setInvite(data);
        setForm({
          fullName: data.contactName || '',
          businessName: data.businessName || '',
          email: data.email || '',
          phone: data.phone || '',
        });
        if (!data.usable) {
          setError(data.message || 'This invite cannot be used.');
        }
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Invalid invite link');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [token]);

  if (session?.role === 'PLATFORM_ADMIN') return <Navigate to="/admin" replace />;
  if (session) return <Navigate to="/dashboard" replace />;

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!invite?.usable || !token) return;
    setError('');
    setSubmitting(true);
    try {
      const res = await api<{ email: string; devOtpHint?: string }>('/api/auth/signup', {
        method: 'POST',
        body: JSON.stringify({
          partyType: 'SUB_MERCHANT',
          fullName: form.fullName,
          businessName: form.businessName || form.fullName,
          email: form.email,
          phone: form.phone,
          franchiseInviteToken: token,
        }),
      });
      navigate('/verify-otp', {
        state: { email: res.email, devOtpHint: res.devOtpHint },
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Signup failed');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="page">
      <div className="container">
        <div className="panel">
          <div className="badge">FRANCHISE / CHILD WALLET</div>
          <h2 style={{ marginTop: 0 }}>Join under your corporate parent</h2>
          <p className="muted">
            Parent is already linked via this invite — you do not enter a parent ID.
          </p>

          {loading && <p className="muted">Validating invite…</p>}
          {error && <div className="alert alert-error">{error}</div>}

          {invite && (
            <div className="ops-meta-grid" style={{ margin: '1rem 0 1.25rem' }}>
              <div>
                <span className="muted">Parent corporate</span>
                <strong>{invite.parentBusinessName || '—'}</strong>
              </div>
              <div>
                <span className="muted">Parent tracking</span>
                <strong>{invite.parentTrackingId || '—'}</strong>
              </div>
              {invite.commissionRatePercent != null && (
                <div>
                  <span className="muted">Proposed commission</span>
                  <strong>{invite.commissionRatePercent}% {invite.commissionType || ''}</strong>
                </div>
              )}
              <div>
                <span className="muted">Invite status</span>
                <strong>{invite.status || '—'}</strong>
              </div>
            </div>
          )}

          {invite?.usable && (
            <form className="form-grid" onSubmit={onSubmit}>
              <div className="form-row">
                <label>Authorized person full name</label>
                <input
                  required
                  value={form.fullName}
                  onChange={(e) => setForm({ ...form, fullName: e.target.value })}
                />
              </div>
              <div className="form-row">
                <label>Franchise / outlet business name</label>
                <input
                  required
                  value={form.businessName}
                  onChange={(e) => setForm({ ...form, businessName: e.target.value })}
                />
              </div>
              <div className="form-row">
                <label>Email</label>
                <input
                  required
                  type="email"
                  value={form.email}
                  onChange={(e) => setForm({ ...form, email: e.target.value })}
                />
              </div>
              <div className="form-row">
                <label>Mobile (app user ID)</label>
                <input
                  required
                  value={form.phone}
                  onChange={(e) => setForm({ ...form, phone: e.target.value })}
                />
              </div>
              <div className="actions">
                <button className="btn btn-primary" disabled={submitting} type="submit">
                  {submitting ? 'Creating…' : 'Send OTP & continue'}
                </button>
                <Link className="btn btn-ghost" to="/login">Already registered? Login</Link>
              </div>
            </form>
          )}

          {!loading && !invite?.usable && (
            <div className="actions" style={{ marginTop: '1rem' }}>
              <Link className="btn btn-ghost" to="/login">Go to login</Link>
              <Link className="btn btn-ghost" to="/">Home</Link>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
