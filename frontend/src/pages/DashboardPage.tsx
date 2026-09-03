import { type FormEvent, useCallback, useEffect, useState } from 'react';
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

type FranchiseInvite = {
  id: number;
  contactName: string;
  email: string;
  phone: string;
  businessName?: string;
  entityType?: string;
  status: string;
  inviteUrl: string;
  invitedAt?: string;
  expiresAt?: string;
  completedAt?: string;
  childTrackingId?: string;
  childStatus?: string;
  commissionRatePercent?: number;
  commissionType?: string;
  commissionNotes?: string;
};

type FranchiseChild = {
  id: number;
  publicId: string;
  trackingId?: string;
  businessName?: string;
  fullName?: string;
  email?: string;
  phone?: string;
  status?: string;
  partyType?: string;
  entityType?: string;
  commissionRatePercent?: number;
  commissionStatus?: string;
  commissionType?: string;
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
  const [invites, setInvites] = useState<FranchiseInvite[]>([]);
  const [children, setChildren] = useState<FranchiseChild[]>([]);
  const [franchiseError, setFranchiseError] = useState('');
  const [franchiseBusy, setFranchiseBusy] = useState(false);
  const [inviteForm, setInviteForm] = useState({
    contactName: '',
    email: '',
    phone: '',
    businessName: '',
    commissionRatePercent: '',
  });
  const [copiedId, setCopiedId] = useState<number | null>(null);

  const isMaster = (party?.partyType || session?.partyType) === 'MERCHANT';
  const canManageFranchises = isMaster && (party?.status || session?.partyStatus) === 'ACTIVE';

  const loadFranchiseData = useCallback(async (token: string) => {
    try {
      const [inv, kids] = await Promise.all([
        api<FranchiseInvite[]>('/api/franchises/invites', { token }),
        api<FranchiseChild[]>('/api/franchises/children', { token }),
      ]);
      setInvites(inv);
      setChildren(kids);
      setFranchiseError('');
    } catch (err) {
      setFranchiseError(err instanceof Error ? err.message : 'Failed to load franchise data');
    }
  }, []);

  useEffect(() => {
    if (!session?.token || session.role === 'PLATFORM_ADMIN') return;
    let cancelled = false;
    setLoading(true);
    api<Party>('/api/onboarding/me', { token: session.token })
      .then((data) => {
        if (cancelled) return;
        setParty(data);
        if (data.status && data.status !== session.partyStatus) {
          setSession({ ...session, partyStatus: data.status, partyType: data.partyType || session.partyType });
        }
        if (data.partyType === 'MERCHANT' && data.status === 'ACTIVE') {
          return loadFranchiseData(session.token);
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
  const badgeLabel = isMaster ? 'CORPORATE MASTER' : 'FRANCHISE / CHILD WALLET';

  async function createInvite(e: FormEvent) {
    e.preventDefault();
    if (!session?.token) return;
    setFranchiseBusy(true);
    setFranchiseError('');
    try {
      const body: Record<string, unknown> = {
        contactName: inviteForm.contactName,
        email: inviteForm.email,
        phone: inviteForm.phone,
        businessName: inviteForm.businessName || undefined,
      };
      if (inviteForm.commissionRatePercent.trim()) {
        body.commissionRatePercent = Number(inviteForm.commissionRatePercent);
        body.commissionType = 'PERCENT_GROSS';
      }
      await api('/api/franchises/invites', {
        method: 'POST',
        token: session.token,
        body: JSON.stringify(body),
      });
      setInviteForm({ contactName: '', email: '', phone: '', businessName: '', commissionRatePercent: '' });
      await loadFranchiseData(session.token);
    } catch (err) {
      setFranchiseError(err instanceof Error ? err.message : 'Invite failed');
    } finally {
      setFranchiseBusy(false);
    }
  }

  async function confirmCommission(childPartyId: number) {
    if (!session?.token) return;
    setFranchiseBusy(true);
    setFranchiseError('');
    try {
      await api(`/api/franchises/children/${childPartyId}/confirm-commission`, {
        method: 'POST',
        token: session.token,
      });
      await loadFranchiseData(session.token);
    } catch (err) {
      setFranchiseError(err instanceof Error ? err.message : 'Commission lock failed');
    } finally {
      setFranchiseBusy(false);
    }
  }

  async function resendInvite(id: number) {
    if (!session?.token) return;
    setFranchiseBusy(true);
    try {
      await api(`/api/franchises/invites/${id}/resend`, { method: 'POST', token: session.token });
      await loadFranchiseData(session.token);
    } catch (err) {
      setFranchiseError(err instanceof Error ? err.message : 'Resend failed');
    } finally {
      setFranchiseBusy(false);
    }
  }

  async function cancelInvite(id: number) {
    if (!session?.token) return;
    setFranchiseBusy(true);
    try {
      await api(`/api/franchises/invites/${id}/cancel`, { method: 'POST', token: session.token });
      await loadFranchiseData(session.token);
    } catch (err) {
      setFranchiseError(err instanceof Error ? err.message : 'Cancel failed');
    } finally {
      setFranchiseBusy(false);
    }
  }

  async function copyLink(inv: FranchiseInvite) {
    try {
      await navigator.clipboard.writeText(inv.inviteUrl);
      setCopiedId(inv.id);
      setTimeout(() => setCopiedId(null), 2000);
    } catch {
      setFranchiseError('Could not copy link — select it manually from the invite row.');
    }
  }

  return (
    <div className="page">
      <div className="container dash">
        <div className="panel panel--wide animate-in">
          <div className="panel-header">
            <div>
              <div className="badge">{badgeLabel}</div>
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

              {canManageFranchises && (
                <div className="dash-kyc animate-in animate-in-delay-1" style={{ marginTop: '1.5rem' }}>
                  <div className="dash-kyc-head">
                    <h3 style={{ margin: 0 }}>Franchise / child wallets</h3>
                    <span className="muted">{children.length} onboarded · {invites.length} invites</span>
                  </div>
                  <p className="muted" style={{ margin: '0.5rem 0 1rem' }}>
                    Invite a franchise with a secure link. Parent is bound automatically — they never
                    type your public ID.
                  </p>
                  {franchiseError && <div className="alert alert-error">{franchiseError}</div>}

                  <form className="form-grid" onSubmit={createInvite} style={{ marginBottom: '1.25rem' }}>
                    <div className="form-row">
                      <label>Contact name</label>
                      <input
                        required
                        value={inviteForm.contactName}
                        onChange={(e) => setInviteForm({ ...inviteForm, contactName: e.target.value })}
                      />
                    </div>
                    <div className="form-row">
                      <label>Email</label>
                      <input
                        required
                        type="email"
                        value={inviteForm.email}
                        onChange={(e) => setInviteForm({ ...inviteForm, email: e.target.value })}
                      />
                    </div>
                    <div className="form-row">
                      <label>Phone (app user ID)</label>
                      <input
                        required
                        value={inviteForm.phone}
                        onChange={(e) => setInviteForm({ ...inviteForm, phone: e.target.value })}
                        placeholder="03XXXXXXXXX"
                      />
                    </div>
                    <div className="form-row">
                      <label>Outlet / business name (optional)</label>
                      <input
                        value={inviteForm.businessName}
                        onChange={(e) => setInviteForm({ ...inviteForm, businessName: e.target.value })}
                      />
                    </div>
                    <div className="form-row">
                      <label>Commission % (proposed — locked on approve)</label>
                      <input
                        type="number"
                        min="0"
                        max="100"
                        step="0.01"
                        value={inviteForm.commissionRatePercent}
                        onChange={(e) => setInviteForm({ ...inviteForm, commissionRatePercent: e.target.value })}
                        placeholder="e.g. 10"
                      />
                    </div>
                    <div className="actions">
                      <button className="btn btn-primary" disabled={franchiseBusy} type="submit">
                        {franchiseBusy ? 'Working…' : 'Send franchise invite'}
                      </button>
                    </div>
                  </form>

                  {invites.length > 0 && (
                    <div className="dash-kyc-list" style={{ marginBottom: '1.25rem' }}>
                      <h4 style={{ margin: '0 0 0.5rem' }}>Invites</h4>
                      {invites.map((inv) => (
                        <div className="doc-row" key={inv.id} style={{ flexWrap: 'wrap', gap: '0.5rem' }}>
                          <div style={{ flex: '1 1 200px' }}>
                            <strong>{inv.contactName}</strong>
                            <div className="muted">{inv.email} · {inv.phone}</div>
                            {inv.commissionRatePercent != null && (
                              <div className="muted">Commission proposed: {inv.commissionRatePercent}%</div>
                            )}
                            <div className="muted" style={{ fontSize: '0.8rem', wordBreak: 'break-all' }}>
                              {inv.inviteUrl}
                            </div>
                          </div>
                          <span className={`status status-${
                            inv.status === 'COMPLETED' ? 'ACTIVE'
                              : inv.status === 'CANCELLED' || inv.status === 'EXPIRED' ? 'REJECTED'
                                : 'SUBMITTED'
                          }`}>{inv.status}</span>
                          <div className="actions" style={{ margin: 0 }}>
                            <button type="button" className="btn btn-ghost" onClick={() => copyLink(inv)}>
                              {copiedId === inv.id ? 'Copied' : 'Copy link'}
                            </button>
                            {inv.status !== 'COMPLETED' && inv.status !== 'CANCELLED' && (
                              <>
                                <button type="button" className="btn btn-ghost" disabled={franchiseBusy} onClick={() => resendInvite(inv.id)}>
                                  Resend
                                </button>
                                <button type="button" className="btn btn-ghost" disabled={franchiseBusy} onClick={() => cancelInvite(inv.id)}>
                                  Cancel
                                </button>
                              </>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                  )}

                  {children.length > 0 && (
                    <div className="dash-kyc-list">
                      <h4 style={{ margin: '0 0 0.5rem' }}>Onboarded franchises</h4>
                      {children.map((c) => (
                        <div className="doc-row" key={c.id} style={{ flexWrap: 'wrap', gap: '0.5rem' }}>
                          <div style={{ flex: '1 1 200px' }}>
                            <strong>{c.businessName || c.fullName}</strong>
                            <div className="muted">{c.trackingId} · {c.email}</div>
                            {c.commissionRatePercent != null && (
                              <div className="muted">
                                Commission: {c.commissionRatePercent}% ({c.commissionStatus || '—'})
                              </div>
                            )}
                          </div>
                          <span className={`status status-${c.status || 'DRAFT'}`}>{c.status}</span>
                          {c.commissionStatus === 'PROPOSED' && (
                            <button
                              type="button"
                              className="btn btn-primary"
                              disabled={franchiseBusy}
                              onClick={() => confirmCommission(c.id)}
                            >
                              Lock commission
                            </button>
                          )}
                        </div>
                      ))}
                    </div>
                  )}
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
