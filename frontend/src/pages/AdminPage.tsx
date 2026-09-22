import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Navigate, useNavigate, useParams } from 'react-router-dom';
import { api, apiUrl } from '../lib/api';
import { useAuth } from '../auth/AuthContext';

type Brand = { id: number; code: string; name: string; merchantCount: number };
type AppUser = {
  id: number;
  phone: string;
  email?: string;
  fullName: string;
  status: string;
  appInviteUrl?: string;
  failureReason?: string;
  kycFailCount?: number;
  kycAttemptsRemaining?: number;
  bankVisitRequired?: boolean;
  signatureUploaded?: boolean;
  cnicNumber?: string;
  cnicFullName?: string;
  manualKycApproveReason?: string;
};
type Invite = {
  id: number;
  email: string;
  fullName: string;
  status: string;
  inviteUrl?: string;
  authorizedToOperate?: boolean;
};
type Doc = {
  id: number;
  documentCode: string;
  originalName: string;
  status: string;
  contentType?: string;
  reviewNote?: string;
};
type Person = { id: number; fullName: string; roleType: string; authorizedToOperate?: boolean; phone?: string; email?: string };
type Party = {
  id: number;
  publicId: string;
  trackingId?: string;
  partyType: string;
  status: string;
  fullName: string;
  email: string;
  phone?: string;
  businessName?: string;
  entityType?: string;
  brandId?: number;
  brandCode?: string;
  brandName?: string;
  sanctionsStatus?: string;
  identityVerificationStatus?: string;
  eddRequired?: boolean;
  decisionDueAt?: string;
  submittedAt?: string;
  clientIp?: string;
  geoLocation?: string;
  discrepancyNote?: string;
  tatOverdue?: boolean;
  partnerKycTotal?: number;
  partnerKycCompleted?: number;
  partnerInvites?: Invite[];
  partnerAppUsers?: AppUser[];
  associatedPersons?: Person[];
  documents?: Doc[];
  docsPending?: number;
  docsRejected?: number;
  docsReadyForApprove?: boolean;
  accountProvisionStatus?: string;
  dfsAccountId?: string;
  cmsRelationshipNum?: string;
  accountProvisionError?: string;
};

const STATUS_FILTERS = [
  { value: 'PENDING_APPROVAL', label: 'Ready to approve' },
  { value: 'SUBMITTED', label: 'Awaiting app KYC' },
  { value: 'INCOMPLETE', label: 'Incomplete (re-upload)' },
  { value: 'DRAFT', label: 'Draft' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'REJECTED', label: 'Rejected (full app)' },
  { value: 'ALL', label: 'All' },
];

function isPreviewable(doc: Doc) {
  const ct = (doc.contentType || '').toLowerCase();
  if (ct.startsWith('image/') || ct.startsWith('video/') || ct === 'application/pdf') return true;
  return /\.(jpe?g|png|gif|webp|mp4|webm|mov|3gp|pdf)$/i.test(doc.originalName || '');
}

function humanizeDocKind(code: string): string {
  const stripped = code.replace(/^PARTNER_\d+_/, '').replace(/^APP_\d+_/, '');
  return stripped.replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());
}

type DocGroup = { title: string; docs: Doc[] };

function buildDocGroups(party: Party): DocGroup[] {
  const docs = party.documents ?? [];
  const used = new Set<number>();
  const groups: DocGroup[] = [];

  const firm = docs.filter((d) => !d.documentCode.startsWith('PARTNER_') && !d.documentCode.startsWith('APP_'));
  if (firm.length) {
    firm.forEach((d) => used.add(d.id));
    groups.push({ title: 'Firm / entity documents', docs: firm });
  }

  for (const p of party.associatedPersons ?? []) {
    const list = docs.filter((d) => d.documentCode.startsWith(`PARTNER_${p.id}_`));
    list.forEach((d) => used.add(d.id));
    if (list.length) groups.push({ title: `${p.fullName} — portal uploads`, docs: list });
  }

  for (const u of party.partnerAppUsers ?? []) {
    const list = docs.filter((d) => d.documentCode.startsWith(`APP_${u.id}_`));
    list.forEach((d) => used.add(d.id));
    if (list.length) {
      groups.push({ title: `${u.fullName} (${u.phone}) — mobile KYC`, docs: list });
    }
  }

  const rest = docs.filter((d) => !used.has(d.id));
  if (rest.length) groups.push({ title: 'Other documents', docs: rest });

  return groups;
}

function kycPct(p: Party) {
  const total = p.partnerKycTotal || 0;
  if (total === 0) return null;
  return Math.round(((p.partnerKycCompleted || 0) / total) * 100);
}

function formatTat(iso?: string) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString(undefined, { day: '2-digit', month: 'short', year: 'numeric' });
}

function statusLabel(status: string) {
  return status.replace(/_/g, ' ');
}

function CloseIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
      <path d="M6 6l12 12M18 6L6 18" strokeLinecap="round" />
    </svg>
  );
}

function EyeIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden>
      <path d="M2.5 12s3.5-6.5 9.5-6.5S21.5 12 21.5 12s-3.5 6.5-9.5 6.5S2.5 12 2.5 12z" />
      <circle cx="12" cy="12" r="2.75" />
    </svg>
  );
}

export function AdminPage() {
  const { session } = useAuth();
  const navigate = useNavigate();
  const { partyId: partyIdParam } = useParams();
  const reviewPartyId = partyIdParam ? Number(partyIdParam) : null;
  const [brands, setBrands] = useState<Brand[]>([]);
  const [brandId, setBrandId] = useState<number | 'ALL'>('ALL');
  const [status, setStatus] = useState('PENDING_APPROVAL');
  const [tatOnly, setTatOnly] = useState(false);
  const [queue, setQueue] = useState<Party[]>([]);
  const [selected, setSelected] = useState<Party | null>(null);
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');
  const [rejectReason, setRejectReason] = useState('');
  const [manualKycReason, setManualKycReason] = useState('');
  const [manualKycUserId, setManualKycUserId] = useState<number | null>(null);
  const [discrepancy, setDiscrepancy] = useState('');
  const [viewerDocId, setViewerDocId] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);
  const [amlRefreshing, setAmlRefreshing] = useState(false);

  const token = session?.token;

  const loadBrands = useCallback(async () => {
    if (!token) return;
    const data = await api<Brand[]>('/api/admin/brands', { token });
    setBrands(data);
  }, [token]);

  const refresh = useCallback(async () => {
    if (!token) return;
    setLoading(true);
    try {
      const params = new URLSearchParams();
      if (brandId !== 'ALL') params.set('brandId', String(brandId));
      if (status !== 'ALL') params.set('status', status);
      const qs = params.toString();
      const data = await api<Party[]>(`/api/admin/parties${qs ? `?${qs}` : ''}`, { token });
      setQueue(data);
    } finally {
      setLoading(false);
    }
  }, [token, brandId, status]);

  useEffect(() => {
    if (session?.role === 'PLATFORM_ADMIN') {
      loadBrands().catch((err) => setError(err instanceof Error ? err.message : 'Failed to load brands'));
    }
  }, [session, loadBrands]);

  useEffect(() => {
    if (session?.role === 'PLATFORM_ADMIN') {
      refresh().catch((err) => setError(err instanceof Error ? err.message : 'Failed to load queue'));
    }
  }, [session, refresh]);

  const filtered = useMemo(() => {
    if (!tatOnly) return queue;
    return queue.filter((p) => p.tatOverdue);
  }, [queue, tatOnly]);

  const stats = useMemo(() => {
    const pending = queue.filter((p) => p.status === 'PENDING_APPROVAL').length;
    const awaitingKyc = queue.filter((p) => p.status === 'SUBMITTED').length;
    const incomplete = queue.filter((p) => p.status === 'INCOMPLETE').length;
    const overdue = queue.filter((p) => p.tatOverdue).length;
    return { pending, awaitingKyc, incomplete, overdue, total: filtered.length };
  }, [queue, filtered]);

  const fetchDocBlob = useCallback(async (docId: number): Promise<string> => {
    if (!session?.token) throw new Error('Not signed in');
    const res = await fetch(apiUrl(`/api/admin/documents/${docId}/file`), {
      headers: { Authorization: `Bearer ${session.token}` },
    });
    if (!res.ok) throw new Error('Could not load document');
    const blob = await res.blob();
    return URL.createObjectURL(blob);
  }, [session?.token]);

  useEffect(() => {
    if (!selected) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== 'Escape') return;
      if (viewerDocId != null) {
        setViewerDocId(null);
        return;
      }
      navigate('/admin');
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [selected, viewerDocId, navigate]);

  useEffect(() => {
    if (selected) window.scrollTo({ top: 0, behavior: 'smooth' });
  }, [selected?.id]);

  useEffect(() => {
    if (session?.role !== 'PLATFORM_ADMIN') return;
    if (reviewPartyId == null || Number.isNaN(reviewPartyId)) {
      setSelected(null);
      setViewerDocId(null);
      return;
    }
    void open(reviewPartyId).catch((err) => {
      setError(err instanceof Error ? err.message : 'Failed to open case');
      navigate('/admin', { replace: true });
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps -- open on route party id only
  }, [session?.role, reviewPartyId]);

  if (!session) return <Navigate to="/login" replace />;
  if (session.role !== 'PLATFORM_ADMIN') return <Navigate to="/" replace />;

  async function open(id: number) {
    setError(''); setOk('');
    const data = await api<Party>(`/api/admin/parties/${id}`, { token: session!.token });
    setSelected(data);
    setViewerDocId(null);
    setDiscrepancy(data.discrepancyNote || '');
  }

  function goQueue() {
    setSelected(null);
    setViewerDocId(null);
    navigate('/admin');
  }

  function goReview(id: number) {
    navigate(`/admin/review/${id}`);
  }

  async function approve(id: number) {
    setError(''); setOk('');
    try {
      const res = await api<{ temporaryPassword: string; message: string; accountProvisionStatus?: string }>(
        `/api/admin/parties/${id}/approve`,
        { method: 'POST', token: session!.token },
      );
      setOk(`${res.message} Temp password: ${res.temporaryPassword}`);
      goQueue();
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Approve failed');
    }
  }

  async function retryProvision(id: number) {
    setError(''); setOk('');
    try {
      const data = await api<Party>(`/api/admin/parties/${id}/retry-account-provision`, {
        method: 'POST', token: session!.token,
      });
      setSelected(data);
      setOk(`Account provision: ${data.accountProvisionStatus}${data.dfsAccountId ? ` · ${data.dfsAccountId}` : ''}`);
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Retry failed');
    }
  }

  async function linkCmsRelationship(id: number) {
    const rel = window.prompt(
      'CMS Relationship # (same as AgentApp /card/inquiry)',
      selected?.cmsRelationshipNum || '',
    );
    if (rel == null) return;
    if (!rel.trim()) {
      setError('Relationship number required');
      return;
    }
    setError('');
    setOk('');
    try {
      const data = await api<Party>(`/api/admin/parties/${id}/cms-relationship`, {
        method: 'PUT',
        token: session!.token,
        body: JSON.stringify({ relationshipNum: rel.trim() }),
      });
      setSelected(data);
      setOk(`CMS relationship linked: ${data.cmsRelationshipNum}`);
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'CMS relationship link failed');
    }
  }

  async function reject(id: number) {
    if (!rejectReason.trim()) { setError('Rejection reason required'); return; }
    setError(''); setOk('');
    try {
      await api(`/api/admin/parties/${id}/reject`, {
        method: 'POST', token: session!.token,
        body: JSON.stringify({ reason: rejectReason }),
      });
      setOk('Application rejected');
      setRejectReason('');
      goQueue();
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Reject failed');
    }
  }

  async function clearSanctions(id: number) {
    const notes = window.prompt('Reason to CLEAR this party (required). This overrides an AML HIT.');
    if (!notes || !notes.trim()) return;
    await api(`/api/admin/parties/${id}/sanctions`, {
      method: 'POST', token: session!.token,
      body: JSON.stringify({ status: 'CLEAR', notes: notes.trim() }),
    });
    await open(id);
  }

  async function rescreenSanctions(id: number) {
    await api(`/api/admin/parties/${id}/sanctions/screen`, {
      method: 'POST', token: session!.token,
    });
    await open(id);
  }

  async function refreshAmlWatchlist() {
    if (!session?.token) return;
    if (!window.confirm('Download official OFAC + UN lists and replace prior OFAC/UN rows? INTERNAL demo CNICs are kept.')) {
      return;
    }
    setError('');
    setOk('');
    setAmlRefreshing(true);
    try {
      const res = await api<{
        message?: string;
        ofacPrimaryNames?: number;
        ofacAliasNames?: number;
        unNames?: number;
        activeTotal?: number;
        errors?: string[];
        elapsedMs?: number;
      }>('/api/admin/aml/watchlist/refresh', { method: 'POST', token: session.token });
      const errs = res.errors?.length ? ` (${res.errors.join('; ')})` : '';
      setOk(
        `${res.message || 'Watchlist refreshed'}: OFAC ${res.ofacPrimaryNames ?? 0}+${res.ofacAliasNames ?? 0} aliases, ` +
          `UN ${res.unNames ?? 0}, active ${res.activeTotal ?? 0} (${res.elapsedMs ?? 0} ms)${errs}`,
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : 'AML watchlist refresh failed');
    } finally {
      setAmlRefreshing(false);
    }
  }

  async function sendDiscrepancy(id: number) {
    if (!discrepancy.trim()) { setError('Discrepancy note required'); return; }
    setError(''); setOk('');
    try {
      await api(`/api/admin/parties/${id}/discrepancy`, {
        method: 'POST', token: session!.token,
        body: JSON.stringify({ note: discrepancy }),
      });
      setOk('Discrepancy note sent to applicant');
      await open(id);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed');
    }
  }

  async function resendInvite(inviteId: number) {
    setError(''); setOk('');
    try {
      await api(`/api/admin/partner-invites/${inviteId}/resend`, {
        method: 'POST', token: session!.token,
      });
      setOk('Legacy invite re-sent (portal KYC disabled — prefer app users)');
      if (selected) await open(selected.id);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Resend failed');
    }
  }

  async function resendAppInvite(appUserId: number) {
    setError(''); setOk('');
    try {
      await api(`/api/admin/partner-app-users/${appUserId}/resend`, {
        method: 'POST', token: session!.token,
      });
      setOk('Mobile app invite re-sent (check mail / backend logs)');
      if (selected) await open(selected.id);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Resend failed');
    }
  }

  async function markAppKycComplete(appUserId: number) {
    setError(''); setOk('');
    try {
      await api(`/api/admin/partner-app-users/${appUserId}/mark-kyc-complete`, {
        method: 'POST', token: session!.token,
      });
      setOk('Marked app KYC complete (stub — not for bank-visit cases)');
      if (selected) await open(selected.id);
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed');
    }
  }

  async function manualKycApprove(appUserId: number) {
    if (!manualKycReason.trim()) {
      setError('Manual KYC approve requires a written reason (bank/office visit)');
      return;
    }
    setError(''); setOk('');
    try {
      await api(`/api/admin/partner-app-users/${appUserId}/manual-kyc-approve`, {
        method: 'POST',
        token: session!.token,
        body: JSON.stringify({ reason: manualKycReason.trim() }),
      });
      setOk('Partner KYC manually approved (bank visit)');
      setManualKycReason('');
      setManualKycUserId(null);
      if (selected) await open(selected.id);
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Manual approve failed');
    }
  }

  async function reviewDoc(docId: number, approveDoc: boolean) {
    const path = approveDoc
      ? `/api/admin/documents/${docId}/approve`
      : `/api/admin/documents/${docId}/reject`;
    await api(path, {
      method: 'POST',
      token: session!.token,
      body: approveDoc ? undefined : JSON.stringify({ note: 'Rejected by backoffice — re-upload this document only' }),
    });
    if (selected) await open(selected.id);
  }

  const canDecide =
    !!selected &&
    (selected.status === 'PENDING_APPROVAL' ||
      selected.status === 'SUBMITTED' ||
      selected.status === 'INCOMPLETE');

  const onReviewPage = reviewPartyId != null && !Number.isNaN(reviewPartyId);

  return (
    <div className="ops-shell">
      {!onReviewPage && (
      <>
      <header className="ops-topbar">
        <div>
          <div className="ops-eyebrow">APPROVER CONSOLE · DFS CONNECT</div>
          <h1 className="ops-title">KYC review queue</h1>
          <p className="muted ops-sub">Click a merchant to open Review Merchant</p>
        </div>
        <div className="ops-stat-row" aria-label="Queue summary">
          <div className="ops-stat"><strong>{stats.pending}</strong><span>Ready</span></div>
          <div className="ops-stat"><strong>{stats.awaitingKyc}</strong><span>App KYC</span></div>
          <div className="ops-stat"><strong>{stats.incomplete}</strong><span>Incomplete</span></div>
          <div className="ops-stat"><strong>{stats.overdue}</strong><span>Overdue</span></div>
          <div className="ops-stat"><strong>{stats.total}</strong><span>In view</span></div>
        </div>
      </header>

      {error && <div className="alert alert-error">{error}</div>}
      {ok && <div className="alert alert-ok">{ok}</div>}

      <div className="ops-toolbar">
        <div className="ops-brand-switcher" role="tablist" aria-label="Brand">
          <button
            type="button"
            className={`ops-chip ${brandId === 'ALL' ? 'active' : ''}`}
            onClick={() => setBrandId('ALL')}
          >
            All brands
          </button>
          {brands.map((b) => (
            <button
              key={b.id}
              type="button"
              className={`ops-chip ${brandId === b.id ? 'active' : ''}`}
              onClick={() => setBrandId(b.id)}
            >
              {b.name}
              <em>{b.merchantCount}</em>
            </button>
          ))}
        </div>
        <div className="ops-filters">
          <select value={status} onChange={(e) => setStatus(e.target.value)} aria-label="Status">
            {STATUS_FILTERS.map((s) => (
              <option key={s.value} value={s.value}>{s.label}</option>
            ))}
          </select>
          <label className="ops-check">
            <input type="checkbox" checked={tatOnly} onChange={(e) => setTatOnly(e.target.checked)} />
            TAT overdue
          </label>
          <button className="btn btn-ghost btn-sm" type="button" disabled={loading} onClick={() => void refresh()}>
            {loading ? 'Refreshing…' : 'Refresh'}
          </button>
          <button
            className="btn btn-ghost btn-sm"
            type="button"
            disabled={amlRefreshing}
            onClick={() => void refreshAmlWatchlist()}
            title="Download OFAC SDN/ALT + UN XML into local watchlist"
          >
            {amlRefreshing ? 'Importing lists…' : 'Refresh AML lists'}
          </button>
        </div>
      </div>

      <section className="ops-queue panel panel--full">
        <div className="ops-queue-head">
          <h3 className="ops-section-title">Application queue</h3>
          <span className="muted ops-queue-count">{filtered.length} case{filtered.length === 1 ? '' : 's'}</span>
        </div>
        <div className="ops-table-wrap">
          <table className="table ops-table">
            <thead>
              <tr>
                <th>Tracking</th>
                <th>Business</th>
                <th>Status</th>
                <th>KYC</th>
                <th>TAT</th>
              </tr>
            </thead>
            <tbody>
              {filtered.length === 0 && (
                <tr>
                  <td colSpan={5} className="ops-empty muted">No applications for this filter</td>
                </tr>
              )}
              {filtered.map((p) => {
                const pct = kycPct(p);
                return (
                  <tr
                    key={p.id}
                    className="ops-row-click"
                    onClick={() => goReview(p.id)}
                  >
                    <td>
                      <div className="ops-track-cell">
                        <strong className="ops-mono">{p.trackingId || p.publicId.slice(0, 8)}</strong>
                        {p.tatOverdue && <span className="ops-pill danger">OVERDUE</span>}
                      </div>
                      {p.brandCode && <span className="ops-pill muted-pill">{p.brandCode}</span>}
                    </td>
                    <td>
                      <div className="ops-biz-name">{p.businessName || p.fullName}</div>
                      <div className="ops-biz-meta muted">
                        {[p.entityType || p.partyType, p.email].filter(Boolean).join(' · ')}
                      </div>
                    </td>
                    <td>
                      <span className={`status status-${p.status}`}>{statusLabel(p.status)}</span>
                    </td>
                    <td>
                      {pct == null ? (
                        <span className="muted">—</span>
                      ) : (
                        <div className="ops-progress" title={`${p.partnerKycCompleted}/${p.partnerKycTotal}`}>
                          <div className="ops-progress-bar"><span style={{ width: `${pct}%` }} /></div>
                          <span className="ops-progress-label">{p.partnerKycCompleted}/{p.partnerKycTotal}</span>
                        </div>
                      )}
                    </td>
                    <td>
                      <span className={p.tatOverdue ? 'ops-tat overdue' : 'ops-tat'}>
                        {formatTat(p.decisionDueAt)}
                      </span>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </section>
      </>
      )}

      {error && onReviewPage && <div className="alert alert-error">{error}</div>}
      {ok && onReviewPage && <div className="alert alert-ok">{ok}</div>}

      {onReviewPage && selected && (
        <div className="ops-case-page">
          <header className="ops-case-bar">
            <button
              type="button"
              className="btn btn-ghost btn-sm"
              onClick={goQueue}
            >
              ← Back to queue
            </button>
            <div className="ops-case-bar-main">
              <div className="ops-case-kicker">
                <span className="ops-eyebrow">REVIEW MERCHANT</span>
                <span className="ops-pill muted-pill">{selected.brandName || selected.brandCode || 'Brand'}</span>
                <span className="ops-mono muted">{selected.trackingId || selected.publicId.slice(0, 8)}</span>
              </div>
              <h1 className="ops-case-title">Review Merchant</h1>
              <h2 className="ops-case-merchant">{selected.businessName || selected.fullName}</h2>
              <p className="muted ops-case-sub">
                {[selected.email, selected.phone, selected.entityType || selected.partyType]
                  .filter(Boolean)
                  .join(' · ')}
              </p>
            </div>
            <button
              type="button"
              className="ops-drawer-close"
              aria-label="Close case"
              onClick={goQueue}
            >
              <CloseIcon />
              <span>Close</span>
            </button>
          </header>

          <div className="ops-meta-strip">
            <div>
              <span className="muted">Status</span>
              <strong className={`status status-${selected.status}`}>{statusLabel(selected.status)}</strong>
            </div>
            <div>
              <span className="muted">Sanctions</span>
              <strong>{selected.sanctionsStatus || '—'}</strong>
            </div>
            <div>
              <span className="muted">Identity</span>
              <strong>{selected.identityVerificationStatus || '—'}</strong>
            </div>
            <div>
              <span className="muted">TAT due</span>
              <strong className={selected.tatOverdue ? 'ops-tat overdue' : undefined}>
                {formatTat(selected.decisionDueAt)}
              </strong>
            </div>
            <div>
              <span className="muted">IP / Geo</span>
              <strong>{selected.clientIp || '—'}{selected.geoLocation ? ` · ${selected.geoLocation}` : ''}</strong>
            </div>
          </div>

          {(selected.partnerKycTotal || 0) > 0 && (
            <section className="ops-drawer-section">
              <div className="ops-drawer-section-head">
                <h3>Partners &amp; signatures</h3>
                <div className="ops-progress large">
                  <div className="ops-progress-bar">
                    <span style={{ width: `${kycPct(selected) || 0}%` }} />
                  </div>
                  <span className="ops-progress-label">
                    {selected.partnerKycCompleted}/{selected.partnerKycTotal} KYC
                  </span>
                </div>
              </div>
              <div className="ops-partner-grid">
                {(selected.partnerAppUsers || []).map((u) => {
                  const docs = selected.documents || [];
                  const sigDoc = docs.find((d) => d.documentCode === `APP_${u.id}_SIGNATURE`);
                  const sheetPng = docs.find((d) => d.documentCode === `APP_${u.id}_SIGNATURE_SHEET_PNG`);
                  const sheetJpeg = docs.find((d) => d.documentCode === `APP_${u.id}_SIGNATURE_SHEET_JPEG`);
                  const sheetPdf = docs.find((d) => d.documentCode === `APP_${u.id}_SIGNATURE_SHEET_PDF`);
                  const sheetPreview = sheetPng || sheetJpeg;
                  return (
                    <article className="ops-partner-card" key={u.id}>
                      <div className="ops-partner-card-top">
                        <div>
                          <strong>{u.fullName}</strong>
                          <div className="muted ops-partner-meta">{u.phone} · {u.email || '—'}</div>
                          <span className={`status status-${u.status === 'KYC_COMPLETED' ? 'ACTIVE' : u.status === 'FAILED' ? 'REJECTED' : 'PENDING_APPROVAL'}`}>
                            {statusLabel(u.status)}
                          </span>
                        </div>
                        <div className="ops-partner-actions">
                          {u.status !== 'KYC_COMPLETED' && !u.bankVisitRequired && u.status !== 'BANK_VISIT_REQUIRED' && (
                            <>
                              <button className="btn btn-ghost btn-sm" type="button" onClick={() => void resendAppInvite(u.id)}>Re-send invite</button>
                              <button className="btn btn-ghost btn-sm" type="button" onClick={() => void markAppKycComplete(u.id)}>Mark KYC done</button>
                            </>
                          )}
                          {(u.bankVisitRequired || u.status === 'BANK_VISIT_REQUIRED' || u.status === 'FAILED') && u.status !== 'KYC_COMPLETED' && (
                            <button className="btn btn-primary btn-sm" type="button" onClick={() => { setManualKycUserId(u.id); setManualKycReason(''); }}>
                              Manual KYC
                            </button>
                          )}
                        </div>
                      </div>
                      {u.failureReason && <p className="muted ops-inline-note">Fail: {u.failureReason}</p>}
                      {u.bankVisitRequired && <div className="alert alert-info ops-inline-alert">Bank visit required — manual approve with reason.</div>}
                      {u.appInviteUrl && <div className="invite-link">{u.appInviteUrl}</div>}
                      {manualKycUserId === u.id && (
                        <div className="ops-manual-kyc">
                          <textarea rows={2} placeholder="Bank visit / verification reason (required)" value={manualKycReason} onChange={(e) => setManualKycReason(e.target.value)} />
                          <div className="actions">
                            <button className="btn btn-primary btn-sm" type="button" onClick={() => void manualKycApprove(u.id)}>Confirm</button>
                            <button className="btn btn-ghost btn-sm" type="button" onClick={() => setManualKycUserId(null)}>Cancel</button>
                          </div>
                        </div>
                      )}
                      <div className="ops-sig-slot">
                        {sheetPreview || sigDoc ? (
                          <SpecimenSignatureCard
                            partner={u}
                            sheetDoc={sheetPreview || sigDoc!}
                            sheetPdf={sheetPdf}
                            sheetPng={sheetPng}
                            sheetJpeg={sheetJpeg}
                            originalDoc={sigDoc}
                            token={session.token}
                            fetchBlob={fetchDocBlob}
                            onOpen={(id) => setViewerDocId(id)}
                          />
                        ) : (
                          <p className="muted ops-sig-empty">Specimen signature — not uploaded yet</p>
                        )}
                      </div>
                    </article>
                  );
                })}
              </div>
              {(selected.partnerInvites || []).length > 0 && (
                <div className="ops-legacy-invites">
                  <p className="muted">Legacy portal invites</p>
                  {(selected.partnerInvites || []).map((inv) => (
                    <div className="ops-legacy-row" key={`leg-${inv.id}`}>
                      <div>
                        <strong>{inv.fullName}</strong>
                        <span className="muted"> · {inv.email} · {inv.status}</span>
                      </div>
                      {inv.status !== 'COMPLETED' && inv.status !== 'CANCELLED' && (
                        <button className="btn btn-ghost btn-sm" type="button" onClick={() => void resendInvite(inv.id)}>
                          Re-send
                        </button>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </section>
          )}

          <section className="ops-drawer-section">
            <div className="ops-drawer-section-head">
              <h3>Roster</h3>
            </div>
            {(selected.associatedPersons || []).length === 0 ? (
              <p className="muted">No associated persons (sole prop uses owner mobile KYC).</p>
            ) : (
              <div className="ops-roster-list">
                {(selected.associatedPersons || []).map((p) => (
                  <div className="ops-roster-item" key={p.id}>
                    <strong>{p.fullName}</strong>
                    <span className="muted">
                      {p.roleType}{p.phone ? ` · ${p.phone}` : ''}{p.email ? ` · ${p.email}` : ''}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </section>

          <section className="ops-drawer-section">
            <div className="ops-drawer-section-head">
              <h3>Documents</h3>
              <p className="muted">Rejecting a file keeps the case open — applicant re-uploads that document only.</p>
            </div>

            {buildDocGroups(selected).map((group) => (
              <div className="ops-doc-group" key={group.title}>
                <h4>{group.title}</h4>
                <div className="ops-table-wrap">
                  <table className="table ops-doc-table">
                    <thead>
                      <tr>
                        <th>Document</th>
                        <th>File</th>
                        <th>Status</th>
                        <th className="ops-col-actions">Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {group.docs.map((d) => (
                        <tr key={d.id} className={viewerDocId === d.id ? 'ops-row-active' : undefined}>
                          <td>
                            <div className="ops-doc-name-cell">
                              <DocMiniThumb
                                doc={d}
                                fetchBlob={fetchDocBlob}
                                onOpen={() => setViewerDocId(d.id)}
                              />
                              <div>
                                <strong>{humanizeDocKind(d.documentCode)}</strong>
                                {d.reviewNote && <div className="muted" style={{ fontSize: '0.78rem' }}>Note: {d.reviewNote}</div>}
                              </div>
                            </div>
                          </td>
                          <td className="muted">
                            <span className="ops-file-cell" title={d.originalName}>{d.originalName}</span>
                          </td>
                          <td>
                            <span className={`status status-${docStatusClass(d.status)}`}>{statusLabel(d.status)}</span>
                          </td>
                          <td className="ops-col-actions">
                            <div className="ops-doc-actions">
                              {isPreviewable(d) && (
                                <button
                                  type="button"
                                  className="ops-eye-btn ops-eye-btn--sm"
                                  aria-label={`Preview ${humanizeDocKind(d.documentCode)}`}
                                  title="Preview document"
                                  onClick={() => setViewerDocId(d.id)}
                                >
                                  <EyeIcon />
                                </button>
                              )}
                              {d.status === 'PENDING' && (
                                <>
                                  <button className="btn btn-primary btn-sm" type="button" onClick={() => void reviewDoc(d.id, true)}>
                                    Approve
                                  </button>
                                  <button className="btn btn-danger btn-sm" type="button" onClick={() => void reviewDoc(d.id, false)}>
                                    Reject
                                  </button>
                                </>
                              )}
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            ))}
            {(selected.documents || []).length === 0 && (
              <p className="muted">No documents uploaded yet.</p>
            )}
          </section>

          <section className="ops-drawer-section">
            <div className="ops-drawer-section-head">
              <h3>Discrepancy note</h3>
            </div>
            <textarea
              className="ops-drawer-textarea"
              rows={3}
              value={discrepancy}
              onChange={(e) => setDiscrepancy(e.target.value)}
              placeholder="Ask applicant for corrections…"
            />
            <button className="btn btn-ghost btn-sm" type="button" onClick={() => void sendDiscrepancy(selected.id)}>
              Send discrepancy
            </button>
          </section>

          {(selected.status === 'ACTIVE' || selected.status === 'PENDING_APPROVAL') && (
            <section className="ops-drawer-section">
              <div className="ops-drawer-section-head">
                <h3>DFS account provision</h3>
              </div>
              <p className="muted ops-provision-line">
                Status <strong>{selected.accountProvisionStatus || 'NOT_STARTED'}</strong>
                {selected.dfsAccountId ? <> · DFS ID <strong className="ops-mono">{selected.dfsAccountId}</strong></> : null}
                {selected.cmsRelationshipNum ? (
                  <> · CMS Rel <strong className="ops-mono">{selected.cmsRelationshipNum}</strong></>
                ) : null}
              </p>
              {selected.accountProvisionError && (
                <div className="alert alert-info">{selected.accountProvisionError}</div>
              )}
              <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
                {(selected.accountProvisionStatus === 'PENDING'
                  || selected.accountProvisionStatus === 'FAILED'
                  || selected.accountProvisionStatus === 'NOT_STARTED') && (
                  <button className="btn btn-primary btn-sm" type="button" onClick={() => void retryProvision(selected.id)}>
                    Retry account create
                  </button>
                )}
                {(selected.status === 'ACTIVE' || selected.status === 'PENDING_APPROVAL') && (
                  <button className="btn btn-ghost btn-sm" type="button" onClick={() => void linkCmsRelationship(selected.id)}>
                    Link CMS relationship
                  </button>
                )}
              </div>
            </section>
          )}

          {canDecide && (
            <footer className="ops-case-footer">
              <div className="ops-drawer-footer-copy">
                {selected.status === 'PENDING_APPROVAL' && (
                  <div className="ops-checklist">
                    <span className={(selected.partnerKycTotal || 0) === 0 || selected.partnerKycCompleted === selected.partnerKycTotal ? 'ok' : ''}>
                      KYC {selected.partnerKycCompleted ?? 0}/{selected.partnerKycTotal ?? 0}
                    </span>
                    <span className={(selected.docsPending ?? 0) === 0 ? 'ok' : ''}>
                      Docs pending {selected.docsPending ?? 0}
                    </span>
                    <span className={(selected.docsRejected ?? 0) === 0 ? 'ok' : ''}>
                      Rejected {selected.docsRejected ?? 0}
                    </span>
                    <span className={selected.sanctionsStatus === 'CLEAR' ? 'ok' : selected.sanctionsStatus === 'HIT' ? 'bad' : ''}>
                      Sanctions {selected.sanctionsStatus || '—'}
                    </span>
                  </div>
                )}
                {selected.status === 'SUBMITTED' && (
                  <p className="muted">Waiting on partner app KYC — you can still reject the full application.</p>
                )}
                {selected.status === 'INCOMPLETE' && (
                  <p className="muted">Incomplete: applicant must re-upload rejected documents.</p>
                )}
                <textarea
                  className="ops-drawer-textarea ops-drawer-textarea--compact"
                  rows={2}
                  value={rejectReason}
                  onChange={(e) => setRejectReason(e.target.value)}
                  placeholder="Full application reject reason (required to reject)"
                />
              </div>
              <div className="ops-drawer-footer-actions">
                <button className="btn btn-ghost" type="button" onClick={() => void rescreenSanctions(selected.id)}>
                  Re-screen AML
                </button>
                <button className="btn btn-ghost" type="button" onClick={() => void clearSanctions(selected.id)} disabled={selected.sanctionsStatus === 'CLEAR'}>
                  Clear sanctions
                </button>
                {selected.status === 'PENDING_APPROVAL' && (
                  <button
                    className="btn btn-primary"
                    type="button"
                    disabled={!selected.docsReadyForApprove}
                    onClick={() => void approve(selected.id)}
                  >
                    Approve entity
                  </button>
                )}
                <button className="btn btn-danger" type="button" onClick={() => void reject(selected.id)}>
                  Reject application
                </button>
              </div>
            </footer>
          )}
        </div>
      )}

      {viewerDocId != null && selected && (
        <DocOverlay
          docId={viewerDocId}
          doc={(selected.documents || []).find((d) => d.id === viewerDocId)}
          fetchBlob={fetchDocBlob}
          onClose={() => setViewerDocId(null)}
        />
      )}
    </div>
  );
}


function docStatusClass(status: string) {
  if (status === 'APPROVED') return 'ACTIVE';
  if (status === 'REJECTED') return 'REJECTED';
  return 'PENDING_APPROVAL';
}

function isImageDoc(doc?: Doc) {
  if (!doc) return false;
  const ct = (doc.contentType || '').toLowerCase();
  if (ct.startsWith('image/')) return true;
  return /\.(jpe?g|png|gif|webp)$/i.test(doc.originalName || '');
}

function isVideoDoc(doc?: Doc) {
  if (!doc) return false;
  const ct = (doc.contentType || '').toLowerCase();
  if (ct.startsWith('video/')) return true;
  return /\.(mp4|webm|mov|3gp)$/i.test(doc.originalName || '');
}

function DocMiniThumb({
  doc,
  fetchBlob,
  onOpen,
}: {
  doc: Doc;
  fetchBlob: (id: number) => Promise<string>;
  onOpen: () => void;
}) {
  const [src, setSrc] = useState<string | null>(null);
  const canPreview = isPreviewable(doc) && isImageDoc(doc);

  useEffect(() => {
    let url: string | null = null;
    if (!canPreview) return;
    fetchBlob(doc.id)
      .then((u) => { url = u; setSrc(u); })
      .catch(() => undefined);
    return () => { if (url) URL.revokeObjectURL(url); };
  }, [doc.id, canPreview, fetchBlob]);

  if (!isPreviewable(doc)) {
    return <span className="ops-doc-mini ops-doc-mini--empty">—</span>;
  }

  return (
    <button type="button" className="ops-doc-mini" onClick={onOpen} title="Preview" aria-label="Preview document">
      {src ? <img src={src} alt="" /> : <span>{isVideoDoc(doc) ? 'VID' : 'DOC'}</span>}
    </button>
  );
}

function DocOverlay({
  docId,
  doc,
  onClose,
  fetchBlob,
}: {
  docId: number;
  doc?: Doc;
  onClose: () => void;
  fetchBlob: (id: number) => Promise<string>;
}) {
  const [src, setSrc] = useState<string | null>(null);
  const [err, setErr] = useState('');
  const [loading, setLoading] = useState(true);
  const image = isImageDoc(doc);
  const video = isVideoDoc(doc);

  useEffect(() => {
    let url: string | null = null;
    setSrc(null);
    setErr('');
    setLoading(true);
    fetchBlob(docId)
      .then((u) => { url = u; setSrc(u); })
      .catch((e) => setErr(e instanceof Error ? e.message : 'Load failed'))
      .finally(() => setLoading(false));
    return () => { if (url) URL.revokeObjectURL(url); };
  }, [docId, fetchBlob]);

  useEffect(() => {
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => { document.body.style.overflow = prev; };
  }, []);

  return (
    <div className="ops-doc-overlay" role="dialog" aria-modal="true" aria-label="Document preview">
      <button type="button" className="ops-doc-overlay-backdrop" aria-label="Close preview" onClick={onClose} />
      <div className="ops-doc-overlay-panel">
        <header className="ops-doc-overlay-head">
          <div>
            <strong>{doc ? humanizeDocKind(doc.documentCode) : `Document #${docId}`}</strong>
            {doc?.originalName && <div className="muted" style={{ fontSize: '0.8rem' }}>{doc.originalName}</div>}
          </div>
          <button type="button" className="ops-doc-overlay-close" aria-label="Close" onClick={onClose}>
            <CloseIcon />
          </button>
        </header>
        <div className="ops-doc-overlay-body">
          {loading && <p className="muted">Loading document…</p>}
          {err && <div className="alert alert-error">{err}</div>}
          {!loading && src && image && <img className="ops-doc-overlay-media" src={src} alt={doc?.originalName || 'Document'} />}
          {!loading && src && video && <video className="ops-doc-overlay-media" src={src} controls autoPlay />}
          {!loading && src && !image && !video && <iframe title="Document" src={src} className="ops-doc-overlay-frame" />}
        </div>
      </div>
    </div>
  );
}

function DocThumb({
  doc,
  label,
  onOpen,
  fetchBlob,
}: {
  doc: Doc;
  label: string;
  token: string;
  onOpen: () => void;
  fetchBlob: (id: number) => Promise<string>;
}) {
  const [src, setSrc] = useState<string | null>(null);
  useEffect(() => {
    let url: string | null = null;
    if (!isPreviewable(doc) || !isImageDoc(doc)) return;
    fetchBlob(doc.id).then((u) => { url = u; setSrc(u); }).catch(() => undefined);
    return () => { if (url) URL.revokeObjectURL(url); };
  }, [doc.id, doc.contentType, doc.originalName, fetchBlob]);

  return (
    <button type="button" className="ops-thumb" onClick={onOpen} title={label}>
      {src ? <img src={src} alt={label} /> : <span>{label}</span>}
    </button>
  );
}

function SpecimenSignatureCard({
  partner,
  sheetDoc,
  sheetPdf,
  sheetPng,
  sheetJpeg,
  originalDoc,
  fetchBlob,
  onOpen,
}: {
  partner: AppUser;
  sheetDoc: Doc;
  sheetPdf?: Doc;
  sheetPng?: Doc;
  sheetJpeg?: Doc;
  originalDoc?: Doc;
  token: string;
  fetchBlob: (id: number) => Promise<string>;
  onOpen: (id: number) => void;
}) {
  const [src, setSrc] = useState<string | null>(null);
  const printRef = useRef<HTMLDivElement>(null);
  const displayName = partner.cnicFullName || partner.fullName;
  const captured = new Date().toLocaleDateString();

  useEffect(() => {
    let url: string | null = null;
    fetchBlob(sheetDoc.id)
      .then((u) => {
        url = u;
        setSrc(u);
      })
      .catch(() => undefined);
    return () => {
      if (url) URL.revokeObjectURL(url);
    };
  }, [sheetDoc.id, fetchBlob]);

  function printCard() {
    const node = printRef.current;
    if (!node) return;
    const win = window.open('', '_blank', 'noopener,noreferrer,width=900,height=700');
    if (!win) return;
    win.document.write(`<!DOCTYPE html><html><head><title>Specimen Signature — ${displayName}</title>
      <style>
        body { font-family: Georgia, "Times New Roman", serif; color: #111; margin: 24px; }
        .ss-card { border: 2px solid #222; padding: 16px 18px; max-width: 720px; }
        .ss-title { letter-spacing: 0.12em; font-size: 13px; font-weight: 700; text-transform: uppercase; margin: 0 0 12px; }
        .ss-meta { display: grid; grid-template-columns: 1fr 1fr; gap: 8px 20px; font-size: 13px; margin-bottom: 14px; }
        .ss-meta span { color: #555; display: block; font-size: 11px; text-transform: uppercase; letter-spacing: 0.06em; }
        .ss-sheet { width: 100%; border: 1px solid #ccc; background: #fff; }
        .ss-foot { margin-top: 10px; font-size: 11px; color: #666; }
      </style></head><body>${node.innerHTML}<script>window.onload=()=>{window.print();}</script></body></html>`);
    win.document.close();
  }

  return (
    <div className="ss-card-wrap">
      <div className="ss-card-toolbar">
        <strong>Specimen signature card</strong>
        <div className="actions" style={{ marginTop: 0 }}>
          <button className="btn btn-primary btn-sm" type="button" onClick={printCard}>
            Print SS card
          </button>
          {sheetPdf && (
            <button className="btn btn-ghost btn-sm" type="button" onClick={() => onOpen(sheetPdf.id)}>
              PDF
            </button>
          )}
          {sheetPng && (
            <button className="btn btn-ghost btn-sm" type="button" onClick={() => onOpen(sheetPng.id)}>
              PNG
            </button>
          )}
          {sheetJpeg && (
            <button className="btn btn-ghost btn-sm" type="button" onClick={() => onOpen(sheetJpeg.id)}>
              JPEG
            </button>
          )}
          {originalDoc && (
            <button className="btn btn-ghost btn-sm" type="button" onClick={() => onOpen(originalDoc.id)}>
              Original
            </button>
          )}
        </div>
      </div>
      <div className="ss-card" ref={printRef}>
        <p className="ss-title">Specimen Signature</p>
        <div className="ss-meta">
          <div>
            <span>Name</span>
            <strong>{displayName}</strong>
          </div>
          <div>
            <span>CNIC</span>
            <strong>{partner.cnicNumber || '—'}</strong>
          </div>
          <div>
            <span>Phone</span>
            <strong>{partner.phone || '—'}</strong>
          </div>
          <div>
            <span>Date</span>
            <strong>{captured}</strong>
          </div>
        </div>
        <div className="ss-sheet-frame">
          {src ? (
            <button type="button" className="ss-sheet-btn" onClick={() => onOpen(sheetDoc.id)}>
              <img className="ss-sheet" src={src} alt="Specimen signature 4-up" />
            </button>
          ) : (
            <div className="ops-signature-grid">
              {[0, 1, 2, 3].map((i) => (
                <DocThumb
                  key={`${sheetDoc.id}-${i}`}
                  doc={sheetDoc}
                  label={`Sig ${i + 1}`}
                  token=""
                  onOpen={() => onOpen(sheetDoc.id)}
                  fetchBlob={fetchBlob}
                />
              ))}
            </div>
          )}
        </div>
        <p className="ss-foot">Same signature image placed in four boxes for specimen record / print.</p>
      </div>
    </div>
  );
}

