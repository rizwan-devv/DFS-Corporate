import { useEffect, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { api } from '../lib/api';
import { useAuth } from '../auth/AuthContext';

type Party = {
  status: string;
  partyType: string;
  trackingId?: string;
  businessName?: string;
  entityType?: string;
  fullName?: string;
  email?: string;
  phone?: string;
  city?: string;
  country?: string;
  submittedAt?: string;
  approvedAt?: string;
};

function fmt(iso?: string) {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleDateString(undefined, {
      year: 'numeric', month: 'short', day: 'numeric',
    });
  } catch {
    return '—';
  }
}

export function ProfilePage() {
  const { session, logout } = useAuth();
  const [party, setParty] = useState<Party | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const isAdmin = session?.role === 'PLATFORM_ADMIN';

  useEffect(() => {
    if (!session?.token || isAdmin) return;
    let cancelled = false;
    setLoading(true);
    api<Party>('/api/onboarding/me', { token: session.token })
      .then((data) => {
        if (!cancelled) setParty(data);
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Failed to load profile');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [session?.token, isAdmin]);

  if (!session) return <Navigate to="/login" replace />;

  const name = party?.fullName || session.fullName || 'User';
  const initials = name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase() || '')
    .join('') || 'U';

  return (
    <div className="page">
      <div className="container dash">
        <div className="panel panel--wide animate-in">
          <div className="panel-header">
            <div className="profile-head">
              <div className="profile-avatar" aria-hidden>{initials}</div>
              <div>
                <div className="badge">{isAdmin ? 'BACKOFFICE' : 'PROFILE'}</div>
                <h2 style={{ margin: '0 0 0.35rem' }}>{name}</h2>
                <p className="muted" style={{ margin: 0 }}>
                  {isAdmin
                    ? 'Platform administrator account'
                    : 'Merchant portal account & application identity'}
                </p>
              </div>
            </div>
            {!isAdmin && party?.status && (
              <span className={`status status-${party.status}`}>{party.status}</span>
            )}
          </div>

          {error && <div className="alert alert-error alert-spaced">{error}</div>}

          {loading ? (
            <p className="muted" style={{ marginTop: '1.25rem' }}>Loading profile…</p>
          ) : (
            <div className="ops-meta-grid" style={{ marginTop: '1.35rem' }}>
              <div>
                <span className="muted">Full name</span>
                <strong>{name}</strong>
              </div>
              <div>
                <span className="muted">Role</span>
                <strong>{session.role}</strong>
              </div>
              {!isAdmin && (
                <>
                  <div>
                    <span className="muted">Email</span>
                    <strong>{party?.email || '—'}</strong>
                  </div>
                  <div>
                    <span className="muted">Phone</span>
                    <strong>{party?.phone || '—'}</strong>
                  </div>
                  <div>
                    <span className="muted">Business</span>
                    <strong>{party?.businessName || '—'}</strong>
                  </div>
                  <div>
                    <span className="muted">Entity</span>
                    <strong>{party?.entityType || party?.partyType || session.partyType || '—'}</strong>
                  </div>
                  <div>
                    <span className="muted">Tracking ID</span>
                    <strong>{party?.trackingId || '—'}</strong>
                  </div>
                  <div>
                    <span className="muted">Location</span>
                    <strong>
                      {[party?.city, party?.country].filter(Boolean).join(', ') || '—'}
                    </strong>
                  </div>
                  <div>
                    <span className="muted">Submitted</span>
                    <strong>{fmt(party?.submittedAt)}</strong>
                  </div>
                  <div>
                    <span className="muted">Approved</span>
                    <strong>{fmt(party?.approvedAt)}</strong>
                  </div>
                </>
              )}
              {isAdmin && (
                <div>
                  <span className="muted">Access</span>
                  <strong>Multi-brand KYC operations</strong>
                </div>
              )}
            </div>
          )}

          <div className="actions" style={{ marginTop: '1.5rem' }}>
            {!isAdmin && (
              <>
                <Link className="btn btn-primary" to="/dashboard">Dashboard</Link>
                {party?.status !== 'ACTIVE' && session?.partyStatus !== 'ACTIVE' && (
                  <Link className="btn btn-ghost" to="/onboarding">My Application</Link>
                )}
              </>
            )}
            {isAdmin && (
              <Link className="btn btn-primary" to="/admin">Backoffice</Link>
            )}
            <button className="btn btn-ghost" type="button" onClick={logout}>
              Logout
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
