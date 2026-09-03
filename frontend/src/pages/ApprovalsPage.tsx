import { type FormEvent, useEffect, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { api } from '../lib/api';
import { useAuth } from '../auth/AuthContext';

type Approval = {
  publicId: string;
  requestType: string;
  title: string;
  referenceKey?: string;
  status: string;
  currentStep: string;
  createdAt?: string;
  actions?: { step: string; decision: string; actorEmail: string; comment?: string; createdAt?: string }[];
};

export function ApprovalsPage() {
  const { session } = useAuth();
  const [inbox, setInbox] = useState<Approval[]>([]);
  const [all, setAll] = useState<Approval[]>([]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [form, setForm] = useState({
    title: '',
    requestType: 'GENERIC',
    referenceKey: '',
    comment: '',
  });

  const roles = session?.portalRoles || [];
  const canMake = roles.includes('MAKER') || roles.includes('PARTY_ADMIN');

  async function load() {
    if (!session?.token) return;
    try {
      const [i, a] = await Promise.all([
        api<Approval[]>('/api/approvals/inbox', { token: session.token }),
        api<Approval[]>('/api/approvals', { token: session.token }),
      ]);
      setInbox(i);
      setAll(a);
      setError('');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load approvals');
    }
  }

  useEffect(() => {
    load();
  }, [session?.token]);

  if (!session) return <Navigate to="/login" replace />;
  if (session.role === 'PLATFORM_ADMIN') return <Navigate to="/admin" replace />;
  if (session.partyStatus !== 'ACTIVE') return <Navigate to="/dashboard" replace />;

  async function create(e: FormEvent) {
    e.preventDefault();
    if (!session?.token) return;
    setBusy(true);
    try {
      await api('/api/approvals', {
        method: 'POST',
        token: session.token,
        body: JSON.stringify(form),
      });
      setForm({ title: '', requestType: 'GENERIC', referenceKey: '', comment: '' });
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Create failed');
    } finally {
      setBusy(false);
    }
  }

  async function decide(publicId: string, decision: 'APPROVE' | 'REJECT') {
    if (!session?.token) return;
    setBusy(true);
    try {
      await api(`/api/approvals/${publicId}/decide`, {
        method: 'POST',
        token: session.token,
        body: JSON.stringify({ decision }),
      });
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Decision failed');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="page">
      <div className="container dash">
        <div className="panel panel--wide">
          <div className="badge">APPROVALS</div>
          <h2 style={{ marginTop: 0 }}>Maker → Checker → Approver → Releaser</h2>
          <p className="muted">
            Flow: Maker submits → Checker → Approver → Releaser. If the Checker also has Approver,
            Approver is skipped and the item goes to Releaser.
          </p>
          <p className="muted">Your roles: {roles.join(', ') || '—'}</p>
          {error && <div className="alert alert-error">{error}</div>}

          {canMake && (
            <form className="form-grid" onSubmit={create} style={{ marginBottom: '1.5rem' }}>
              <div className="form-row">
                <label>Title</label>
                <input required value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })}
                  placeholder="e.g. Sample payment request" />
              </div>
              <div className="form-row">
                <label>Type</label>
                <select value={form.requestType} onChange={(e) => setForm({ ...form, requestType: e.target.value })}>
                  <option value="GENERIC">GENERIC (workflow test)</option>
                  <option value="PAYMENT">PAYMENT (future)</option>
                  <option value="BULK_PAYMENT">BULK_PAYMENT (future)</option>
                  <option value="COMMISSION_CHANGE">COMMISSION_CHANGE</option>
                </select>
              </div>
              <div className="form-row">
                <label>Reference (optional)</label>
                <input value={form.referenceKey} onChange={(e) => setForm({ ...form, referenceKey: e.target.value })} />
              </div>
              <button className="btn btn-primary" disabled={busy} type="submit">Submit for check</button>
            </form>
          )}

          <h3>Inbox (awaiting your action)</h3>
          <div className="dash-kyc-list" style={{ marginBottom: '1.5rem' }}>
            {inbox.length === 0 && <p className="muted">Nothing in your inbox.</p>}
            {inbox.map((item) => (
              <div className="doc-row" key={item.publicId} style={{ flexWrap: 'wrap', gap: '0.5rem' }}>
                <div style={{ flex: '1 1 200px' }}>
                  <strong>{item.title}</strong>
                  <div className="muted">{item.requestType} · step {item.currentStep}</div>
                </div>
                <span className="status status-SUBMITTED">{item.status}</span>
                <div className="actions" style={{ margin: 0 }}>
                  <button type="button" className="btn btn-primary" disabled={busy} onClick={() => decide(item.publicId, 'APPROVE')}>
                    Approve
                  </button>
                  <button type="button" className="btn btn-ghost" disabled={busy} onClick={() => decide(item.publicId, 'REJECT')}>
                    Reject
                  </button>
                </div>
              </div>
            ))}
          </div>

          <h3>All requests</h3>
          <div className="dash-kyc-list">
            {all.map((item) => (
              <div className="doc-row" key={item.publicId}>
                <div>
                  <strong>{item.title}</strong>
                  <div className="muted">
                    {item.requestType} · {item.currentStep} · {item.status}
                  </div>
                </div>
                <span className={`status status-${item.status === 'APPROVED' ? 'ACTIVE' : item.status === 'REJECTED' ? 'REJECTED' : 'SUBMITTED'}`}>
                  {item.status}
                </span>
              </div>
            ))}
          </div>

          <div className="actions" style={{ marginTop: '1.25rem' }}>
            <Link className="btn btn-ghost" to="/dashboard">Dashboard</Link>
            <Link className="btn btn-ghost" to="/portal-users">Portal users</Link>
          </div>
        </div>
      </div>
    </div>
  );
}
