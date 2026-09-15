import { useCallback, useEffect, useState } from 'react';
import { PageHeader } from '../components/PageHeader';
import { FinanceSlideshow } from '../components/FinanceSlideshow';
import { api } from '../lib/api';
import { useAuth } from '../auth/AuthContext';

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

export function TransactionsPage() {
  const { session } = useAuth();
  const [children, setChildren] = useState<FranchiseChild[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    if (!session?.token) {
      setChildren([]);
      setError('Login to view franchise commission rates.');
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const kids = await api<FranchiseChild[]>('/api/franchises/children', { token: session.token });
      setChildren(kids);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load children');
      setChildren([]);
    } finally {
      setLoading(false);
    }
  }, [session?.token]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div className="portal-page">
      <PageHeader
        eyebrow="Finance"
        title="Commission rates"
        subtitle="Percentage agreements only — no transaction amount split in this portal."
        actions={
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => void load()} disabled={loading}>
            {loading ? 'Refreshing…' : 'Refresh'}
          </button>
        }
      />

      <FinanceSlideshow
        slides={[
          { accent: 'Percent', title: 'Rate agreements', body: 'Parent locks a % for each franchise child.' },
          { accent: 'No split', title: 'No ledger here', body: 'Amount splits are not posted in DFS Corporate.' },
          { accent: 'Plans', title: 'PROPOSED → LOCKED', body: 'Confirm commission on Dashboard to lock the rate.' },
        ]}
      />

      {error && <p className="api-banner">{error}</p>}

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
          <span className="stat-label">Proposed</span>
          <strong className="stat-value">
            {children.filter((c) => c.commissionStatus === 'PROPOSED').length}
          </strong>
        </article>
      </div>

      <section className="glass-panel animate-in animate-in-delay-2">
        <div className="panel-header">
          <div>
            <h2 className="panel-title">Franchise commission %</h2>
            <p className="muted panel-subtitle">From locked / proposed plans</p>
          </div>
        </div>

        {children.length === 0 ? (
          <p className="muted">No franchise children found.</p>
        ) : (
          <div className="txn-list">
            {children.map((c, i) => (
              <article className="txn-card" key={c.id} style={{ animationDelay: `${0.05 * i}s` }}>
                <div className="txn-card-main">
                  <div className="txn-id mono">{c.trackingId || `#${c.id}`}</div>
                  <h3>{c.businessName || c.fullName || 'Franchise'}</h3>
                  <p className="muted">{c.status || '—'}</p>
                </div>
                <div className="txn-card-meta">
                  <div>
                    <span className="stat-label">Rate</span>
                    <strong className="mono">
                      {c.commissionRatePercent != null ? `${c.commissionRatePercent}%` : '—'}
                    </strong>
                  </div>
                  <div>
                    <span className="stat-label">Type</span>
                    <strong className="mono">{c.commissionType || 'PERCENT_GROSS'}</strong>
                  </div>
                  <span
                    className={`chip ${
                      c.commissionStatus === 'LOCKED'
                        ? 'chip-success'
                        : c.commissionStatus === 'PROPOSED'
                          ? 'chip-warn'
                          : 'chip-warn'
                    }`}
                  >
                    {c.commissionStatus || 'NONE'}
                  </span>
                </div>
              </article>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
