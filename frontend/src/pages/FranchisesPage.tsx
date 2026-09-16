import { useCallback, useEffect, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { PageHeader } from '../components/PageHeader';
import { api } from '../lib/api';
import { useAuth } from '../auth/AuthContext';
import {
  type AgentAppPortalResponse,
  type FranchiseChild,
  agentBalanceValue,
  agentMiniStatementRows,
  amountTypeLabel,
  fmtDateTime,
} from '../lib/franchiseTypes';

type PartyMe = { partyType?: string; status?: string };

export function FranchisesPage() {
  const { session } = useAuth();
  const [children, setChildren] = useState<FranchiseChild[]>([]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [allowed, setAllowed] = useState(false);
  const [agentChildId, setAgentChildId] = useState<number | null>(null);
  const [agentBalance, setAgentBalance] = useState<AgentAppPortalResponse | null>(null);
  const [agentStatement, setAgentStatement] = useState<AgentAppPortalResponse | null>(null);
  const [agentBusy, setAgentBusy] = useState(false);
  const [agentError, setAgentError] = useState('');

  const load = useCallback(async () => {
    if (!session?.token) return;
    setLoading(true);
    setError('');
    try {
      const me = await api<PartyMe>('/api/onboarding/me', { token: session.token });
      const ok = me.partyType === 'MERCHANT' && me.status === 'ACTIVE';
      setAllowed(ok);
      if (!ok) {
        setChildren([]);
        return;
      }
      const kids = await api<FranchiseChild[]>('/api/franchises/children', { token: session.token });
      setChildren(kids);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load franchises');
    } finally {
      setLoading(false);
    }
  }, [session?.token]);

  useEffect(() => {
    void load();
  }, [load]);

  if (!session) return <Navigate to="/login" replace />;
  if (session.role === 'PLATFORM_ADMIN') return <Navigate to="/admin" replace />;

  async function confirmCommission(childPartyId: number) {
    if (!session?.token) return;
    setBusy(true);
    setError('');
    try {
      await api(`/api/franchises/children/${childPartyId}/confirm-commission`, {
        method: 'POST',
        token: session.token,
      });
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Commission lock failed');
    } finally {
      setBusy(false);
    }
  }

  async function loadChildAgentData(childPartyId: number) {
    if (!session?.token) return;
    setAgentBusy(true);
    setAgentError('');
    setAgentChildId(childPartyId);
    setAgentBalance(null);
    setAgentStatement(null);
    try {
      const [bal, stmt] = await Promise.all([
        api<AgentAppPortalResponse>(`/api/franchises/children/${childPartyId}/agent-balance`, {
          token: session.token,
        }),
        api<AgentAppPortalResponse>(`/api/franchises/children/${childPartyId}/agent-mini-statement`, {
          token: session.token,
        }),
      ]);
      setAgentBalance(bal);
      setAgentStatement(stmt);
    } catch (err) {
      setAgentError(err instanceof Error ? err.message : 'Failed to load AgentApp data');
    } finally {
      setAgentBusy(false);
    }
  }

  const statementRows = agentMiniStatementRows(agentStatement?.data);
  const proposed = children.filter((c) => c.commissionStatus === 'PROPOSED').length;

  return (
    <div className="portal-page">
      <PageHeader
        eyebrow="Network"
        title="Onboarded franchises"
        subtitle="Children who finished invite onboarding. Manage commission % and peek AgentApp balance here."
        actions={
          <>
            <button type="button" className="btn btn-ghost btn-sm" onClick={() => void load()} disabled={loading || busy}>
              {loading ? 'Refreshing…' : 'Refresh'}
            </button>
            <Link className="btn btn-ghost btn-sm" to="/invites">
              Pending invites
            </Link>
            <Link className="btn btn-ghost btn-sm" to="/transactions">
              Commission overview
            </Link>
          </>
        }
      />

      {error && <p className="api-banner">{error}</p>}

      {!allowed && !loading ? (
        <section className="glass-panel">
          <h2 className="panel-title">Not available</h2>
          <p className="muted">Only an ACTIVE corporate master can manage onboarded franchises.</p>
          <Link className="btn btn-primary" to="/dashboard">
            Back to Dashboard
          </Link>
        </section>
      ) : (
        <>
          <div
            className="stat-row"
            style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap', marginBottom: '1rem' }}
          >
            <div className="stat-chip glass-panel" style={{ padding: '0.75rem 1rem' }}>
              <span className="muted">Onboarded</span>
              <strong style={{ display: 'block', fontSize: '1.35rem' }}>{children.length}</strong>
            </div>
            <div className="stat-chip glass-panel" style={{ padding: '0.75rem 1rem' }}>
              <span className="muted">Commission to lock</span>
              <strong style={{ display: 'block', fontSize: '1.35rem' }}>{proposed}</strong>
            </div>
          </div>

          <section className="glass-panel animate-in">
            <h2 className="panel-title">Franchises</h2>
            <p className="muted panel-subtitle">Percentage commission only — no amount split in this portal.</p>
            {loading ? (
              <p className="muted">Loading…</p>
            ) : children.length === 0 ? (
              <p className="muted">
                No onboarded franchises yet.{' '}
                <Link to="/invites">Send an invite</Link> — completed signups appear here.
              </p>
            ) : (
              <div className="dash-kyc-list">
                {children.map((c) => (
                  <div className="doc-row" key={c.id} style={{ flexWrap: 'wrap', gap: '0.5rem' }}>
                    <div style={{ flex: '1 1 220px' }}>
                      <strong>{c.businessName || c.fullName}</strong>
                      <div className="muted">
                        {c.trackingId} · {c.email}
                      </div>
                      {c.phone && (
                        <div className="muted">
                          Mobile {c.phone} · Level {c.levelCode || 'L4'}
                        </div>
                      )}
                      {c.dfsAccountId && (
                        <div className="muted mono">DFS ID {c.dfsAccountId}</div>
                      )}
                      <div className="muted">
                        Commission:{' '}
                        {c.commissionRatePercent != null
                          ? `${c.commissionRatePercent}% (${c.commissionStatus || '—'})`
                          : 'Not set'}
                      </div>
                    </div>
                    <span className={`status status-${c.status || 'DRAFT'}`}>{c.status}</span>
                    <button
                      type="button"
                      className="btn btn-ghost btn-sm"
                      disabled={agentBusy}
                      onClick={() => void loadChildAgentData(c.id)}
                    >
                      {agentBusy && agentChildId === c.id ? 'Loading…' : 'Agent balance / statement'}
                    </button>
                    {c.commissionStatus === 'PROPOSED' && (
                      <button
                        type="button"
                        className="btn btn-primary btn-sm"
                        disabled={busy}
                        onClick={() => void confirmCommission(c.id)}
                      >
                        Lock commission
                      </button>
                    )}
                  </div>
                ))}
              </div>
            )}
          </section>

          {(agentError || agentBalance || agentStatement) && (
            <section className="glass-panel animate-in" style={{ marginTop: '1.25rem' }}>
              <h2 className="panel-title">
                AgentApp — child #{agentChildId}
                {agentBalance?.childTrackingId ? ` (${agentBalance.childTrackingId})` : ''}
              </h2>
              {agentError && <div className="alert alert-error">{agentError}</div>}
              {agentBalance && !agentError && (
                <div className="doc-row" style={{ marginBottom: '0.75rem' }}>
                  <div>
                    <strong>Balance: {agentBalanceValue(agentBalance)}</strong>
                    <div className="muted">
                      {agentBalance.messages || '—'} · code {agentBalance.responsecode || '—'}
                      {agentBalance.mobileNumber ? ` · ${agentBalance.mobileNumber}` : ''}
                    </div>
                  </div>
                </div>
              )}
              {agentStatement && !agentError && (
                <div>
                  <div className="muted" style={{ marginBottom: '0.5rem' }}>
                    Mini-statement · {agentStatement.messages || '—'} · code{' '}
                    {agentStatement.responsecode || '—'}
                    {statementRows.length > 0 ? ` · last ${statementRows.length} txn(s)` : ''}
                  </div>
                  {statementRows.length === 0 ? (
                    <p className="muted">No transactions returned.</p>
                  ) : (
                    <div style={{ overflowX: 'auto', border: '1px solid var(--border, #e5e5e5)', borderRadius: 6 }}>
                      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' }}>
                        <thead>
                          <tr style={{ textAlign: 'left', background: 'var(--surface-2, #f3f3f3)' }}>
                            <th style={{ padding: '0.5rem 0.65rem' }}>Date</th>
                            <th style={{ padding: '0.5rem 0.65rem' }}>Description</th>
                            <th style={{ padding: '0.5rem 0.65rem' }}>Amount</th>
                            <th style={{ padding: '0.5rem 0.65rem' }}>Type</th>
                            <th style={{ padding: '0.5rem 0.65rem' }}>Closing</th>
                            <th style={{ padding: '0.5rem 0.65rem' }}>Ref</th>
                          </tr>
                        </thead>
                        <tbody>
                          {statementRows.map((row, idx) => (
                            <tr
                              key={`${row.transRefnum || 'txn'}-${idx}`}
                              style={{ borderTop: '1px solid var(--border, #eee)' }}
                            >
                              <td style={{ padding: '0.5rem 0.65rem', whiteSpace: 'nowrap' }}>
                                {fmtDateTime(row.transDate)}
                              </td>
                              <td style={{ padding: '0.5rem 0.65rem' }}>
                                <strong>{row.transDocsDescr || '—'}</strong>
                              </td>
                              <td style={{ padding: '0.5rem 0.65rem', whiteSpace: 'nowrap' }}>
                                {row.txnAmt == null ? '—' : Number(row.txnAmt).toFixed(2)}
                              </td>
                              <td style={{ padding: '0.5rem 0.65rem' }}>
                                {amountTypeLabel(row.amountType)}
                              </td>
                              <td style={{ padding: '0.5rem 0.65rem', whiteSpace: 'nowrap' }}>
                                {row.closingBalance == null ? '—' : Number(row.closingBalance).toFixed(2)}
                              </td>
                              <td style={{ padding: '0.5rem 0.65rem', fontSize: '0.75rem' }} className="muted">
                                {row.transRefnum || '—'}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </div>
              )}
            </section>
          )}
        </>
      )}
    </div>
  );
}
