import { type FormEvent, useCallback, useEffect, useMemo, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { PageHeader } from '../components/PageHeader';
import { api } from '../lib/api';
import { useAuth } from '../auth/AuthContext';
import {
  type FranchiseInvite,
  fmtDate,
  isPendingInvite,
} from '../lib/franchiseTypes';

type PartyMe = { partyType?: string; status?: string };

export function InvitesPage() {
  const { session } = useAuth();
  const [invites, setInvites] = useState<FranchiseInvite[]>([]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [copiedId, setCopiedId] = useState<number | null>(null);
  const [allowed, setAllowed] = useState(false);
  const [form, setForm] = useState({
    contactName: '',
    email: '',
    phone: '',
    businessName: '',
    commissionRatePercent: '',
  });

  const pending = useMemo(() => invites.filter(isPendingInvite), [invites]);
  const history = useMemo(() => invites.filter((i) => !isPendingInvite(i)), [invites]);

  const load = useCallback(async () => {
    if (!session?.token) return;
    setLoading(true);
    setError('');
    try {
      const me = await api<PartyMe>('/api/onboarding/me', { token: session.token });
      const ok = me.partyType === 'MERCHANT' && me.status === 'ACTIVE';
      setAllowed(ok);
      if (!ok) {
        setInvites([]);
        return;
      }
      const inv = await api<FranchiseInvite[]>('/api/franchises/invites', { token: session.token });
      setInvites(inv);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load invites');
    } finally {
      setLoading(false);
    }
  }, [session?.token]);

  useEffect(() => {
    void load();
  }, [load]);

  if (!session) return <Navigate to="/login" replace />;
  if (session.role === 'PLATFORM_ADMIN') return <Navigate to="/admin" replace />;

  async function createInvite(e: FormEvent) {
    e.preventDefault();
    if (!session?.token) return;
    setBusy(true);
    setError('');
    try {
      const body: Record<string, unknown> = {
        contactName: form.contactName,
        email: form.email,
        phone: form.phone,
        businessName: form.businessName || undefined,
      };
      if (form.commissionRatePercent.trim()) {
        body.commissionRatePercent = Number(form.commissionRatePercent);
        body.commissionType = 'PERCENT_GROSS';
      }
      await api('/api/franchises/invites', {
        method: 'POST',
        token: session.token,
        body: JSON.stringify(body),
      });
      setForm({ contactName: '', email: '', phone: '', businessName: '', commissionRatePercent: '' });
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Invite failed');
    } finally {
      setBusy(false);
    }
  }

  async function resendInvite(id: number) {
    if (!session?.token) return;
    setBusy(true);
    try {
      await api(`/api/franchises/invites/${id}/resend`, { method: 'POST', token: session.token });
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Resend failed');
    } finally {
      setBusy(false);
    }
  }

  async function cancelInvite(id: number) {
    if (!session?.token) return;
    setBusy(true);
    try {
      await api(`/api/franchises/invites/${id}/cancel`, { method: 'POST', token: session.token });
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Cancel failed');
    } finally {
      setBusy(false);
    }
  }

  async function copyLink(inv: FranchiseInvite) {
    try {
      await navigator.clipboard.writeText(inv.inviteUrl);
      setCopiedId(inv.id);
      setTimeout(() => setCopiedId(null), 2000);
    } catch {
      setError('Could not copy link — select it from the invite row.');
    }
  }

  return (
    <div className="portal-page">
      <PageHeader
        eyebrow="Network"
        title="Franchise invites"
        subtitle="Pending invites only. When someone completes onboarding they leave this list and appear under Onboarded."
        actions={
          <>
            <button type="button" className="btn btn-ghost btn-sm" onClick={() => void load()} disabled={loading || busy}>
              {loading ? 'Refreshing…' : 'Refresh'}
            </button>
            <Link className="btn btn-ghost btn-sm" to="/franchises">
              Onboarded
            </Link>
          </>
        }
      />

      {error && <p className="api-banner">{error}</p>}

      {!allowed && !loading ? (
        <section className="glass-panel">
          <h2 className="panel-title">Not available</h2>
          <p className="muted">Only an ACTIVE corporate master can send franchise invites.</p>
          <Link className="btn btn-primary" to="/dashboard">
            Back to Dashboard
          </Link>
        </section>
      ) : (
        <>
          <section className="glass-panel animate-in">
            <h2 className="panel-title">Send invite</h2>
            <p className="muted panel-subtitle">
              Parent is bound automatically — the franchise never types your public ID.
            </p>
            <form className="form-grid" onSubmit={createInvite}>
              <div className="form-row">
                <label>Contact name</label>
                <input
                  required
                  value={form.contactName}
                  onChange={(e) => setForm({ ...form, contactName: e.target.value })}
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
                <label>Phone (app user ID)</label>
                <input
                  required
                  value={form.phone}
                  onChange={(e) => setForm({ ...form, phone: e.target.value })}
                  placeholder="03XXXXXXXXX"
                />
              </div>
              <div className="form-row">
                <label>Outlet / business name (optional)</label>
                <input
                  value={form.businessName}
                  onChange={(e) => setForm({ ...form, businessName: e.target.value })}
                />
              </div>
              <div className="form-row">
                <label>Commission % (proposed)</label>
                <input
                  type="number"
                  min="0"
                  max="100"
                  step="0.01"
                  value={form.commissionRatePercent}
                  onChange={(e) => setForm({ ...form, commissionRatePercent: e.target.value })}
                  placeholder="e.g. 10"
                />
              </div>
              <div className="actions">
                <button className="btn btn-primary" disabled={busy} type="submit">
                  {busy ? 'Working…' : 'Send franchise invite'}
                </button>
              </div>
            </form>
          </section>

          <section className="glass-panel animate-in animate-in-delay-1" style={{ marginTop: '1.25rem' }}>
            <div className="panel-header">
              <div>
                <h2 className="panel-title">Pending</h2>
                <p className="muted panel-subtitle">{pending.length} open invite(s)</p>
              </div>
            </div>
            {loading ? (
              <p className="muted">Loading…</p>
            ) : pending.length === 0 ? (
              <p className="muted">No pending invites. Sent invites that completed onboarding are on Franchises.</p>
            ) : (
              <div className="dash-kyc-list">
                {pending.map((inv) => (
                  <div className="doc-row" key={inv.id} style={{ flexWrap: 'wrap', gap: '0.5rem' }}>
                    <div style={{ flex: '1 1 220px' }}>
                      <strong>{inv.contactName}</strong>
                      <div className="muted">
                        {inv.email} · {inv.phone}
                      </div>
                      {inv.businessName && <div className="muted">{inv.businessName}</div>}
                      {inv.commissionRatePercent != null && (
                        <div className="muted">Proposed commission: {inv.commissionRatePercent}%</div>
                      )}
                      <div className="muted" style={{ fontSize: '0.8rem' }}>
                        Sent {fmtDate(inv.invitedAt)} · expires {fmtDate(inv.expiresAt)}
                      </div>
                      <div className="mono muted" style={{ fontSize: '0.75rem', wordBreak: 'break-all' }}>
                        {inv.inviteUrl}
                      </div>
                    </div>
                    <span className="status status-SUBMITTED">{inv.status}</span>
                    <div className="actions" style={{ margin: 0 }}>
                      <button type="button" className="btn btn-ghost btn-sm" onClick={() => void copyLink(inv)}>
                        {copiedId === inv.id ? 'Copied' : 'Copy link'}
                      </button>
                      <button
                        type="button"
                        className="btn btn-ghost btn-sm"
                        disabled={busy}
                        onClick={() => void resendInvite(inv.id)}
                      >
                        Resend
                      </button>
                      <button
                        type="button"
                        className="btn btn-ghost btn-sm"
                        disabled={busy}
                        onClick={() => void cancelInvite(inv.id)}
                      >
                        Cancel
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </section>

          {history.length > 0 && (
            <section className="glass-panel animate-in" style={{ marginTop: '1.25rem' }}>
              <h2 className="panel-title">Closed invites</h2>
              <p className="muted panel-subtitle">Completed / cancelled / expired — for audit only.</p>
              <div className="dash-kyc-list">
                {history.map((inv) => (
                  <div className="doc-row" key={inv.id}>
                    <div>
                      <strong>{inv.contactName}</strong>
                      <div className="muted">
                        {inv.email} · {inv.status}
                        {inv.childTrackingId ? ` · child ${inv.childTrackingId}` : ''}
                      </div>
                    </div>
                    <span
                      className={`status status-${
                        inv.status === 'COMPLETED' ? 'ACTIVE' : 'REJECTED'
                      }`}
                    >
                      {inv.status}
                    </span>
                  </div>
                ))}
              </div>
            </section>
          )}
        </>
      )}
    </div>
  );
}
