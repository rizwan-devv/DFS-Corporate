import { useCallback, useEffect, useMemo, useState } from 'react';
import { Navigate } from 'react-router-dom';
import { api } from '../lib/api';
import { useAuth } from '../auth/AuthContext';

type Brand = { id: number; code: string; name: string; merchantCount: number };
type AppUser = {
  id: number;
  phone: string;
  email?: string;
  fullName: string;
  status: string;
  appInviteUrl?: string;
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
  riskRating?: string;
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
  accountProvisionError?: string;
};

const STATUS_FILTERS = [
  { value: 'PENDING_APPROVAL', label: 'Ready to approve' },
  { value: 'SUBMITTED', label: 'Awaiting app KYC' },
  { value: 'DRAFT', label: 'Draft' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: 'ALL', label: 'All' },
];

function isIdDoc(code: string) {
  const c = code.toUpperCase();
  return c.includes('ID_FRONT') || c.includes('ID_BACK') || c.includes('CNIC') || c.includes('PHOTO') || c.includes('_PHOTO');
}

function kycPct(p: Party) {
  const total = p.partnerKycTotal || 0;
  if (total === 0) return null;
  return Math.round(((p.partnerKycCompleted || 0) / total) * 100);
}

export function AdminPage() {
  const { session } = useAuth();
  const [brands, setBrands] = useState<Brand[]>([]);
  const [brandId, setBrandId] = useState<number | 'ALL'>('ALL');
  const [status, setStatus] = useState('PENDING_APPROVAL');
  const [tatOnly, setTatOnly] = useState(false);
  const [queue, setQueue] = useState<Party[]>([]);
  const [selected, setSelected] = useState<Party | null>(null);
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');
  const [rejectReason, setRejectReason] = useState('');
  const [discrepancy, setDiscrepancy] = useState('');
  const [viewerDocId, setViewerDocId] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);

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
    const overdue = queue.filter((p) => p.tatOverdue).length;
    const brandsActive = brands.length;
    return { pending, awaitingKyc, overdue, brandsActive, total: filtered.length };
  }, [queue, filtered, brands]);

  if (!session) return <Navigate to="/login" replace />;
  if (session.role !== 'PLATFORM_ADMIN') return <Navigate to="/" replace />;

  async function open(id: number) {
    setError(''); setOk('');
    const data = await api<Party>(`/api/admin/parties/${id}`, { token: session!.token });
    setSelected(data);
    setViewerDocId(null);
    setDiscrepancy(data.discrepancyNote || '');
  }

  async function approve(id: number) {
    setError(''); setOk('');
    try {
      const res = await api<{ temporaryPassword: string; message: string; accountProvisionStatus?: string }>(
        `/api/admin/parties/${id}/approve`,
        { method: 'POST', token: session!.token },
      );
      setOk(`${res.message} Temp password: ${res.temporaryPassword}`);
      setSelected(null);
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

  async function reject(id: number) {
    if (!rejectReason.trim()) { setError('Rejection reason required'); return; }
    setError(''); setOk('');
    try {
      await api(`/api/admin/parties/${id}/reject`, {
        method: 'POST', token: session!.token,
        body: JSON.stringify({ reason: rejectReason }),
      });
      setOk('Application rejected');
      setSelected(null); setRejectReason('');
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Reject failed');
    }
  }

  async function clearSanctions(id: number) {
    await api(`/api/admin/parties/${id}/sanctions`, {
      method: 'POST', token: session!.token,
      body: JSON.stringify({ status: 'CLEAR', notes: 'Admin cleared UNSC/ATA screening' }),
    });
    await open(id);
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
      setOk('Marked app KYC complete (stub until mobile app is live)');
      if (selected) await open(selected.id);
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed');
    }
  }

  async function reviewDoc(docId: number, approveDoc: boolean) {
    const path = approveDoc
      ? `/api/admin/documents/${docId}/approve`
      : `/api/admin/documents/${docId}/reject`;
    await api(path, { method: 'POST', token: session!.token, body: approveDoc ? undefined : JSON.stringify({ note: 'Rejected by backoffice' }) });
    if (selected) await open(selected.id);
  }

  function docUrl(docId: number) {
    return `/api/admin/documents/${docId}/file`;
  }

  async function fetchDocBlob(docId: number): Promise<string> {
    const res = await fetch(docUrl(docId), {
      headers: { Authorization: `Bearer ${session!.token}` },
    });
    if (!res.ok) throw new Error('Could not load document');
    const blob = await res.blob();
    return URL.createObjectURL(blob);
  }

  return (
    <div className="ops-shell">
      <div className="ops-topbar">
        <div>
          <div className="ops-eyebrow">BACKOFFICE · MULTI-BRAND</div>
          <h1 className="ops-title">Entity KYC Operations</h1>
          <p className="muted ops-sub">Wide ops console · filters · partner KYC progress · document review</p>
        </div>
        <div className="ops-stat-row">
          <div className="ops-stat"><strong>{stats.pending}</strong><span>Ready to approve</span></div>
          <div className="ops-stat"><strong>{stats.awaitingKyc}</strong><span>Awaiting app KYC</span></div>
          <div className="ops-stat"><strong>{stats.overdue}</strong><span>TAT overdue</span></div>
          <div className="ops-stat"><strong>{stats.brandsActive}</strong><span>Brands</span></div>
          <div className="ops-stat"><strong>{stats.total}</strong><span>In view</span></div>
        </div>
      </div>

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
            TAT overdue only
          </label>
          <button className="btn btn-ghost btn-sm" type="button" disabled={loading} onClick={() => void refresh()}>
            {loading ? 'Refreshing…' : 'Refresh'}
          </button>
        </div>
      </div>

      <div className={`ops-grid ${selected ? 'has-detail' : ''}`}>
        <section className="ops-queue panel panel--full">
          <h3 className="ops-section-title">Application queue</h3>
          <div className="ops-table-wrap">
            <table className="table ops-table">
              <thead>
                <tr>
                  <th>Tracking</th>
                  <th>Brand</th>
                  <th>Business</th>
                  <th>Entity</th>
                  <th>Status</th>
                  <th>Partner KYC</th>
                  <th>TAT due</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {filtered.length === 0 && (
                  <tr><td colSpan={8} className="muted">No applications for this filter</td></tr>
                )}
                {filtered.map((p) => {
                  const pct = kycPct(p);
                  return (
                    <tr
                      key={p.id}
                      className={selected?.id === p.id ? 'ops-row-active' : ''}
                      onClick={() => void open(p.id)}
                    >
                      <td>
                        <strong>{p.trackingId || p.publicId.slice(0, 8)}</strong>
                        {p.tatOverdue && <span className="ops-pill danger">OVERDUE</span>}
                      </td>
                      <td><span className="ops-pill muted-pill">{p.brandCode || '—'}</span></td>
                      <td>
                        <div>{p.businessName || p.fullName}</div>
                        <div className="muted" style={{ fontSize: '0.78rem' }}>{p.email}</div>
                      </td>
                      <td>{p.entityType || p.partyType}</td>
                      <td><span className={`status status-${p.status}`}>{p.status}</span></td>
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
                      <td>{p.decisionDueAt ? new Date(p.decisionDueAt).toLocaleDateString() : '—'}</td>
                      <td>
                        <button className="btn btn-ghost btn-sm" type="button" onClick={(e) => { e.stopPropagation(); void open(p.id); }}>
                          Open
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </section>

        {selected && (
          <aside className="ops-detail panel panel--full">
            <div className="ops-detail-head">
              <div>
                <div className="badge">{selected.brandName || selected.brandCode || 'Brand'}</div>
                <h3 style={{ margin: '0.35rem 0' }}>{selected.businessName || selected.fullName}</h3>
                <p className="muted" style={{ margin: 0 }}>
                  {selected.trackingId} · {selected.email} · {selected.phone || '—'}
                </p>
              </div>
              <button className="btn btn-ghost btn-sm" type="button" onClick={() => setSelected(null)}>Close</button>
            </div>

            <div className="ops-meta-grid">
              <div><span className="muted">Entity</span><strong>{selected.entityType || '—'}</strong></div>
              <div><span className="muted">Status</span><strong className={`status status-${selected.status}`}>{selected.status}</strong></div>
              <div><span className="muted">Sanctions</span><strong>{selected.sanctionsStatus || '—'}</strong></div>
              <div><span className="muted">Identity</span><strong>{selected.identityVerificationStatus || '—'}</strong></div>
              <div><span className="muted">Risk / EDD</span><strong>{selected.riskRating || '—'} / {selected.eddRequired ? 'Yes' : 'No'}</strong></div>
              <div><span className="muted">IP / Geo</span><strong>{selected.clientIp || '—'} / {selected.geoLocation || '—'}</strong></div>
            </div>

            {(selected.partnerKycTotal || 0) > 0 && (
              <div className="ops-block">
                <h4>Partner mobile app KYC</h4>
                <div className="ops-progress large">
                  <div className="ops-progress-bar">
                    <span style={{ width: `${kycPct(selected) || 0}%` }} />
                  </div>
                  <span className="ops-progress-label">
                    {selected.partnerKycCompleted}/{selected.partnerKycTotal} completed
                  </span>
                </div>
                {(selected.partnerAppUsers || []).map((u) => (
                  <div className="doc-row" key={u.id}>
                    <div>
                      <strong>{u.fullName}</strong>
                      <div className="muted">{u.phone} · {u.email || '—'} · {u.status}</div>
                      {u.appInviteUrl && <div className="invite-link">{u.appInviteUrl}</div>}
                    </div>
                    <div className="actions" style={{ marginTop: 0 }}>
                      {u.status !== 'KYC_COMPLETED' && (
                        <>
                          <button className="btn btn-ghost btn-sm" type="button" onClick={() => void resendAppInvite(u.id)}>
                            Re-send app invite
                          </button>
                          <button className="btn btn-ghost btn-sm" type="button" onClick={() => void markAppKycComplete(u.id)}>
                            Mark KYC done
                          </button>
                        </>
                      )}
                    </div>
                  </div>
                ))}
                {(selected.partnerInvites || []).length > 0 && (
                  <p className="muted" style={{ fontSize: '0.8rem' }}>Legacy portal invites (disabled) still listed for history.</p>
                )}
                {(selected.partnerInvites || []).map((inv) => (
                  <div className="doc-row" key={`leg-${inv.id}`}>
                    <div>
                      <strong>{inv.fullName}</strong>
                      <div className="muted">{inv.email} · {inv.status} (legacy)</div>
                    </div>
                    {inv.status !== 'COMPLETED' && inv.status !== 'CANCELLED' && (
                      <button className="btn btn-ghost btn-sm" type="button" onClick={() => void resendInvite(inv.id)}>
                        Re-send legacy
                      </button>
                    )}
                  </div>
                ))}
              </div>
            )}

            <div className="ops-block">
              <h4>Partners / owner roster</h4>
              {(selected.associatedPersons || []).length === 0 && (
                <p className="muted">None on roster (sole prop uses owner mobile KYC only)</p>
              )}
              {(selected.associatedPersons || []).map((p) => (
                <div className="doc-row" key={p.id}>
                  <div>
                    <strong>{p.fullName}</strong>
                    <div className="muted">{p.roleType}{p.phone ? ` · ${p.phone}` : ''}{p.email ? ` · ${p.email}` : ''}</div>
                  </div>
                </div>
              ))}
            </div>

            <div className="ops-block">
              <h4>Documents & ID thumbnails</h4>
              <div className="ops-thumb-grid">
                {(selected.documents || []).filter((d) => isIdDoc(d.documentCode)).map((d) => (
                  <DocThumb key={d.id} doc={d} token={session.token} onOpen={() => setViewerDocId(d.id)} fetchBlob={fetchDocBlob} />
                ))}
              </div>
              {(selected.documents || []).map((d) => (
                <div className="doc-row" key={d.id}>
                  <div>
                    <strong>{d.documentCode}</strong>
                    <div className="muted">{d.originalName}</div>
                  </div>
                  <div className="actions" style={{ marginTop: 0 }}>
                    <span className={`status status-${d.status === 'APPROVED' ? 'ACTIVE' : d.status === 'REJECTED' ? 'REJECTED' : 'PENDING_APPROVAL'}`}>{d.status}</span>
                    <button className="btn btn-ghost btn-sm" type="button" onClick={() => setViewerDocId(d.id)}>View</button>
                    {d.status === 'PENDING' && (
                      <>
                        <button className="btn btn-ghost btn-sm" type="button" onClick={() => void reviewDoc(d.id, true)}>Approve</button>
                        <button className="btn btn-danger btn-sm" type="button" onClick={() => void reviewDoc(d.id, false)}>Reject</button>
                      </>
                    )}
                  </div>
                </div>
              ))}
              {viewerDocId != null && (
                <DocViewer docId={viewerDocId} token={session.token} onClose={() => setViewerDocId(null)} fetchBlob={fetchDocBlob} />
              )}
            </div>

            <div className="ops-block">
              <h4>Discrepancy note</h4>
              <div className="form-row">
                <textarea rows={3} value={discrepancy} onChange={(e) => setDiscrepancy(e.target.value)} placeholder="Ask applicant for corrections…" />
              </div>
              <button className="btn btn-ghost btn-sm" type="button" onClick={() => void sendDiscrepancy(selected.id)}>
                Send discrepancy
              </button>
            </div>

            {(selected.status === 'PENDING_APPROVAL' || selected.status === 'SUBMITTED') && (
              <div className="ops-block">
                <h4>Decision</h4>
                {selected.status === 'SUBMITTED' && (
                  <p className="muted">Waiting for all partners to complete mobile app KYC before final approve. You can reject now, or mark KYC done (stub) above.</p>
                )}
                {selected.status === 'PENDING_APPROVAL' && (
                  <div className="alert alert-info" style={{ marginBottom: '0.75rem' }}>
                    <strong>Pre-approve checklist</strong>
                    <ul style={{ margin: '0.4rem 0 0', paddingLeft: '1.2rem' }}>
                      <li>Partner app KYC: {(selected.partnerKycCompleted ?? 0)}/{(selected.partnerKycTotal ?? 0)}
                        {(selected.partnerKycTotal || 0) > 0 && selected.partnerKycCompleted === selected.partnerKycTotal ? ' ✓' : ' — incomplete'}
                      </li>
                      <li>Documents pending review: {selected.docsPending ?? 0}
                        {(selected.docsPending ?? 0) === 0 ? ' ✓' : ' — approve/reject each'}
                      </li>
                      <li>Documents rejected: {selected.docsRejected ?? 0}
                        {(selected.docsRejected ?? 0) === 0 ? ' ✓' : ' — must be re-uploaded'}
                      </li>
                      <li>Sanctions: {selected.sanctionsStatus || '—'}
                        {selected.sanctionsStatus === 'HIT' ? ' — block' : selected.sanctionsStatus === 'CLEAR' ? ' ✓' : ' (will clear on approve)'}
                      </li>
                    </ul>
                    {!selected.docsReadyForApprove && (
                      <p className="muted" style={{ margin: '0.5rem 0 0' }}>
                        Approve stays disabled until every uploaded document is reviewed (no PENDING / REJECTED).
                      </p>
                    )}
                  </div>
                )}
                <div className="form-row">
                  <label>Rejection reason</label>
                  <textarea rows={2} value={rejectReason} onChange={(e) => setRejectReason(e.target.value)} />
                </div>
                <div className="actions">
                  <button className="btn btn-ghost" type="button" onClick={() => void clearSanctions(selected.id)}>Mark sanctions CLEAR</button>
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
                  <button className="btn btn-danger" type="button" onClick={() => void reject(selected.id)}>Reject</button>
                </div>
              </div>
            )}

            {selected.status === 'ACTIVE' || selected.status === 'PENDING_APPROVAL' ? (
              <div className="ops-block">
                <h4>DFS backend account provision</h4>
                <p className="muted" style={{ marginTop: 0 }}>
                  Status: <strong>{selected.accountProvisionStatus || 'NOT_STARTED'}</strong>
                  {selected.dfsAccountId ? <> · ID <strong>{selected.dfsAccountId}</strong></> : null}
                </p>
                {selected.accountProvisionError && (
                  <div className="alert alert-info">{selected.accountProvisionError}</div>
                )}
                {(selected.accountProvisionStatus === 'PENDING'
                  || selected.accountProvisionStatus === 'FAILED'
                  || selected.accountProvisionStatus === 'NOT_STARTED') && (
                  <button className="btn btn-primary btn-sm" type="button" onClick={() => void retryProvision(selected.id)}>
                    Retry DFS backend account create
                  </button>
                )}
              </div>
            ) : null}
          </aside>
        )}
      </div>
    </div>
  );
}

function DocThumb({
  doc,
  onOpen,
  fetchBlob,
}: {
  doc: Doc;
  token: string;
  onOpen: () => void;
  fetchBlob: (id: number) => Promise<string>;
}) {
  const [src, setSrc] = useState<string | null>(null);
  useEffect(() => {
    let url: string | null = null;
    const isImg = (doc.contentType || '').startsWith('image/') || /\.(jpe?g|png|gif|webp)$/i.test(doc.originalName);
    if (!isImg) return;
    fetchBlob(doc.id).then((u) => { url = u; setSrc(u); }).catch(() => undefined);
    return () => { if (url) URL.revokeObjectURL(url); };
  }, [doc.id, doc.contentType, doc.originalName, fetchBlob]);

  return (
    <button type="button" className="ops-thumb" onClick={onOpen} title={doc.documentCode}>
      {src ? <img src={src} alt={doc.documentCode} /> : <span>{doc.documentCode}</span>}
    </button>
  );
}

function DocViewer({
  docId,
  onClose,
  fetchBlob,
}: {
  docId: number;
  token: string;
  onClose: () => void;
  fetchBlob: (id: number) => Promise<string>;
}) {
  const [src, setSrc] = useState<string | null>(null);
  const [err, setErr] = useState('');
  useEffect(() => {
    let url: string | null = null;
    fetchBlob(docId)
      .then((u) => { url = u; setSrc(u); })
      .catch((e) => setErr(e instanceof Error ? e.message : 'Load failed'));
    return () => { if (url) URL.revokeObjectURL(url); };
  }, [docId, fetchBlob]);

  return (
    <div className="ops-viewer">
      <div className="ops-viewer-bar">
        <strong>Document #{docId}</strong>
        <button className="btn btn-ghost btn-sm" type="button" onClick={onClose}>Close viewer</button>
      </div>
      {err && <div className="alert alert-error">{err}</div>}
      {src && (
        <iframe title="Document" src={src} className="ops-viewer-frame" />
      )}
    </div>
  );
}
