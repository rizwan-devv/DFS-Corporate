import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { PageHeader } from '../components/PageHeader';
import { FinanceSlideshow } from '../components/FinanceSlideshow';
import { api } from '../lib/api';
import { useAuth } from '../auth/AuthContext';
import { formatMoney } from '../lib/agentPortal';

type FranchiseChild = {
  id: number;
  trackingId?: string;
  businessName?: string;
  fullName?: string;
  status?: string;
  commissionRatePercent?: number;
  commissionStatus?: string;
  commissionType?: string;
};

type CommissionEntry = {
  publicId: string;
  childPartyId: number;
  childBusinessName?: string;
  childTrackingId?: string;
  inboundRef?: string;
  inboundAt?: string;
  grossAmount?: number;
  ratePercent?: number;
  commissionAmount?: number;
  status?: string;
  dfsAuthId?: string;
  errorMessage?: string;
};

type SettleResult = {
  scannedCredits?: number;
  created?: number;
  posted?: number;
  failed?: number;
  skipped?: number;
  message?: string;
};

export function TransactionsPage() {
  const { session } = useAuth();
  const [children, setChildren] = useState<FranchiseChild[]>([]);
  const [entries, setEntries] = useState<CommissionEntry[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [ok, setOk] = useState('');
  const [loading, setLoading] = useState(false);
  const [settling, setSettling] = useState(false);
  const [mpin, setMpin] = useState('');

  const load = useCallback(async () => {
    if (!session?.token) {
      setChildren([]);
      setEntries([]);
      setError('Login to view franchise commission.');
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const [kids, rows] = await Promise.all([
        api<FranchiseChild[]>('/api/franchises/children', { token: session.token }),
        api<CommissionEntry[]>('/api/franchises/commission/entries', { token: session.token }).catch(() => []),
      ]);
      setChildren(kids);
      setEntries(rows);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load commission');
      setChildren([]);
    } finally {
      setLoading(false);
    }
  }, [session?.token]);

  useEffect(() => {
    void load();
  }, [load]);

  async function settle() {
    if (!session?.token) return;
    setSettling(true);
    setError(null);
    setOk('');
    try {
      const r = await api<SettleResult>('/api/franchises/commission/settle', {
        method: 'POST',
        token: session.token,
        body: JSON.stringify(mpin.trim() ? { mpin: mpin.trim() } : {}),
      });
      setOk(r.message || 'Settle finished');
      setMpin('');
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Settle failed');
    } finally {
      setSettling(false);
    }
  }

  const postedAmt = entries.filter((e) => e.status === 'POSTED').reduce((s, e) => s + Number(e.commissionAmount || 0), 0);
  const needsMpin = entries.some((e) => e.status === 'NEEDS_MPIN');

  return (
    <div className="portal-page">
      <PageHeader
        eyebrow="Finance"
        title="Commission"
        subtitle="Inbound credits on franchise wallets: locked % is taken from the child and paid to you."
        actions={
          <>
            <button type="button" className="btn btn-ghost btn-sm" onClick={() => void load()} disabled={loading}>
              {loading ? 'Refreshing…' : 'Refresh'}
            </button>
            <Link className="btn btn-ghost btn-sm" to="/franchises">
              Manage onboarded
            </Link>
          </>
        }
      />

      <FinanceSlideshow
        slides={[
          { accent: 'Inbound', title: 'Child receives money', body: 'Only credits (C/CR) on the franchise wallet are commissioned.' },
          { accent: 'Split', title: '10% example', body: 'Child is credited 1,000 → 100 moves child → parent on success.' },
          { accent: 'Lock', title: 'PROPOSED → LOCKED', body: 'Lock the % on Onboarded before settle can pay you.' },
        ]}
      />

      {error && <p className="api-banner">{error}</p>}
      {ok && <div className="alert alert-ok">{ok}</div>}

      <div className="stat-ribbon animate-in animate-in-delay-1">
        <article className="stat-tile">
          <span className="stat-label">Franchises</span>
          <strong className="stat-value">{children.length}</strong>
        </article>
        <article className="stat-tile">
          <span className="stat-label">Locked rates</span>
          <strong className="stat-value">
            {children.filter((c) => c.commissionStatus === 'LOCKED').length}
          </strong>
        </article>
        <article className="stat-tile">
          <span className="stat-label">Paid to you</span>
          <strong className="stat-value">{formatMoney(postedAmt)}</strong>
        </article>
      </div>

      <section className="glass-panel animate-in animate-in-delay-1">
        <div className="panel-header">
          <div>
            <h2 className="panel-title">Settle inbound commission</h2>
            <p className="muted panel-subtitle">
              Reads each locked child’s last 30 days of credits, then FT the % to your wallet.
              Auto-runs every 5 minutes when DFS portal APIs are on.
            </p>
          </div>
        </div>
        {needsMpin && (
          <div className="alert alert-warn">
            Some rows need the <strong>child wallet MPIN</strong> (or set <code>parties.wallet_pin</code> on the franchise).
          </div>
        )}
        <div className="form-grid" style={{ maxWidth: 360 }}>
          <div className="form-row">
            <label>Child MPIN (if not stored)</label>
            <input
              type="password"
              inputMode="numeric"
              autoComplete="off"
              value={mpin}
              onChange={(e) => setMpin(e.target.value)}
              placeholder="Optional — used for all open rows"
            />
          </div>
        </div>
        <div className="actions">
          <button type="button" className="btn btn-primary" onClick={() => void settle()} disabled={settling || loading}>
            {settling ? 'Settling…' : 'Settle now'}
          </button>
        </div>
      </section>

      <section className="glass-panel animate-in animate-in-delay-2">
        <h2 className="panel-title">Per-transaction entries</h2>
        <p className="muted panel-subtitle">Each inbound credit on a locked franchise becomes one row. POSTED = paid to parent.</p>
        {entries.length === 0 ? (
          <p className="muted">No inbound commission yet. Lock a rate, receive money on the child wallet, then Settle now.</p>
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Franchise</th>
                  <th>Inbound</th>
                  <th>Gross</th>
                  <th>%</th>
                  <th>To parent</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {entries.map((e) => (
                  <tr key={e.publicId}>
                    <td>
                      <strong>{e.childBusinessName || e.childPartyId}</strong>
                      <div className="muted">{e.childTrackingId || '—'}</div>
                    </td>
                    <td>
                      <span className="mono">{e.inboundRef || '—'}</span>
                      <div className="muted">{e.inboundAt || '—'}</div>
                    </td>
                    <td>{formatMoney(e.grossAmount)}</td>
                    <td>{e.ratePercent != null ? `${e.ratePercent}%` : '—'}</td>
                    <td>
                      <strong>{formatMoney(e.commissionAmount)}</strong>
                      {e.dfsAuthId && <div className="muted mono">{e.dfsAuthId}</div>}
                    </td>
                    <td>
                      <span
                        className={`status status-${
                          e.status === 'POSTED' ? 'ACTIVE' : e.status === 'FAILED' ? 'REJECTED' : 'SUBMITTED'
                        }`}
                      >
                        {e.status}
                      </span>
                      {e.errorMessage && <div className="muted">{e.errorMessage}</div>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="glass-panel animate-in animate-in-delay-2">
        <h2 className="panel-title">Locked rates</h2>
        <p className="muted panel-subtitle">
          Lock rates on the <Link to="/franchises">Onboarded</Link> page.
        </p>
        {children.length === 0 ? (
          <p className="muted">No franchise children yet.</p>
        ) : (
          <div className="dash-kyc-list">
            {children.map((c) => (
              <div className="doc-row" key={c.id}>
                <div>
                  <h3 style={{ margin: 0 }}>{c.businessName || c.fullName || 'Franchise'}</h3>
                  <p className="muted" style={{ margin: '0.25rem 0 0' }}>
                    {c.trackingId || '—'} · {c.status || '—'}
                  </p>
                </div>
                <div style={{ textAlign: 'right' }}>
                  <strong>
                    {c.commissionRatePercent != null ? `${c.commissionRatePercent}%` : '—'}
                  </strong>
                  <div className="muted">{c.commissionStatus || '—'}</div>
                </div>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
