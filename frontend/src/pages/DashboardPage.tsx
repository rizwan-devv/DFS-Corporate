import { useEffect, useMemo, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { PageHeader } from '../components/PageHeader';
import { api } from '../lib/api';
import { useAuth } from '../auth/AuthContext';
import {
  type FranchiseChild,
  type FranchiseInvite,
  fmtDate,
  isPendingInvite,
} from '../lib/franchiseTypes';
import {
  type AgentAppPortalResponse,
  type AgentMiniStatementRow,
  agentBalanceValue,
  agentMiniStatementRows,
  formatMoney,
} from '../lib/agentPortal';
import {
  AreaChart,
  BarChart,
  ChartCard,
  ChartEmpty,
  GaugeChart,
  KpiTile,
  RankRow,
  Sparkline,
  compactNumber,
} from '../components/charts';

function formatBalanceDisplay(raw: string): string {
  if (!raw || raw === '—') return '—';
  const n = Number(raw);
  return Number.isFinite(n) ? formatMoney(n) : raw;
}

type AppUser = {
  id: number;
  fullName: string;
  phone: string;
  status: string;
  signatureUploaded?: boolean;
  bankVisitRequired?: boolean;
};
type RequiredDoc = { documentCode: string; documentLabel: string; mandatory: boolean; uploaded: boolean };
type PartyDoc = { documentCode: string; status: string; reviewNote?: string };
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
  discrepancyNote?: string;
  rejectionReason?: string;
  accountProvisionStatus?: string;
  dfsAccountId?: string;
  cmsRelationshipNum?: string;
  accountProvisionError?: string;
  accountProvisionedAt?: string;
  partnerKycTotal?: number;
  partnerKycCompleted?: number;
  partnerAppUsers?: AppUser[];
  requiredDocuments?: RequiredDoc[];
  documents?: PartyDoc[];
  levelCode?: string;
};

type DayBucket = {
  label: string;
  sort: number;
  credit: number;
  debit: number;
  fee: number;
  closing: number | null;
  count: number;
};

/** AgentApp sends several date shapes; fall back to row order when unparseable. */
function parseTxnDate(raw?: string): Date | null {
  if (!raw) return null;
  const s = raw.trim();
  const direct = new Date(s.replace(' ', 'T'));
  if (!Number.isNaN(direct.getTime())) return direct;
  const dmy = s.match(/^(\d{2})[/-](\d{2})[/-](\d{4})/);
  if (dmy) return new Date(Number(dmy[3]), Number(dmy[2]) - 1, Number(dmy[1]));
  return null;
}

function bucketByDay(rows: AgentMiniStatementRow[]): DayBucket[] {
  const map = new Map<string, DayBucket>();
  rows.forEach((row, idx) => {
    const d = parseTxnDate(row.transDate);
    const key = d ? d.toISOString().slice(0, 10) : `#${idx}`;
    const bucket =
      map.get(key) ??
      {
        label: d
          ? d.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
          : `#${idx + 1}`,
        sort: d ? d.getTime() : idx,
        credit: 0,
        debit: 0,
        fee: 0,
        closing: null,
        count: 0,
      };
    const amount = Math.abs(Number(row.txnAmt ?? 0) || 0);
    if ((row.amountType || '').toUpperCase().startsWith('C')) bucket.credit += amount;
    else bucket.debit += amount;
    bucket.fee += Math.abs(Number(row.feeAmt ?? 0) || 0);
    if (row.closingBalance != null) bucket.closing = Number(row.closingBalance);
    bucket.count += 1;
    map.set(key, bucket);
  });
  return [...map.values()].sort((a, b) => a.sort - b.sort).slice(-10);
}

function statusHint(status?: string) {
  switch (status) {
    case 'DRAFT':
      return 'Upload documents as you get them. Submit stays locked until all required files are in.';
    case 'SUBMITTED':
      return 'Waiting for partners to finish mobile app KYC.';
    case 'INCOMPLETE':
      return 'Documents incomplete or rejected — re-upload only the rejected files in My Application.';
    case 'PENDING_APPROVAL':
      return 'With backoffice for final review (5 working-day TAT).';
    case 'ACTIVE':
      return 'Entity approved. Master account and network shortcuts are below.';
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
  const [balance, setBalance] = useState<AgentAppPortalResponse | null>(null);
  const [statement, setStatement] = useState<AgentAppPortalResponse | null>(null);

  const isMaster = (party?.partyType || session?.partyType) === 'MERCHANT';
  const canManageNetwork = isMaster && (party?.status || session?.partyStatus) === 'ACTIVE';
  const status = party?.status || session?.partyStatus || '—';
  const provision = party?.accountProvisionStatus;
  const kycTotal = party?.partnerKycTotal || 0;
  const kycDone = party?.partnerKycCompleted || 0;
  const kycPct = kycTotal > 0 ? Math.round((kycDone / kycTotal) * 100) : 0;
  const showApp = status === 'DRAFT' || status === 'REJECTED' || status === 'SUBMITTED' || status === 'PENDING_APPROVAL' || status === 'INCOMPLETE';
  const badgeLabel = isMaster ? 'CORPORATE MASTER' : 'FRANCHISE / CHILD WALLET';
  const pendingInvites = invites.filter(isPendingInvite).length;
  const onboardedCount = children.length;
  const mandatoryDocs = (party?.requiredDocuments || []).filter((d) => d.mandatory);
  const draftDocProgress =
    status === 'DRAFT' && mandatoryDocs.length > 0
      ? {
          total: mandatoryDocs.length,
          uploaded: mandatoryDocs.filter((d) => d.uploaded).length,
          missing: mandatoryDocs.filter((d) => !d.uploaded),
        }
      : null;
  const rejectedDocs =
    status === 'INCOMPLETE' || status === 'SUBMITTED' || status === 'PENDING_APPROVAL'
      ? (party?.documents || []).filter((d) => d.status === 'REJECTED')
      : [];

  useEffect(() => {
    if (!session?.token || session.role === 'PLATFORM_ADMIN') return;
    let cancelled = false;
    setLoading(true);
    api<Party>('/api/onboarding/me', { token: session.token })
      .then(async (data) => {
        if (cancelled) return;
        setParty(data);
        if (data.status && data.status !== session.partyStatus) {
          setSession({ ...session, partyStatus: data.status, partyType: data.partyType || session.partyType });
        }
        if (data.partyType === 'MERCHANT' && data.status === 'ACTIVE') {
          try {
            const [inv, kids] = await Promise.all([
              api<FranchiseInvite[]>('/api/franchises/invites', { token: session.token }),
              api<FranchiseChild[]>('/api/franchises/children', { token: session.token }),
            ]);
            if (!cancelled) {
              setInvites(inv);
              setChildren(kids);
            }
          } catch {
            /* network section optional */
          }
        }
        if (data.status === 'ACTIVE') {
          const [bal, stmt] = await Promise.all([
            api<AgentAppPortalResponse>('/api/me/agent-balance', { token: session.token }).catch(
              () => null,
            ),
            api<AgentAppPortalResponse>('/api/me/agent-mini-statement', {
              token: session.token,
            }).catch(() => null),
          ]);
          if (!cancelled) {
            setBalance(bal);
            setStatement(stmt);
          }
        }
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Failed to load application');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [session?.token, session?.role]);

  const rows = useMemo(() => agentMiniStatementRows(statement?.data), [statement]);
  const buckets = useMemo(() => bucketByDay(rows), [rows]);
  const totals = useMemo(() => {
    const credit = buckets.reduce((sum, b) => sum + b.credit, 0);
    const debit = buckets.reduce((sum, b) => sum + b.debit, 0);
    const fee = buckets.reduce((sum, b) => sum + b.fee, 0);
    const count = buckets.reduce((sum, b) => sum + b.count, 0);
    const turnover = credit + debit;
    return {
      credit,
      debit,
      fee,
      count,
      creditShare: turnover > 0 ? (credit / turnover) * 100 : 0,
    };
  }, [buckets]);

  const closingTrend = useMemo(
    () => buckets.map((b) => b.closing).filter((v): v is number => v != null),
    [buckets],
  );

  const topFranchises = useMemo(() => {
    const ranked = children
      .filter((c) => (c.commissionRatePercent ?? 0) > 0)
      .sort((a, b) => (b.commissionRatePercent ?? 0) - (a.commissionRatePercent ?? 0))
      .slice(0, 5);
    const max = ranked.length ? ranked[0].commissionRatePercent ?? 1 : 1;
    return ranked.map((c) => ({ child: c, percent: ((c.commissionRatePercent ?? 0) / max) * 100 }));
  }, [children]);

  const avgCommission = useMemo(() => {
    const rates = children
      .map((c) => c.commissionRatePercent)
      .filter((v): v is number => v != null && v > 0);
    if (!rates.length) return 0;
    return rates.reduce((a, b) => a + b, 0) / rates.length;
  }, [children]);

  if (!session) return <Navigate to="/login" replace />;
  if (session.role === 'PLATFORM_ADMIN') return <Navigate to="/admin" replace />;

  const balStr = agentBalanceValue(balance);
  const liveBal = balStr !== '—';
  const hasSeries = buckets.length > 0;
  const chartLabels = buckets.map((b) => b.label);

  return (
    <div className="portal-page">
      <PageHeader
        eyebrow={badgeLabel}
        title={`Welcome, ${party?.fullName || session.fullName || 'user'}`}
        subtitle={statusHint(status)}
        actions={<span className={`status status-${status}`}>{status}</span>}
      />

      {error && <p className="api-banner">{error}</p>}
      {party?.discrepancyNote && (
        <div className="alert alert-info">{party.discrepancyNote}</div>
      )}
      {party?.rejectionReason && (
        <div className="alert alert-error">Rejected: {party.rejectionReason}</div>
      )}
      {draftDocProgress && draftDocProgress.missing.length > 0 && (
        <div className="alert alert-warn">
          <strong>Documents in progress</strong>
          {' — '}
          {draftDocProgress.uploaded} of {draftDocProgress.total} required files uploaded.
          You can add what you have now; submit opens only when the rest are attached.
          <ul className="dash-doc-miss-list">
            {draftDocProgress.missing.map((d) => (
              <li key={d.documentCode}>{d.documentLabel}</li>
            ))}
          </ul>
          <Link className="btn btn-primary btn-sm" to="/onboarding#documents">
            Upload documents
          </Link>
        </div>
      )}
      {rejectedDocs.length > 0 && (
        <div className="alert alert-error">
          <strong>Document(s) rejected</strong>
          {' — re-upload only these files. Your application is still open.'}
          <ul className="dash-doc-miss-list">
            {rejectedDocs.map((d) => (
              <li key={d.documentCode}>
                {d.documentCode}
                {d.reviewNote ? ` — ${d.reviewNote}` : ''}
              </li>
            ))}
          </ul>
          <Link className="btn btn-primary btn-sm" to="/onboarding#documents">
            Re-upload rejected documents
          </Link>
        </div>
      )}

      {loading ? (
        <p className="muted">Loading…</p>
      ) : (
        <>
          {status === 'ACTIVE' && (
            <>
              <div className="kpi-row animate-in">
                <KpiTile
                  label="Live wallet balance"
                  value={liveBal ? formatBalanceDisplay(balStr) : '—'}
                  icon="◉"
                  hint="AgentApp master account"
                  trend={closingTrend.length > 1 ? <Sparkline points={closingTrend} /> : undefined}
                />
                <KpiTile
                  label="Money in"
                  value={hasSeries ? formatMoney(totals.credit) : '—'}
                  icon="↓"
                  accent="success"
                  hint={hasSeries ? `${totals.count} movements on statement` : 'Awaiting statement'}
                  delta={
                    hasSeries
                      ? { text: `${Math.round(totals.creditShare)}% of turnover`, tone: 'up' }
                      : undefined
                  }
                />
                <KpiTile
                  label="Money out"
                  value={hasSeries ? formatMoney(totals.debit) : '—'}
                  icon="↑"
                  accent="warning"
                  hint={hasSeries ? `Fees ${formatMoney(totals.fee)}` : 'Awaiting statement'}
                  delta={
                    hasSeries
                      ? {
                          text: `${Math.round(100 - totals.creditShare)}% of turnover`,
                          tone: 'down',
                        }
                      : undefined
                  }
                />
                <KpiTile
                  label="Onboarded franchises"
                  value={onboardedCount}
                  icon="▣"
                  accent="teal"
                  hint={`${pendingInvites} invite${pendingInvites === 1 ? '' : 's'} pending`}
                  delta={
                    avgCommission > 0
                      ? { text: `avg ${avgCommission.toFixed(2)}%`, tone: 'flat' }
                      : undefined
                  }
                />
              </div>

              <div className="dash-grid animate-in animate-in-delay-1">
                <ChartCard
                  title="Money movement"
                  subtitle="Credits vs debits per day, from the live AgentApp mini-statement"
                  actions={
                    <Link className="btn btn-ghost btn-sm" to="/statement">
                      Statement
                    </Link>
                  }
                >
                  {hasSeries ? (
                    <AreaChart
                      labels={chartLabels}
                      height={250}
                      series={[
                        { name: 'Money in', color: 'var(--accent)', points: buckets.map((b) => b.credit) },
                        { name: 'Money out', color: 'var(--teal)', points: buckets.map((b) => b.debit) },
                      ]}
                    />
                  ) : (
                    <ChartEmpty message="No statement movements yet. Charts fill in as soon as AgentApp returns transactions." />
                  )}
                </ChartCard>

                <ChartCard
                  title="Earnings"
                  subtitle="Fees and commission captured on this statement"
                >
                  <strong className="kpi-value" style={{ fontSize: '1.9rem' }}>
                    {hasSeries ? formatMoney(totals.fee) : '—'}
                  </strong>
                  <p className="chart-card-sub" style={{ marginBottom: '0.5rem' }}>
                    {hasSeries
                      ? `${Math.round(totals.creditShare)}% of turnover came in as credits`
                      : 'Awaiting statement data'}
                  </p>
                  <GaugeChart
                    percent={totals.creditShare}
                    label="Credit share"
                    caption={
                      hasSeries
                        ? `Turnover ${compactNumber(totals.credit + totals.debit)} across ${totals.count} movements`
                        : undefined
                    }
                  />
                </ChartCard>
              </div>

              <div className="dash-grid dash-grid--even animate-in animate-in-delay-2">
                <ChartCard title="Daily volume" subtitle="In / out amounts per statement day">
                  {hasSeries ? (
                    <BarChart
                      labels={chartLabels}
                      height={220}
                      series={[
                        { name: 'In', color: 'var(--accent)', points: buckets.map((b) => b.credit) },
                        { name: 'Out', color: 'var(--teal)', points: buckets.map((b) => b.debit) },
                      ]}
                    />
                  ) : (
                    <ChartEmpty message="Daily volume appears once statement rows are available." />
                  )}
                </ChartCard>

                <ChartCard
                  title="Top franchises by commission"
                  subtitle={
                    avgCommission > 0
                      ? `Average locked rate ${avgCommission.toFixed(2)}%`
                      : 'No commission rates locked yet'
                  }
                  actions={
                    canManageNetwork ? (
                      <Link className="btn btn-ghost btn-sm" to="/franchises">
                        Manage
                      </Link>
                    ) : undefined
                  }
                >
                  {topFranchises.length > 0 ? (
                    <div className="rank-list">
                      <div className="rank-head">
                        <span>#</span>
                        <span>Franchise</span>
                        <span>Share</span>
                        <span style={{ justifySelf: 'end' }}>Rate</span>
                      </div>
                      {topFranchises.map(({ child, percent }, i) => (
                        <RankRow
                          key={child.id}
                          index={i + 1}
                          name={child.businessName || child.fullName || child.trackingId || '—'}
                          meta={child.commissionStatus || child.status}
                          percent={percent}
                          value={`${(child.commissionRatePercent ?? 0).toFixed(2)}%`}
                          color={i % 2 === 0 ? 'var(--accent)' : 'var(--teal)'}
                        />
                      ))}
                    </div>
                  ) : (
                    <ChartEmpty message="Lock a commission percentage on the Onboarded page to rank your franchises here." />
                  )}
                </ChartCard>
              </div>
            </>
          )}

          <section className="glass-panel animate-in">
            <div className="panel-header">
              <div>
                <h2 className="panel-title">Entity</h2>
                <p className="muted panel-subtitle">{party?.businessName || '—'}</p>
              </div>
            </div>
            <div className="ops-meta-grid">
              <div>
                <span className="muted">Entity type</span>
                <strong>{party?.entityType || party?.partyType || '—'}</strong>
              </div>
              <div>
                <span className="muted">Tracking ID</span>
                <strong className="mono">{party?.trackingId || '—'}</strong>
              </div>
              <div>
                <span className="muted">Submitted</span>
                <strong>{fmtDate(party?.submittedAt)}</strong>
              </div>
              <div>
                <span className="muted">Approved</span>
                <strong>{fmtDate(party?.approvedAt)}</strong>
              </div>
              <div>
                <span className="muted">Contact</span>
                <strong>{party?.email || party?.phone || '—'}</strong>
              </div>
              <div>
                <span className="muted">Mobile</span>
                <strong className="mono">{party?.phone || '—'}</strong>
              </div>
            </div>
          </section>

          {status === 'ACTIVE' && (
            <section className="glass-panel animate-in animate-in-delay-1">
              <div className="panel-header">
                <div>
                  <h2 className="panel-title">Master DFS account</h2>
                  <p className="muted panel-subtitle">
                    {provisionHint(provision) || 'Account identity used for AgentApp balance and CMS cards.'}
                  </p>
                </div>
                {provision && (
                  <span
                    className={`status status-${
                      provision === 'SUCCESS' ? 'ACTIVE' : provision === 'FAILED' ? 'REJECTED' : 'SUBMITTED'
                    }`}
                  >
                    {provision}
                  </span>
                )}
              </div>
              <div className="ops-meta-grid">
                <div>
                  <span className="muted">DFS account / relationship ID</span>
                  <strong className="mono">{party?.dfsAccountId || '—'}</strong>
                </div>
                <div>
                  <span className="muted">CMS Relationship #</span>
                  <strong className="mono">{party?.cmsRelationshipNum || '—'}</strong>
                </div>
                <div>
                  <span className="muted">Level</span>
                  <strong>{party?.levelCode || 'L4'}</strong>
                </div>
                <div>
                  <span className="muted">Live balance</span>
                  <strong>{liveBal ? formatBalanceDisplay(balStr) : '—'}</strong>
                </div>
                <div>
                  <span className="muted">IBAN / QR</span>
                  <strong className="muted">Pending AgentApp account-detail API</strong>
                </div>
                <div>
                  <span className="muted">Provisioned</span>
                  <strong>{fmtDate(party?.accountProvisionedAt)}</strong>
                </div>
              </div>
              {party?.accountProvisionError && provision !== 'SUCCESS' && (
                <div className="alert alert-info" style={{ marginTop: '0.75rem' }}>
                  {party.accountProvisionError}
                </div>
              )}
              <div className="actions" style={{ marginTop: '1rem' }}>
                <Link className="btn btn-primary btn-sm" to="/balance">
                  Open balance
                </Link>
                <Link className="btn btn-ghost btn-sm" to="/statement">
                  Statement
                </Link>
                <Link className="btn btn-ghost btn-sm" to="/cards">
                  Cards
                </Link>
              </div>
            </section>
          )}

          {canManageNetwork && (
            <section className="glass-panel animate-in animate-in-delay-2">
              <div className="panel-header">
                <div>
                  <h2 className="panel-title">Network</h2>
                  <p className="muted panel-subtitle">
                    Invites stay pending until the franchise completes signup; then they move to Onboarded.
                  </p>
                </div>
              </div>
              <div
                className="stat-row"
                style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap', marginBottom: '1rem' }}
              >
                <div className="stat-chip">
                  <span className="stat-label">Pending invites</span>
                  <strong style={{ fontSize: '1.4rem' }}>{pendingInvites}</strong>
                </div>
                <div className="stat-chip">
                  <span className="stat-label">Onboarded franchises</span>
                  <strong style={{ fontSize: '1.4rem' }}>{onboardedCount}</strong>
                </div>
              </div>
              <div className="actions">
                <Link className="btn btn-primary" to="/invites">
                  Manage invites
                </Link>
                <Link className="btn btn-ghost" to="/franchises">
                  Onboarded & commission
                </Link>
              </div>
            </section>
          )}

          {kycTotal > 0 && (
            <section className="glass-panel animate-in">
              <div className="dash-kyc-head">
                <h2 className="panel-title" style={{ margin: 0 }}>
                  Partner mobile KYC
                </h2>
                <span className="muted">
                  {kycDone}/{kycTotal} complete
                </span>
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
                      <div className="muted">
                        {u.phone}
                        {u.signatureUploaded ? ' · signature on file' : ' · signature missing'}
                      </div>
                    </div>
                    <div className="actions" style={{ marginTop: 0, alignItems: 'center' }}>
                      {!u.signatureUploaded && !u.bankVisitRequired && (
                        <Link className="btn btn-ghost btn-sm" to={`/signature/${u.id}`}>
                          Upload signature
                        </Link>
                      )}
                      {u.signatureUploaded && (
                        <Link className="btn btn-ghost btn-sm" to={`/signature/${u.id}`}>
                          Replace signature
                        </Link>
                      )}
                      <span
                        className={`status status-${
                          u.status === 'KYC_COMPLETED' ? 'ACTIVE' : u.status === 'FAILED' ? 'REJECTED' : 'SUBMITTED'
                        }`}
                      >
                        {u.status}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            </section>
          )}

          <div className="actions" style={{ marginTop: '0.25rem' }}>
            {showApp && (
              <Link className="btn btn-primary" to="/onboarding">
                {status === 'DRAFT' || status === 'REJECTED' ? 'Continue application' : 'View application'}
              </Link>
            )}
            <Link className="btn btn-ghost" to="/profile">
              Profile
            </Link>
          </div>
        </>
      )}
    </div>
  );
}
