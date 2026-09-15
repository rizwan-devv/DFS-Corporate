import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { PageHeader } from '../components/PageHeader';
import { FinanceSlideshow } from '../components/FinanceSlideshow';
import { api } from '../lib/api';
import { useAuth } from '../auth/AuthContext';
import {
  type AgentAppPortalResponse,
  agentBalanceValue,
  formatMoney,
} from '../lib/agentPortal';

type PartyMe = {
  businessName?: string;
  fullName?: string;
  phone?: string;
  status?: string;
  dfsAccountId?: string;
  levelCode?: string;
  trackingId?: string;
};

export function BalancePage() {
  const { session } = useAuth();
  const [party, setParty] = useState<PartyMe | null>(null);
  const [balance, setBalance] = useState<AgentAppPortalResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!session?.token) {
      setError('Login required.');
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const me = await api<PartyMe>('/api/onboarding/me', { token: session.token });
      setParty(me);
      const bal = await api<AgentAppPortalResponse>('/api/me/agent-balance', { token: session.token });
      setBalance(bal);
      if (bal.responsecode && bal.responsecode !== '000') {
        setError(bal.messages || `AgentApp response ${bal.responsecode}`);
      }
    } catch (e) {
      setBalance(null);
      setError(e instanceof Error ? e.message : 'Failed to load balance');
    } finally {
      setLoading(false);
    }
  }, [session?.token]);

  useEffect(() => {
    void load();
  }, [load]);

  const balStr = agentBalanceValue(balance);
  const live = balStr !== '—';

  return (
    <div className="portal-page">
      <PageHeader
        eyebrow="Finance"
        title="Account balance"
        subtitle="Your corporate AgentApp wallet balance (live)."
        actions={
          <>
            <button type="button" className="btn btn-ghost btn-sm" onClick={() => void load()} disabled={loading}>
              {loading ? 'Refreshing…' : 'Refresh'}
            </button>
            <Link className="btn btn-ghost btn-sm" to="/statement">
              View statement
            </Link>
          </>
        }
      />

      <FinanceSlideshow
        slides={[
          { accent: 'Live', title: 'AgentApp balance', body: 'Pulled with your party mobile + account level.' },
          { accent: 'Portal key', title: 'Server config', body: 'Needs DFS_PORTAL_API_ENABLED and CORPORATE_PORTAL_API_KEY.' },
          { accent: 'Statement', title: 'See movements', body: 'Open Statement for miniStatment rows.' },
        ]}
      />

      {error && <p className="api-banner">{error}</p>}

      <div className="stat-ribbon animate-in animate-in-delay-1">
        <article className="stat-tile stat-tile--primary">
          <span className="stat-label">Available balance</span>
          <strong className="stat-value">
            {live ? `PKR ${formatMoney(Number(balStr))}` : '—'}
          </strong>
          <span className="stat-hint">{live ? 'Live from AgentApp' : loading ? 'Loading…' : 'Unavailable'}</span>
        </article>
        <article className="stat-tile">
          <span className="stat-label">Mobile</span>
          <strong className="stat-value" style={{ fontSize: '1.1rem' }}>
            {balance?.mobileNumber || party?.phone || '—'}
          </strong>
          <span className="stat-hint">AgentApp lookup key</span>
        </article>
        <article className="stat-tile">
          <span className="stat-label">Account level</span>
          <strong className="stat-value">{balance?.accountLevelCode || party?.levelCode || 'L4'}</strong>
          <span className="stat-hint">Must match AgentApp account</span>
        </article>
      </div>

      <div className="portal-grid-2 animate-in animate-in-delay-2">
        <section className="glass-panel">
          <div className="panel-header">
            <div>
              <h2 className="panel-title">Your account</h2>
              <p className="muted panel-subtitle">Corporate party profile</p>
            </div>
            <span className={`chip ${party?.status === 'ACTIVE' ? 'chip-success' : 'chip-warn'}`}>
              {party?.status || '—'}
            </span>
          </div>
          <dl className="detail-list">
            <div><dt>Account name</dt><dd>{party?.businessName || party?.fullName || '—'}</dd></div>
            <div><dt>Tracking ID</dt><dd className="mono">{party?.trackingId || balance?.childTrackingId || '—'}</dd></div>
            <div><dt>DFS account</dt><dd className="mono">{party?.dfsAccountId || '—'}</dd></div>
            <div><dt>AgentApp code</dt><dd className="mono">{balance?.responsecode || '—'}</dd></div>
          </dl>
        </section>

        <section className="glass-panel">
          <div className="panel-header">
            <div>
              <h2 className="panel-title">Quick actions</h2>
              <p className="muted panel-subtitle">Related finance views</p>
            </div>
          </div>
          <div className="action-stack">
            <Link className="action-row" to="/statement">
              <span>Mini statement</span>
              <span className="muted">Recent movements</span>
            </Link>
            <Link className="action-row" to="/transactions">
              <span>Commission %</span>
              <span className="muted">Franchise rate plans</span>
            </Link>
            <Link className="action-row" to="/cards">
              <span>Linked cards</span>
              <span className="muted">CMS-style details</span>
            </Link>
          </div>
        </section>
      </div>
    </div>
  );
}
