import { type FormEvent, useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { PageHeader } from '../../components/PageHeader';
import { BeneficiaryPicker } from '../../components/BeneficiaryPicker';
import { LiveBulkPanel } from '../../components/LiveBulkPanel';
import { PaymentReceipt } from '../../components/PaymentReceipt';
import { api, apiUrl } from '../../lib/api';
import { useAuth } from '../../auth/AuthContext';
import type { Beneficiary, MockTransfer } from '../../lib/transferTypes';
import { TRANSFER_PRODUCTS } from '../../lib/transferTypes';
import {
  banksFromResponse,
  fetchLiveStatus,
  isLiveOk,
  type DfsTxnResponse,
  type IbftBank,
  type LiveStatus,
} from '../../lib/liveTransfers';
import {
  canActOn,
  displayStatus,
  formatActionDecision,
  formatWhen,
  isPendingDisplayStatus,
  parsePayload,
  progressLabel,
  roleFlags,
  shortRequestId,
  type ApprovalRow,
} from '../../lib/transferWorkflow';
import {
  buildReceiptFromHistory,
  buildReceiptFromLive,
  type PaymentReceiptModel,
} from '../../lib/paymentReceipt';

const emptyForm = {
  accountNumber: '',
  ipin: '',
  bankName: '',
  bankImd: '',
  amount: '',
  cnic: '',
  mobile: '',
  beneficiaryName: '',
  notes: '',
  mpin: '',
  appUserId: '',
  purposeOfPayment: '',
};

type Props = { product: 'FT' | 'IBFT' };
type PageTab = 'all' | 'new' | 'bulk';

export function AccountRailTransferPage({ product }: Props) {
  const { session } = useAuth();
  const meta = TRANSFER_PRODUCTS.find((p) => p.id === product)!;
  const roles = session?.portalRoles || [];
  const flags = roleFlags(roles);

  const [live, setLive] = useState<LiveStatus | null>(null);
  const [pageTab, setPageTab] = useState<PageTab>('all');
  const [form, setForm] = useState(emptyForm);
  const [selectedBenId, setSelectedBenId] = useState<string | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [history, setHistory] = useState<MockTransfer[]>([]);
  const [approvals, setApprovals] = useState<ApprovalRow[]>([]);
  const [banks, setBanks] = useState<IbftBank[]>([]);
  const [titleResult, setTitleResult] = useState<DfsTxnResponse | null>(null);
  const [ftInitResult, setFtInitResult] = useState<DfsTxnResponse | null>(null);
  const [lastLive, setLastLive] = useState<DfsTxnResponse | null>(null);
  const [receipt, setReceipt] = useState<PaymentReceiptModel | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');
  const [statusFilter, setStatusFilter] = useState('PENDING');
  const [detailsId, setDetailsId] = useState<string | null>(null);
  const [directLive, setDirectLive] = useState(false);
  const [releaseMpin, setReleaseMpin] = useState('');
  const [showTechJson, setShowTechJson] = useState(false);

  const liveOn = !!live?.liveEnabled;

  const productApprovals = useMemo(() => {
    return approvals.filter((a) => {
      if (a.requestType !== 'PAYMENT' && a.requestType !== 'GENERIC') return false;
      const p = parsePayload(a.payloadJson);
      if (p.product) return p.product === product;
      // GENERIC without product: show on both only if title mentions product
      return (a.title || '').toUpperCase().includes(product);
    });
  }, [approvals, product]);

  const filteredApprovals = useMemo(() => {
    if (statusFilter === 'ALL') return productApprovals;
    if (statusFilter === 'PENDING') {
      return productApprovals.filter((a) => isPendingDisplayStatus(displayStatus(a)));
    }
    return productApprovals.filter((a) => displayStatus(a) === statusFilter);
  }, [productApprovals, statusFilter]);

  const detailsRow = useMemo(
    () => productApprovals.find((a) => a.publicId === detailsId) ?? null,
    [productApprovals, detailsId],
  );

  const loadStatus = useCallback(async () => {
    if (!session?.token) return;
    try {
      setLive(await fetchLiveStatus(session.token));
    } catch {
      setLive({
        liveEnabled: false,
        txnConfigured: false,
        appConfigured: false,
        hasNid: false,
        hasDfsAppUserId: false,
        products: [],
        raastLive: false,
      });
    }
  }, [session?.token]);

  const loadHistory = useCallback(async () => {
    if (!session?.token) return;
    try {
      setHistory(await api<MockTransfer[]>(`/api/transfers/mock?productType=${product}`, { token: session.token }));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load history');
    }
  }, [session?.token, product]);

  const loadApprovals = useCallback(async () => {
    if (!session?.token) return;
    try {
      const list = await api<ApprovalRow[]>('/api/approvals', { token: session.token });
      setApprovals(list);
    } catch {
      setApprovals([]);
    }
  }, [session?.token]);

  const loadBanks = useCallback(async () => {
    if (!session?.token || !liveOn || product !== 'IBFT') return;
    try {
      const r = await api<DfsTxnResponse>('/api/transfers/live/ibft/banks', { token: session.token });
      setBanks(banksFromResponse(r));
      if (!isLiveOk(r) && r.messages) setError(r.messages);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load bank list');
    }
  }, [session?.token, liveOn, product]);

  useEffect(() => { void loadStatus(); }, [loadStatus]);
  useEffect(() => { void loadHistory(); }, [loadHistory]);
  useEffect(() => { void loadApprovals(); }, [loadApprovals]);
  useEffect(() => { void loadBanks(); }, [loadBanks]);

  function applyBeneficiary(b: Beneficiary | null) {
    setSelectedBenId(b?.publicId ?? null);
    setTitleResult(null);
    setFtInitResult(null);
    if (!b) return;
    setForm((f) => ({
      ...f,
      accountNumber: b.accountNumber || '',
      bankName: b.bankName || '',
      mobile: b.mobile || '',
      cnic: b.cnic || '',
      beneficiaryName: b.fullName || '',
    }));
  }

  async function refreshAll() {
    setError('');
    await Promise.all([loadStatus(), loadHistory(), loadApprovals(), loadBanks()]);
  }

  async function submitForApproval(e: FormEvent) {
    e.preventDefault();
    if (!session?.token) return;
    if (!flags.canMake) {
      setError('MAKER or PARTY_ADMIN role required to submit requests');
      return;
    }
    setError('');
    setOk('');
    setLoading(true);
    try {
      const amount = form.amount;
      if (!amount || Number(amount) <= 0) throw new Error('Enter a valid amount');
      if (!form.accountNumber.trim()) throw new Error(product === 'FT' ? 'Beneficiary mobile is required' : 'IBAN / account is required');
      if (product === 'IBFT' && !form.bankImd && !form.bankName) throw new Error('Select beneficiary bank');

      const payload = {
        product,
        amount,
        accountNumber: form.accountNumber,
        bankName: form.bankName,
        bankImd: form.bankImd,
        beneficiaryName: form.beneficiaryName,
        notes: form.notes,
        purposeOfPayment: form.purposeOfPayment,
        mobile: form.mobile,
      };
      await api('/api/approvals', {
        method: 'POST',
        token: session.token,
        body: JSON.stringify({
          requestType: 'PAYMENT',
          title: `${product} ${form.beneficiaryName || form.accountNumber} · PKR ${amount}`,
          referenceKey: form.notes || form.accountNumber,
          payloadJson: JSON.stringify(payload),
          comment: 'Submitted from transfer New Request',
        }),
      });
      if (Number(amount) <= 5000) {
        setOk(`${product} PKR ${amount} ≤ 5,000 — sent straight to Releaser (Checker/Approver skipped)`);
      } else {
        setOk(`${product} request submitted — awaiting Checker → Approver → Releaser`);
      }
      setForm(emptyForm);
      setTitleResult(null);
      setFtInitResult(null);
      setSelectedBenId(null);
      setPageTab('all');
      setStatusFilter('PENDING');
      await loadApprovals();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Submit failed');
    } finally {
      setLoading(false);
    }
  }

  async function decide(publicId: string, decision: 'APPROVE' | 'REJECT', comment?: string, mpin?: string) {
    if (!session?.token) return;
    setLoading(true);
    setError('');
    try {
      await api(`/api/approvals/${publicId}/decide`, {
        method: 'POST',
        token: session.token,
        body: JSON.stringify({ decision, comment, mpin: mpin || undefined }),
      });
      setOk(decision === 'APPROVE'
        ? (comment === 'RELEASE' ? 'Released — live DFS payout sent' : 'Decision recorded')
        : 'Request stopped / rejected');
      if (comment === 'RELEASE') setReleaseMpin('');
      await loadApprovals();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Action failed');
    } finally {
      setLoading(false);
    }
  }

  async function ibftTitleFetch(e: FormEvent) {
    e.preventDefault();
    if (!session?.token) return;
    setError('');
    setOk('');
    setLoading(true);
    setTitleResult(null);
    try {
      const r = await api<DfsTxnResponse>('/api/transfers/live/ibft/title-fetch', {
        method: 'POST',
        token: session.token,
        body: JSON.stringify({
          beneficiaryAccountNo: form.accountNumber,
          beneficiaryBankImd: form.bankImd,
          amount: form.amount,
        }),
      });
      setTitleResult(r);
      if (isLiveOk(r)) {
        setOk(r.messages || 'Title fetched');
        const data = r.data as { beneficiaryTitle?: string; title?: string; accountTitle?: string } | undefined;
        const title = data?.beneficiaryTitle || data?.title || data?.accountTitle;
        if (title) setForm((f) => ({ ...f, beneficiaryName: title }));
      } else {
        setError(r.messages || `Title fetch failed (${r.responsecode})`);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Title fetch failed');
    } finally {
      setLoading(false);
    }
  }

  async function ibftAdvice(e: FormEvent) {
    e.preventDefault();
    if (!session?.token) return;
    setError('');
    setOk('');
    setLoading(true);
    try {
      const r = await api<DfsTxnResponse>('/api/transfers/live/ibft/advice', {
        method: 'POST',
        token: session.token,
        body: JSON.stringify({
          beneficiaryAccountNo: form.accountNumber,
          beneficiaryBankImd: form.bankImd,
          amount: form.amount,
          purposeOfPayment: form.purposeOfPayment || '',
          beneficiaryName: form.beneficiaryName || undefined,
          notes: form.notes || undefined,
        }),
      });
      setLastLive(r);
      if (isLiveOk(r)) {
        setReceipt(buildReceiptFromLive(r, {
          product: 'IBFT',
          amount: form.amount,
          accountNumber: form.accountNumber,
          beneficiaryName: form.beneficiaryName || undefined,
          bankName: form.bankName || undefined,
          bankImd: form.bankImd || undefined,
          portalTxnRef: r.portalTxnRef,
        }));
        setOk(r.messages || 'IBFT success — receipt ready');
        setForm(emptyForm);
        setTitleResult(null);
        setSelectedBenId(null);
        await loadHistory();
      } else {
        setReceipt(buildReceiptFromLive(r, {
          product: 'IBFT',
          amount: form.amount,
          accountNumber: form.accountNumber,
          beneficiaryName: form.beneficiaryName || undefined,
          bankName: form.bankName || undefined,
          bankImd: form.bankImd || undefined,
          portalTxnRef: r.portalTxnRef,
        }));
        setError(r.messages || `IBFT failed (${r.responsecode})`);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'IBFT advice failed');
    } finally {
      setLoading(false);
    }
  }

  async function ftInitiate(e: FormEvent) {
    e.preventDefault();
    if (!session?.token) return;
    setError('');
    setOk('');
    setLoading(true);
    setFtInitResult(null);
    try {
      const r = await api<DfsTxnResponse>('/api/transfers/live/ft/initiate', {
        method: 'POST',
        token: session.token,
        body: JSON.stringify({ accountNo: form.accountNumber, amount: form.amount, accountType: 'W' }),
      });
      setFtInitResult(r);
      if (isLiveOk(r)) {
        setOk(r.messages || 'FT initiated — enter MPIN to confirm');
        const data = r.data as { beneficiaryTitle?: string; title?: string } | undefined;
        const title = data?.beneficiaryTitle || data?.title;
        if (title) setForm((f) => ({ ...f, beneficiaryName: title }));
      } else {
        setError(r.messages || `Initiate failed (${r.responsecode})`);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'FT initiate failed');
    } finally {
      setLoading(false);
    }
  }

  async function ftConfirm(e: FormEvent) {
    e.preventDefault();
    if (!session?.token) return;
    setError('');
    setOk('');
    setLoading(true);
    try {
      const r = await api<DfsTxnResponse>('/api/transfers/live/ft/confirm', {
        method: 'POST',
        token: session.token,
        body: JSON.stringify({
          accountNo: form.accountNumber,
          amount: form.amount,
          accountType: 'W',
          mpin: form.mpin,
          appUserId: form.appUserId || undefined,
          narration: form.notes || undefined,
          beneficiaryName: form.beneficiaryName || undefined,
        }),
      });
      setLastLive(r);
      if (isLiveOk(r)) {
        setReceipt(buildReceiptFromLive(r, {
          product: 'FT',
          amount: form.amount,
          accountNumber: form.accountNumber,
          beneficiaryName: form.beneficiaryName || undefined,
          portalTxnRef: r.portalTxnRef,
        }));
        setOk(r.messages || 'Local FT success — receipt ready');
        setForm(emptyForm);
        setFtInitResult(null);
        setSelectedBenId(null);
        await loadHistory();
      } else {
        setReceipt(buildReceiptFromLive(r, {
          product: 'FT',
          amount: form.amount,
          accountNumber: form.accountNumber,
          beneficiaryName: form.beneficiaryName || undefined,
          portalTxnRef: r.portalTxnRef,
        }));
        setError(r.messages || `FT confirm failed (${r.responsecode})`);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'FT confirm failed');
    } finally {
      setLoading(false);
    }
  }

  async function submitMockSingle(e: FormEvent) {
    e.preventDefault();
    if (!session?.token) return;
    setError('');
    setOk('');
    setLoading(true);
    try {
      const amount = Number(form.amount);
      if (!Number.isFinite(amount) || amount <= 0) throw new Error('Enter a valid amount (PKR)');
      const res = await api<MockTransfer>('/api/transfers/mock/single', {
        method: 'POST',
        token: session.token,
        body: JSON.stringify({
          productType: product,
          accountNumber: form.accountNumber || undefined,
          bankName: form.bankName || undefined,
          amount,
          beneficiaryName: form.beneficiaryName || undefined,
          notes: form.notes || undefined,
        }),
      });
      setOk(`Mock ${product} success — ${res.mockTxnRef}`);
      setForm(emptyForm);
      await loadHistory();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Transfer failed');
    } finally {
      setLoading(false);
    }
  }

  async function submitBulk(e: FormEvent) {
    e.preventDefault();
    if (!session?.token || !file) {
      setError('Choose a CSV file first');
      return;
    }
    setLoading(true);
    setError('');
    try {
      const fd = new FormData();
      fd.append('productType', product);
      fd.append('file', file);
      const res = await api<MockTransfer>('/api/transfers/mock/bulk', {
        method: 'POST',
        token: session.token,
        body: fd,
      });
      setOk(`Mock bulk ${product}: ${res.bulkRowCount} row(s)`);
      setFile(null);
      await loadHistory();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Bulk upload failed');
    } finally {
      setLoading(false);
    }
  }

  if (!session) {
    return (
      <div className="portal-page">
        <PageHeader eyebrow={meta.short} title="Login required" subtitle="Sign in to continue." />
        <Link className="btn btn-primary" to="/login">Login</Link>
      </div>
    );
  }

  return (
    <div className="portal-page txn-page">
      <PageHeader
        eyebrow="Transfers"
        title={`${meta.short} — Single Transfer`}
        subtitle={liveOn ? `${meta.blurb} · Live DFS · maker → checker → releaser` : `${meta.blurb} · Mock / workflow`}
        actions={
          <div className="actions" style={{ marginTop: 0 }}>
            <Link className="btn btn-ghost btn-sm" to="/beneficiaries">Beneficiaries</Link>
            <button type="button" className="btn btn-ghost btn-sm" onClick={() => void refreshAll()}>Refresh</button>
          </div>
        }
      />

      <div className="txn-role-bar">
        <span className="muted">Your roles</span>
        <div className="txn-role-chips">
          {(roles.length ? roles : ['(none — assign MAKER/CHECKER/APPROVER/RELEASER)']).map((r) => (
            <span key={r} className="txn-role-chip">{r}</span>
          ))}
        </div>
      </div>

      <div className={`alert ${liveOn ? 'alert-ok' : 'alert-info'}`}>
        {liveOn ? (
          <><strong>Live mode</strong> — payer {live?.fromAccountNo || '—'}. New Request submits for approval; use Direct live pay to move money immediately (testing).</>
        ) : (
          <><strong>Mock / offline DFS</strong> — enable portal key for live rails. Workflow still works for PAYMENT requests.</>
        )}
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {ok && <div className="alert alert-ok">{ok}</div>}

      <div className="txn-tabs">
        <button type="button" className={pageTab === 'all' ? 'is-active' : ''} onClick={() => setPageTab('all')}>All Requests</button>
        <button type="button" className={pageTab === 'new' ? 'is-active' : ''} onClick={() => setPageTab('new')}>New Request</button>
        <button type="button" className={pageTab === 'bulk' ? 'is-active' : ''} onClick={() => setPageTab('bulk')}>Bulk CSV</button>
      </div>

      {pageTab === 'all' && (
        <div className="panel panel--wide txn-panel">
          <div className="txn-panel-head">
            <div>
              <h3>{product} — Approval queue</h3>
              <p className="muted">
                Maker → Checker → Approver → Releaser. <strong>Release</strong> on FT sends live DFS
                initiate + confirm (<code>COP</code>). Enter customer MPIN if it is not stored on the party.
              </p>
            </div>
            <div className="txn-panel-tools">
              <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} aria-label="Status filter">
                <option value="PENDING">Pending only</option>
                <option value="ALL">All statuses</option>
                <option value="PENDING_CHECK">PENDING_CHECK</option>
                <option value="CHECKED">CHECKED</option>
                <option value="PENDING_RELEASE">PENDING_RELEASE</option>
                <option value="RELEASED">RELEASED</option>
                <option value="REJECTED">REJECTED</option>
                <option value="STOPPED">STOPPED</option>
              </select>
              <button type="button" className="btn btn-ghost btn-sm" onClick={() => void loadApprovals()}>Refresh</button>
            </div>
          </div>
          <div className="form-row" style={{ maxWidth: 280, margin: '0.75rem 0 0' }}>
            <label>Customer MPIN (for Release → DFS)</label>
            <input
              type="password"
              inputMode="numeric"
              autoComplete="off"
              value={releaseMpin}
              onChange={(e) => setReleaseMpin(e.target.value)}
              placeholder="If not stored on party"
            />
          </div>

          <div className="alert alert-info txn-info">
            Actions appear for your role at the current step. Use <strong>All statuses</strong> to see completed or stopped requests.
          </div>

          <div className="txn-table-wrap">
            <table className="txn-table">
              <thead>
                <tr>
                  <th>Id</th>
                  <th>Amount</th>
                  <th>{product === 'IBFT' ? 'Beneficiary / IBAN' : 'Beneficiary'}</th>
                  <th>Status</th>
                  <th>Steps</th>
                  <th>Created</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredApprovals.length === 0 && (
                  <tr>
                    <td colSpan={7} className="txn-empty muted">
                      {statusFilter === 'PENDING'
                        ? `No pending ${product} requests. Switch filter to All statuses or create one under New Request.`
                        : `No ${product} approval requests yet. Create one under New Request.`}
                    </td>
                  </tr>
                )}
                {filteredApprovals.map((row) => {
                  const p = parsePayload(row.payloadJson);
                  const acts = canActOn(row, roles);
                  const st = displayStatus(row);
                  const beneficiary = p.beneficiaryName?.trim()
                    || p.accountNumber
                    || row.referenceKey
                    || '—';
                  return (
                    <tr key={row.publicId}>
                      <td className="txn-mono" title={row.publicId}>{shortRequestId(row.publicId)}</td>
                      <td>{p.amount != null && p.amount !== '' ? `PKR ${p.amount}` : '—'}</td>
                      <td>
                        <div>{beneficiary}</div>
                        {p.beneficiaryName?.trim() && p.accountNumber && (
                          <div className="muted txn-mono" style={{ fontSize: '0.78rem' }}>{p.accountNumber}</div>
                        )}
                      </td>
                      <td><span className={`txn-status txn-status--${st.toLowerCase()}`}>{st}</span></td>
                      <td className="muted" style={{ fontSize: '0.85rem' }}>{progressLabel(row)}</td>
                      <td className="muted">{formatWhen(row.createdAt)}</td>
                      <td>
                        <div className="txn-actions">
                          <button
                            type="button"
                            className="btn btn-ghost btn-sm"
                            onClick={() => { setDetailsId(row.publicId); setShowTechJson(false); }}
                          >
                            Details
                          </button>
                          {acts.approve && (
                            <button type="button" className="btn btn-ghost btn-sm" disabled={loading} onClick={() => void decide(row.publicId, 'APPROVE')}>
                              Approve
                            </button>
                          )}
                          {acts.release && (
                            <button type="button" className="btn btn-primary btn-sm" disabled={loading} onClick={() => void decide(row.publicId, 'APPROVE', 'RELEASE', releaseMpin.trim())}>
                              Release
                            </button>
                          )}
                          {acts.stop && (
                            <button type="button" className="btn btn-ghost btn-sm" disabled={loading} onClick={() => void decide(row.publicId, 'REJECT', 'STOPPED by user')}>
                              Stop
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          {detailsRow && (() => {
            const p = parsePayload(detailsRow.payloadJson);
            const st = displayStatus(detailsRow);
            const actions = [...(detailsRow.actions || [])].sort(
              (a, b) => new Date(a.createdAt || 0).getTime() - new Date(b.createdAt || 0).getTime(),
            );
            return (
              <div className="txn-details">
                <div className="txn-details-head">
                  <h4>Request {shortRequestId(detailsRow.publicId)}</h4>
                  <button type="button" className="btn btn-ghost btn-sm" onClick={() => setDetailsId(null)}>Close</button>
                </div>

                <div className="txn-details-summary">
                  <div><span className="muted">Status</span><strong className={`txn-status txn-status--${st.toLowerCase()}`}>{st}</strong></div>
                  <div><span className="muted">Amount</span><strong>{p.amount != null && p.amount !== '' ? `PKR ${p.amount}` : '—'}</strong></div>
                  <div><span className="muted">To</span><strong className="txn-mono">{p.accountNumber || '—'}</strong></div>
                  <div><span className="muted">Beneficiary</span><strong>{p.beneficiaryName || '—'}</strong></div>
                  <div><span className="muted">Steps</span><strong>{progressLabel(detailsRow)}</strong></div>
                  <div>
                    <span className="muted">Payout</span>
                    <strong className={p.payoutStatus === 'SENT' ? undefined : 'muted'}>
                      {p.payoutStatus === 'SENT'
                        ? `Sent ${p.portalTxnRef || p.dfsAuthId || ''}`.trim()
                        : st === 'RELEASED'
                          ? 'Not sent (IBFT/UBP Release has no DFS hook, or older row)'
                          : 'Awaiting Release'}
                    </strong>
                  </div>
                </div>

                <h5 className="txn-timeline-title">Lifecycle</h5>
                {actions.length === 0 ? (
                  <p className="muted">No actions recorded yet.</p>
                ) : (
                  <ol className="txn-timeline">
                    {actions.map((a, i) => (
                      <li key={`${a.step}-${a.createdAt}-${i}`}>
                        <div className="txn-timeline-main">
                          <strong>{formatActionDecision(a.step, a.decision, a.comment)}</strong>
                          <span className="muted"> · {a.step}</span>
                        </div>
                        <div className="muted txn-timeline-meta">
                          {a.actorEmail || '—'} · {formatWhen(a.createdAt)}
                          {a.comment ? ` · ${a.comment}` : ''}
                        </div>
                      </li>
                    ))}
                  </ol>
                )}

                {st === 'RELEASED' && p.payoutStatus === 'SENT' && (
                  <p className="alert alert-ok" style={{ marginTop: '0.85rem' }}>
                    Live DFS payout sent{p.portalTxnRef ? ` · ${p.portalTxnRef}` : ''}. Same row also appears under Direct live pays.
                  </p>
                )}
                {st === 'RELEASED' && p.payoutStatus !== 'SENT' && (
                  <p className="alert alert-info" style={{ marginTop: '0.85rem' }}>
                    Workflow complete. This row was released before live FT payout, or it is not an FT payment.
                  </p>
                )}

                <button
                  type="button"
                  className="btn btn-ghost btn-sm"
                  style={{ marginTop: '0.5rem' }}
                  onClick={() => setShowTechJson((v) => !v)}
                >
                  {showTechJson ? 'Hide technical JSON' : 'Show technical JSON'}
                </button>
                {showTechJson && (
                  <pre className="transfer-pre">{JSON.stringify({
                    ...detailsRow,
                    payload: p,
                  }, null, 2)}</pre>
                )}
              </div>
            );
          })()}

          {history.length > 0 && (
            <div className="txn-legacy">
              <h4>Direct live / mock pays (bypass workflow)</h4>
              <p className="muted" style={{ marginTop: 0 }}>
                Tap a row for a payment receipt. These are Direct live / mock pays — not linked to the approval queue above.
              </p>
              {history.slice(0, 8).map((t) => (
                <div className="doc-row" key={t.id}>
                  <button
                    type="button"
                    className="history-receipt-btn"
                    onClick={() => setReceipt(buildReceiptFromHistory(t))}
                  >
                    <strong>{t.mockTxnRef}</strong>
                    <div className="muted">{t.amount != null ? `PKR ${t.amount}` : '—'}{t.accountNumber ? ` · ${t.accountNumber}` : ''}</div>
                  </button>
                  <span className={`status ${t.status === 'LIVE_SUCCESS' ? 'status-ACTIVE' : 'status-REJECTED'}`}>{t.status}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {pageTab === 'new' && (
        <div className="panel panel--wide txn-panel">
          <div className="txn-panel-head">
            <div>
              <h3>{product} — New Request</h3>
              <p className="muted">
                {flags.canMake
                  ? `Fill the form and Submit for approval. Amount ≤ 5,000 PKR skips Checker/Approver → Releaser; above that requires Checker → Approver → Releaser. Payment completes only when Releaser releases.`
                  : 'You need MAKER (or PARTY_ADMIN) to submit for approval. Direct live pay may still be available below.'}
              </p>
            </div>
            {liveOn && (
              <label className="txn-direct-toggle">
                <input type="checkbox" checked={directLive} onChange={(e) => setDirectLive(e.target.checked)} />
                Direct live pay (skip workflow)
              </label>
            )}
          </div>

          {!directLive && (
            <form className="txn-form-grid" onSubmit={submitForApproval}>
              <div className="txn-span-3">
                <BeneficiaryPicker product={product} selectedPublicId={selectedBenId} onSelect={applyBeneficiary} />
              </div>

              {product === 'IBFT' ? (
                <div className="form-row">
                  <label>Beneficiary bank <span className="txn-req">*</span></label>
                  {liveOn ? (
                    <select
                      required
                      value={form.bankImd}
                      onChange={(e) => {
                        const imd = e.target.value;
                        const b = banks.find((x) => String(x.bankImd) === imd);
                        setForm({ ...form, bankImd: imd, bankName: b?.bankName ? String(b.bankName) : '' });
                      }}
                    >
                      <option value="">Select bank</option>
                      {banks.map((b) => (
                        <option key={String(b.bankImd)} value={String(b.bankImd)}>
                          {String(b.bankName || b.bankImd)} ({String(b.bankImd)})
                        </option>
                      ))}
                    </select>
                  ) : (
                    <input required value={form.bankName} onChange={(e) => setForm({ ...form, bankName: e.target.value })} placeholder="Bank name" />
                  )}
                </div>
              ) : (
                <div className="form-row">
                  <label>Transfer type</label>
                  <input value="Fund Transfer (same network)" disabled readOnly />
                </div>
              )}

              <div className="form-row">
                <label>{product === 'FT' ? 'Beneficiary wallet / mobile' : 'IBAN'} <span className="txn-req">*</span></label>
                <input
                  required
                  value={form.accountNumber}
                  onChange={(e) => setForm({ ...form, accountNumber: e.target.value })}
                  placeholder={product === 'IBFT' ? 'PKxxAAAA…' : '03XXXXXXXXX'}
                />
              </div>

              <div className="form-row">
                <label>Amount (PKR) <span className="txn-req">*</span></label>
                <input required value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} />
              </div>

              <div className="form-row">
                <label>Account title {product === 'IBFT' && liveOn ? <span className="txn-req">*</span> : null}</label>
                <input
                  value={form.beneficiaryName}
                  onChange={(e) => setForm({ ...form, beneficiaryName: e.target.value })}
                  required={product === 'IBFT' && liveOn}
                />
                {product === 'IBFT' && liveOn && (
                  <button
                    type="button"
                    className="btn btn-primary btn-sm txn-fetch-btn"
                    disabled={loading}
                    onClick={(e) => void ibftTitleFetch(e as unknown as FormEvent)}
                  >
                    Fetch title
                  </button>
                )}
              </div>

              <div className="form-row">
                <label>Phone number</label>
                <input
                  value={form.mobile}
                  onChange={(e) => setForm({ ...form, mobile: e.target.value })}
                  placeholder="03XXXXXXXXX"
                />
              </div>

              <div className="form-row">
                <label>Customer reference</label>
                <input value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} />
              </div>

              {product === 'IBFT' && (
                <div className="form-row">
                  <label>Purpose</label>
                  <input value={form.purposeOfPayment} onChange={(e) => setForm({ ...form, purposeOfPayment: e.target.value })} />
                </div>
              )}

              <div className="txn-span-3 txn-form-actions">
                <button className="btn btn-primary" type="submit" disabled={loading || !flags.canMake}>
                  {loading ? 'Submitting…' : `Submit ${product}`}
                </button>
              </div>
            </form>
          )}

          {directLive && liveOn && product === 'IBFT' && (
            <form className="txn-form-grid" onSubmit={titleResult && isLiveOk(titleResult) ? ibftAdvice : ibftTitleFetch}>
              <h4 className="form-section-title txn-span-3">Direct live IBFT</h4>
              <div className="txn-span-3">
                <BeneficiaryPicker product="IBFT" selectedPublicId={selectedBenId} onSelect={applyBeneficiary} />
              </div>
              <div className="form-row">
                <label>Beneficiary bank <span className="txn-req">*</span></label>
                <select required value={form.bankImd} onChange={(e) => {
                  const imd = e.target.value;
                  const b = banks.find((x) => String(x.bankImd) === imd);
                  setTitleResult(null);
                  setForm({ ...form, bankImd: imd, bankName: b?.bankName ? String(b.bankName) : '' });
                }}>
                  <option value="">Select bank</option>
                  {banks.map((b) => (
                    <option key={String(b.bankImd)} value={String(b.bankImd)}>{String(b.bankName || b.bankImd)}</option>
                  ))}
                </select>
              </div>
              <div className="form-row">
                <label>IBAN <span className="txn-req">*</span></label>
                <input required value={form.accountNumber} placeholder="PKxxAAAA…" onChange={(e) => { setTitleResult(null); setForm({ ...form, accountNumber: e.target.value }); }} />
              </div>
              <div className="form-row">
                <label>Amount (PKR) <span className="txn-req">*</span></label>
                <input required value={form.amount} onChange={(e) => { setTitleResult(null); setForm({ ...form, amount: e.target.value }); }} />
              </div>
              <div className="form-row">
                <label>Account title</label>
                <input value={form.beneficiaryName} onChange={(e) => setForm({ ...form, beneficiaryName: e.target.value })} />
                {!titleResult || !isLiveOk(titleResult) ? (
                  <button type="submit" className="btn btn-primary btn-sm txn-fetch-btn" disabled={loading}>
                    {loading ? 'Working…' : 'Fetch title'}
                  </button>
                ) : null}
              </div>
              <div className="form-row">
                <label>Phone number</label>
                <input value={form.mobile} placeholder="03XXXXXXXXX" onChange={(e) => setForm({ ...form, mobile: e.target.value })} />
              </div>
              <div className="form-row">
                <label>Customer reference</label>
                <input value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} />
              </div>
              {titleResult && isLiveOk(titleResult) && (
                <div className="txn-span-3 txn-form-actions">
                  <button className="btn btn-primary" type="submit" disabled={loading}>
                    {loading ? 'Working…' : 'Submit advice (move money)'}
                  </button>
                </div>
              )}
            </form>
          )}

          {directLive && liveOn && product === 'FT' && (
            <form className="txn-form-grid" onSubmit={ftInitResult && isLiveOk(ftInitResult) ? ftConfirm : ftInitiate}>
              <h4 className="form-section-title txn-span-3">Direct live FT</h4>
              <div className="txn-span-3">
                <BeneficiaryPicker product="FT" selectedPublicId={selectedBenId} onSelect={applyBeneficiary} />
              </div>
              <div className="form-row">
                <label>Beneficiary wallet / mobile <span className="txn-req">*</span></label>
                <input required value={form.accountNumber} placeholder="03XXXXXXXXX" onChange={(e) => { setFtInitResult(null); setForm({ ...form, accountNumber: e.target.value }); }} />
              </div>
              <div className="form-row">
                <label>Amount (PKR) <span className="txn-req">*</span></label>
                <input required value={form.amount} onChange={(e) => { setFtInitResult(null); setForm({ ...form, amount: e.target.value }); }} />
              </div>
              <div className="form-row">
                <label>Account title</label>
                <input value={form.beneficiaryName} onChange={(e) => setForm({ ...form, beneficiaryName: e.target.value })} />
              </div>
              {ftInitResult && isLiveOk(ftInitResult) ? (
                <>
                  <div className="form-row">
                    <label>Customer MPIN <span className="txn-req">*</span></label>
                    <input required type="password" value={form.mpin} onChange={(e) => setForm({ ...form, mpin: e.target.value })} />
                  </div>
                  <div className="form-row">
                    <label>Customer reference</label>
                    <input value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} />
                  </div>
                  {!live?.hasDfsAppUserId ? (
                    <div className="form-row">
                      <label>APP_USER_ID <span className="txn-req">*</span></label>
                      <input required value={form.appUserId} onChange={(e) => setForm({ ...form, appUserId: e.target.value })} />
                    </div>
                  ) : (
                    <div className="form-row" />
                  )}
                  <div className="txn-span-3 txn-form-actions">
                    <button className="btn btn-primary" type="submit" disabled={loading}>
                      {loading ? 'Working…' : 'Confirm FT'}
                    </button>
                  </div>
                </>
              ) : (
                <div className="txn-span-3 txn-form-actions">
                  <button className="btn btn-primary" type="submit" disabled={loading}>
                    {loading ? 'Working…' : 'Initiate FT'}
                  </button>
                </div>
              )}
            </form>
          )}

          {!liveOn && !directLive && !flags.canMake && (
            <form className="txn-form-grid" onSubmit={submitMockSingle}>
              <h4 className="form-section-title txn-span-3">Mock single (no maker role)</h4>
              <div className="form-row">
                <label>Account <span className="txn-req">*</span></label>
                <input required value={form.accountNumber} onChange={(e) => setForm({ ...form, accountNumber: e.target.value })} />
              </div>
              <div className="form-row">
                <label>Amount (PKR) <span className="txn-req">*</span></label>
                <input required value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} />
              </div>
              <div className="form-row">
                <label>Title</label>
                <input value={form.beneficiaryName} onChange={(e) => setForm({ ...form, beneficiaryName: e.target.value })} />
              </div>
              <div className="txn-span-3 txn-form-actions">
                <button className="btn btn-primary" type="submit" disabled={loading}>Submit mock</button>
              </div>
            </form>
          )}

          {lastLive && (
            <div style={{ marginTop: '1.25rem' }}>
              <h4>Last live payment</h4>
              <p>
                <span className={`status ${isLiveOk(lastLive) ? 'status-ACTIVE' : 'status-REJECTED'}`}>{lastLive.responsecode}</span>{' '}
                {lastLive.messages}
                {lastLive.portalTxnRef ? ` · ${lastLive.portalTxnRef}` : ''}
              </p>
              <button
                type="button"
                className="btn btn-primary btn-sm"
                onClick={() => setReceipt(buildReceiptFromLive(lastLive, { product, portalTxnRef: lastLive.portalTxnRef }))}
              >
                View receipt
              </button>
            </div>
          )}
        </div>
      )}

      {receipt && (
        <PaymentReceipt receipt={receipt} onClose={() => setReceipt(null)} />
      )}

      {pageTab === 'bulk' && (
        <div className="panel panel--wide txn-panel">
          {liveOn ? (
            <LiveBulkPanel product={product} token={session.token} />
          ) : (
            <form className="form-grid" onSubmit={submitBulk}>
              <h3 className="form-section-title">{product} — bulk CSV (mock)</h3>
              <div className="form-row">
                <label>CSV file</label>
                <input type="file" accept=".csv,.txt" onChange={(e) => setFile(e.target.files?.[0] || null)} />
              </div>
              <button className="btn btn-primary" type="submit" disabled={loading || !file}>Upload</button>
              <button
                type="button"
                className="btn btn-ghost btn-sm"
                onClick={() => {
                  void fetch(apiUrl('/api/transfers/mock/template.csv'), {
                    headers: { Authorization: `Bearer ${session.token}` },
                  }).then(async (res) => {
                    const blob = await res.blob();
                    const url = URL.createObjectURL(blob);
                    const a = document.createElement('a');
                    a.href = url;
                    a.download = 'dfs-bulk-transfer-template.csv';
                    a.click();
                    URL.revokeObjectURL(url);
                  });
                }}
              >
                Download template
              </button>
            </form>
          )}
        </div>
      )}
    </div>
  );
}

export function FtTransferPage() {
  return <AccountRailTransferPage product="FT" />;
}

export function IbftTransferPage() {
  return <AccountRailTransferPage product="IBFT" />;
}
