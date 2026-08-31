import { useEffect, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { api } from '../lib/api';
import { useAuth } from '../auth/AuthContext';

type AppUser = { id: number; fullName: string; phone: string; status: string };
type Party = {
  status: string;
  partyType: string;
  trackingId?: string;
  businessName?: string;
  entityType?: string;
  fullName?: string;
  email?: string;
  phone?: string;
  submittedAt?: string;
  approvedAt?: string;
  decisionDueAt?: string;
  partnerKycTotal?: number;
  partnerKycCompleted?: number;
  partnerAppUsers?: AppUser[];
  discrepancyNote?: string;
  rejectionReason?: string;
  accountProvisionStatus?: string;
  dfsAccountId?: string;
  accountProvisionError?: string;
  accountProvisionedAt?: string;
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

function statusHint(status?: string) {
  switch (status) {
    case 'DRAFT':
      return 'Continue your entity application when ready.';
    case 'SUBMITTED':
      return 'Waiting for partners to finish mobile app KYC.';
    case 'PENDING_APPROVAL':
      return 'With backoffice for final review (5 working-day TAT).';
    case 'ACTIVE':
      return 'Entity approved. DFS account provisioning status is shown below.';
    case 'REJECTED':
      return 'Application was rejected — open My Application to correct and resubmit.';
    default:
      return 'Track your corporate onboarding status here.';
  }
}

function provisionHint(status?: string) {
  switch (status) {
    case 'NOT_STARTED':
      return 'Account creation has not started yet.';
    case 'PENDING':
      return 'Your DFS account is being created (or waiting for DFS Account API).';
    case 'SUCCESS':
      return 'Your DFS account has been created successfully.';
    case 'FAILED':
      return 'Account creation failed. Backoffice can retry after the DFS API is available.';
    default:
      return '';
  }
}

export function DashboardPage() {
  const { session, setSession } = useAuth();
  const [party, setParty] = useState<Party | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!session?.token || session.role === 'PLATFORM_ADMIN') return;
    let cancelled = false;
    setLoading(true);
    api<Party>('/api/onboarding/me', { token: session.token })
      .then((data) => {
        if (cancelled) return;
        setParty(data);
        if (data.status && data.status !== session.partyStatus) {
          setSession({ ...session, partyStatus: data.status });
        }
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Failed to load application');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [session?.token, session?.role]);

  if (!session) return <Navigate to="/login" replace />;
  if (session.role === 'PLATFORM_ADMIN') return <Navigate to="/admin" replace />;

  const status = party?.status || session.partyStatus || '—';
  const kycTotal = party?.partnerKycTotal || 0;
  const kycDone = party?.partnerKycCompleted || 0;
  const kycPct = kycTotal > 0 ? Math.round((kycDone / kycTotal) * 100) : 0;
  const provision = party?.accountProvisionStatus;
  const showApp = status === 'DRAFT' || status === 'REJECTED' || status === 'SUBMITTED' || status === 'PENDING_APPROVAL';

  return (
    <div className="page">
      <div className="container dash">
        <div className="panel panel--wide animate-in">
          <div className="panel-header">
            <div>
              <div className="badge">MERCHANT PORTAL</div>
              <h2 style={{ margin: '0 0 0.35rem' }}>
                Welcome, {party?.fullName || session.fullName || 'user'}
              </h2>
              <p className="muted" style={{ margin: 0 }}>
                {statusHint(status)}
              </p>
            </div>
            <span className={`status status-${status}`}>{status}</span>
          </div>

          {error && <div className="alert alert-error alert-spaced">{error}</div>}
          {party?.discrepancyNote && (
            <div className="alert alert-info alert-spaced">Discrepancy: {party.discrepancyNote}</div>
          )}
          {party?.rejectionReason && (
            <div className="alert alert-error alert-spaced">Rejected: {party.rejectionReason}</div>
          )}

          {loading ? (
            <p className="muted" style={{ marginTop: '1.25rem' }}>Loading application…</p>
          ) : (
            <>
              <div className="ops-meta-grid" style={{ marginTop: '1.35rem' }}>
                <div>
                  <span className="muted">Business</span>
                  <strong>{party?.businessName || '—'}</strong>
                </div>
                <div>
                  <span className="muted">Entity type</span>
                  <strong>{party?.entityType || party?.partyType || session.partyType || '—'}</strong>
                </div>
                <div>
                  <span className="muted">Tracking ID</span>
                  <strong>{party?.trackingId || '—'}</strong>
                </div>
                <div>
                  <span className="muted">Submitted</span>
                  <strong>{fmt(party?.submittedAt)}</strong>
                </div>
                <div>
                  <span className="muted">Approved</span>
                  <strong>{fmt(party?.approvedAt)}</strong>
                </div>
                <div>
                  <span className="muted">Contact</span>
                  <strong>{party?.email || party?.phone || '—'}</strong>
                </div>
              </div>

              {status === 'ACTIVE' && provision && (
                <div className="dash-kyc animate-in animate-in-delay-1" style={{ marginTop: '1.25rem' }}>
                  <div className="dash-kyc-head">
                    <h3 style={{ margin: 0 }}>DFS account</h3>
                    <span className={`status status-${
                      provision === 'SUCCESS' ? 'ACTIVE'
                        : provision === 'FAILED' ? 'REJECTED'
                          : 'SUBMITTED'
                    }`}>{provision}</span>
                  </div>
                  <p className="muted" style={{ margin: '0.5rem 0 0' }}>{provisionHint(provision)}</p>
                  <div className="ops-meta-grid" style={{ marginTop: '0.85rem' }}>
                    <div>
                      <span className="muted">DFS account ID</span>
                      <strong>{party?.dfsAccountId || '—'}</strong>
                    </div>
                    <div>
                      <span className="muted">Provisioned</span>
                      <strong>{fmt(party?.accountProvisionedAt)}</strong>
                    </div>
                  </div>
                  {party?.accountProvisionError && provision !== 'SUCCESS' && (
                    <div className="alert alert-info alert-spaced" style={{ marginTop: '0.75rem' }}>
                      {party.accountProvisionError}
                    </div>
                  )}
                </div>
              )}

              {kycTotal > 0 && (
                <div className="dash-kyc animate-in animate-in-delay-1">
                  <div className="dash-kyc-head">
                    <h3 style={{ margin: 0 }}>Partner mobile KYC</h3>
                    <span className="muted">{kycDone}/{kycTotal} complete</span>
                  </div>
                  <div className="ops-progress large">
                    <div className="ops-progress-bar">
                      <span style={{ width: `${kycPct}%` }} />
                    </div>
                    <span className="ops-progress-label">{kycPct}%</span>
                  </div>
                  <div className="dash-kyc-list">
                    {(party?.partnerAppUsers || []).map((u) => (
                      <div className="doc-row" key={u.id}>
                        <div>
                          <strong>{u.fullName}</strong>
                          <div className="muted">{u.phone}</div>
                        </div>
                        <span className={`status status-${u.status === 'KYC_COMPLETED' ? 'ACTIVE' : u.status === 'FAILED' ? 'REJECTED' : 'SUBMITTED'}`}>
                          {u.status}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              <div className="actions" style={{ marginTop: '1.5rem' }}>
                {showApp && (
                  <Link className="btn btn-primary" to="/onboarding">
                    {status === 'DRAFT' || status === 'REJECTED' ? 'Continue application' : 'View application'}
                  </Link>
                )}
                <Link className="btn btn-ghost" to="/getting-started">
                  Getting started
                </Link>
                <Link className="btn btn-ghost" to="/profile">
                  Profile
                </Link>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
