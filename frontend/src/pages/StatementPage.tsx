import { useCallback, useEffect, useState } from 'react';
import { PageHeader } from '../components/PageHeader';
import { FinanceSlideshow } from '../components/FinanceSlideshow';
import { api } from '../lib/api';
import { useAuth } from '../auth/AuthContext';
import {
  type AgentAppPortalResponse,
  agentMiniStatementRows,
  amountTypeLabel,
  formatMoney,
} from '../lib/agentPortal';

export function StatementPage() {
  const { session } = useAuth();
  const [resp, setResp] = useState<AgentAppPortalResponse | null>(null);
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
      const data = await api<AgentAppPortalResponse>('/api/me/agent-mini-statement', {
        token: session.token,
      });
      setResp(data);
      if (data.responsecode && data.responsecode !== '000') {
        setError(data.messages || `AgentApp response ${data.responsecode}`);
      }
    } catch (e) {
      setResp(null);
      setError(e instanceof Error ? e.message : 'Failed to load statement');
    } finally {
      setLoading(false);
    }
  }, [session?.token]);

  useEffect(() => {
    void load();
  }, [load]);

  const rows = agentMiniStatementRows(resp?.data);

  return (
    <div className="portal-page">
      <PageHeader
        eyebrow="Finance"
        title="Account statement"
        subtitle="Your AgentApp mini-statement (live)."
        actions={
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => void load()} disabled={loading}>
            {loading ? 'Refreshing…' : 'Refresh'}
          </button>
        }
      />

      <FinanceSlideshow
        slides={[
          { accent: 'Live', title: 'Mini statement', body: 'Last movements from AgentApp corporate miniStatment.' },
          { accent: 'Keys', title: 'Mobile + level', body: `Lookup ${resp?.mobileNumber || '…'} @ ${resp?.accountLevelCode || 'L4'}` },
          { accent: 'Config', title: 'Portal API', body: 'Requires DFS_PORTAL_API_ENABLED and CORPORATE_PORTAL_API_KEY.' },
        ]}
      />

      {error && <p className="api-banner">{error}</p>}

      <section className="glass-panel animate-in animate-in-delay-1">
        <div className="panel-header">
          <div>
            <h2 className="panel-title">Recent activity</h2>
            <p className="muted panel-subtitle">
              {resp?.mobileNumber
                ? `${resp.mobileNumber} · level ${resp.accountLevelCode || '—'}`
                : loading
                  ? 'Loading…'
                  : 'No data yet'}
            </p>
          </div>
          <span className={`chip ${resp?.responsecode === '000' ? 'chip-success' : 'chip-warn'}`}>
            {resp?.responsecode || (loading ? '…' : '—')}
          </span>
        </div>

        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Date</th>
                <th>Description</th>
                <th>Type</th>
                <th className="num">Amount</th>
                <th className="num">Fee</th>
                <th className="num">Closing</th>
                <th>Ref</th>
              </tr>
            </thead>
            <tbody>
              {rows.length === 0 ? (
                <tr>
                  <td colSpan={7} className="muted">
                    {loading ? 'Loading…' : 'No transactions returned.'}
                  </td>
                </tr>
              ) : (
                rows.map((row, i) => {
                  const type = amountTypeLabel(row.amountType);
                  const isCredit = type.toUpperCase().startsWith('C');
                  return (
                    <tr key={`${row.transRefnum || i}-${row.transDate || i}`}>
                      <td className="mono">{row.transDate || '—'}</td>
                      <td>{row.transDocsDescr || '—'}</td>
                      <td>
                        <span className={`chip ${isCredit ? 'chip-success' : 'chip-warn'}`}>{type}</span>
                      </td>
                      <td className={`num mono ${isCredit ? 'text-success' : 'text-danger'}`}>
                        {formatMoney(row.txnAmt ?? null)}
                      </td>
                      <td className="num mono">{formatMoney(row.feeAmt ?? null)}</td>
                      <td className="num mono">{formatMoney(row.closingBalance ?? null)}</td>
                      <td className="mono muted">{row.transRefnum || '—'}</td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
        {resp?.messages && <p className="muted" style={{ marginTop: '0.75rem' }}>{resp.messages}</p>}
      </section>
    </div>
  );
}
