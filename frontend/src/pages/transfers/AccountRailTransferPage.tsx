import { type FormEvent, useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { PageHeader } from '../../components/PageHeader';
import { BeneficiaryPicker } from '../../components/BeneficiaryPicker';
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

export function AccountRailTransferPage({ product }: Props) {
  const { session } = useAuth();
  const meta = TRANSFER_PRODUCTS.find((p) => p.id === product)!;
  const [live, setLive] = useState<LiveStatus | null>(null);
  const [tab, setTab] = useState<'single' | 'bulk'>('single');
  const [form, setForm] = useState(emptyForm);
  const [selectedBenId, setSelectedBenId] = useState<string | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [history, setHistory] = useState<MockTransfer[]>([]);
  const [banks, setBanks] = useState<IbftBank[]>([]);
  const [titleResult, setTitleResult] = useState<DfsTxnResponse | null>(null);
  const [ftInitResult, setFtInitResult] = useState<DfsTxnResponse | null>(null);
  const [lastLive, setLastLive] = useState<DfsTxnResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');

  const liveOn = !!live?.liveEnabled;

  const loadStatus = useCallback(async () => {
    if (!session?.token) return;
    try {
      const s = await fetchLiveStatus(session.token);
      setLive(s);
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
      const list = await api<MockTransfer[]>(`/api/transfers/mock?productType=${product}`, {
        token: session.token,
      });
      setHistory(list);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load history');
    }
  }, [session?.token, product]);

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

  useEffect(() => {
    void loadStatus();
  }, [loadStatus]);

  useEffect(() => {
    void loadHistory();
  }, [loadHistory]);

  useEffect(() => {
    void loadBanks();
  }, [loadBanks]);

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
        setOk(r.messages || 'Title fetched — confirm beneficiary then submit advice');
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
        setOk(r.messages || 'IBFT advice success — money moved');
        setForm(emptyForm);
        setTitleResult(null);
        setSelectedBenId(null);
        await loadHistory();
      } else {
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
        body: JSON.stringify({
          accountNo: form.accountNumber,
          amount: form.amount,
          accountType: 'W',
        }),
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
        setOk(r.messages || 'Local FT success — money moved');
        setForm(emptyForm);
        setFtInitResult(null);
        setSelectedBenId(null);
        await loadHistory();
      } else {
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
          ipin: form.ipin || undefined,
          bankName: form.bankName || undefined,
          amount,
          cnic: form.cnic || undefined,
          mobile: form.mobile || undefined,
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
    setError('');
    setOk('');
    setLoading(true);
    try {
      const fd = new FormData();
      fd.append('productType', product);
      fd.append('file', file);
      const res = await api<MockTransfer>('/api/transfers/mock/bulk', {
        method: 'POST',
        token: session.token,
        body: fd,
      });
      setOk(`Mock bulk ${product}: ${res.bulkRowCount} row(s) — ${res.mockTxnRef}`);
      setFile(null);
      await loadHistory();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Bulk upload failed');
    } finally {
      setLoading(false);
    }
  }

  function downloadTemplate() {
    if (!session?.token) return;
    void (async () => {
      try {
        const res = await fetch(apiUrl('/api/transfers/mock/template.csv'), {
          headers: { Authorization: `Bearer ${session.token}` },
        });
        if (!res.ok) throw new Error('Template download failed');
        const blob = await res.blob();
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'dfs-bulk-transfer-template.csv';
        a.click();
        URL.revokeObjectURL(url);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Template download failed');
      }
    })();
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
    <div className="portal-page">
      <PageHeader
        eyebrow="Transfers"
        title={meta.title}
        subtitle={liveOn ? `${meta.blurb} · Live DFS rails` : `${meta.blurb} · Mock mode`}
        actions={
          <div className="actions" style={{ marginTop: 0 }}>
            <Link className="btn btn-ghost btn-sm" to="/beneficiaries">Beneficiaries</Link>
            <button type="button" className="btn btn-ghost btn-sm" onClick={() => { void loadStatus(); void loadHistory(); void loadBanks(); }}>
              Refresh
            </button>
          </div>
        }
      />

      <div className={`alert ${liveOn ? 'alert-ok' : 'alert-info'}`} style={{ marginBottom: '1rem' }}>
        {liveOn ? (
          <>
            <strong>Live mode</strong> — payer {live?.fromAccountNo || '—'}
            {!live?.hasNid && ' · CNIC missing on party'}
            {product === 'FT' && !live?.hasDfsAppUserId && ' · set dfs_app_user_id for FT confirm (or enter APP_USER_ID below)'}
          </>
        ) : (
          <>
            <strong>Mock mode</strong> — set <code>DFS_PORTAL_API_ENABLED=true</code> and{' '}
            <code>CORPORATE_PORTAL_API_KEY</code> on the backend to enable live FT/IBFT.
          </>
        )}
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {ok && <div className="alert alert-ok">{ok}</div>}

      {!liveOn && (
        <div className="transfer-mode-tabs" style={{ marginTop: '0.25rem' }}>
          <button type="button" className={`btn btn-sm ${tab === 'single' ? 'btn-primary' : 'btn-ghost'}`} onClick={() => setTab('single')}>
            Single
          </button>
          <button type="button" className={`btn btn-sm ${tab === 'bulk' ? 'btn-primary' : 'btn-ghost'}`} onClick={() => setTab('bulk')} style={{ marginLeft: '0.5rem' }}>
            Bulk (CSV mock)
          </button>
        </div>
      )}

      <div className="panel panel--wide" style={{ marginTop: '1.25rem' }}>
        {liveOn && product === 'IBFT' ? (
          <form className="form-grid" onSubmit={titleResult && isLiveOk(titleResult) ? ibftAdvice : ibftTitleFetch}>
            <h3 className="form-section-title">IBFT — live (bank list → title → advice)</h3>
            <BeneficiaryPicker product="IBFT" selectedPublicId={selectedBenId} onSelect={applyBeneficiary} />
            <div className="form-row">
              <label>Bank</label>
              <select
                required
                value={form.bankImd}
                onChange={(e) => {
                  const imd = e.target.value;
                  const b = banks.find((x) => String(x.bankImd) === imd);
                  setTitleResult(null);
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
            </div>
            <div className="form-row">
              <label>Beneficiary account / IBAN</label>
              <input required value={form.accountNumber} onChange={(e) => { setTitleResult(null); setForm({ ...form, accountNumber: e.target.value }); }} />
            </div>
            <div className="form-row">
              <label>Amount (PKR)</label>
              <input required value={form.amount} onChange={(e) => { setTitleResult(null); setForm({ ...form, amount: e.target.value }); }} />
            </div>
            {titleResult && isLiveOk(titleResult) && (
              <>
                <div className="alert alert-info">
                  Title OK — {form.beneficiaryName || 'see response'} · confirm then advice moves money
                </div>
                <div className="form-row">
                  <label>Beneficiary name</label>
                  <input value={form.beneficiaryName} onChange={(e) => setForm({ ...form, beneficiaryName: e.target.value })} />
                </div>
                <div className="form-row">
                  <label>Purpose (optional)</label>
                  <input value={form.purposeOfPayment} onChange={(e) => setForm({ ...form, purposeOfPayment: e.target.value })} />
                </div>
              </>
            )}
            <div className="actions">
              <button className="btn btn-primary" type="submit" disabled={loading}>
                {loading ? 'Working…' : titleResult && isLiveOk(titleResult) ? 'Submit advice (move money)' : 'Fetch title'}
              </button>
              {titleResult && (
                <button type="button" className="btn btn-ghost" onClick={() => setTitleResult(null)}>
                  Reset title step
                </button>
              )}
            </div>
          </form>
        ) : liveOn && product === 'FT' ? (
          <form className="form-grid" onSubmit={ftInitResult && isLiveOk(ftInitResult) ? ftConfirm : ftInitiate}>
            <h3 className="form-section-title">Fund Transfer — live (initiate → MPIN confirm)</h3>
            <BeneficiaryPicker product="FT" selectedPublicId={selectedBenId} onSelect={applyBeneficiary} />
            <div className="form-row">
              <label>Beneficiary wallet / mobile</label>
              <input required value={form.accountNumber} onChange={(e) => { setFtInitResult(null); setForm({ ...form, accountNumber: e.target.value }); }} />
            </div>
            <div className="form-row">
              <label>Amount (PKR)</label>
              <input required value={form.amount} onChange={(e) => { setFtInitResult(null); setForm({ ...form, amount: e.target.value }); }} />
            </div>
            {ftInitResult && isLiveOk(ftInitResult) && (
              <>
                <div className="alert alert-info">
                  Initiate OK — beneficiary {form.beneficiaryName || 'resolved'}. Enter customer MPIN to move money.
                </div>
                <div className="form-row">
                  <label>Customer MPIN</label>
                  <input required type="password" autoComplete="one-time-code" value={form.mpin} onChange={(e) => setForm({ ...form, mpin: e.target.value })} />
                </div>
                {!live?.hasDfsAppUserId && (
                  <div className="form-row">
                    <label>Payer APP_USER_ID (DFS)</label>
                    <input required value={form.appUserId} onChange={(e) => setForm({ ...form, appUserId: e.target.value })} placeholder="From DFS app — Postman customerAppUserId" />
                  </div>
                )}
                <div className="form-row">
                  <label>Narration</label>
                  <input value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} />
                </div>
              </>
            )}
            <div className="actions">
              <button className="btn btn-primary" type="submit" disabled={loading}>
                {loading ? 'Working…' : ftInitResult && isLiveOk(ftInitResult) ? 'Confirm FT (move money)' : 'Initiate FT'}
              </button>
              {ftInitResult && (
                <button type="button" className="btn btn-ghost" onClick={() => setFtInitResult(null)}>
                  Reset initiate
                </button>
              )}
            </div>
          </form>
        ) : tab === 'single' ? (
          <form className="form-grid" onSubmit={submitMockSingle}>
            <h3 className="form-section-title">{product} — single (mock)</h3>
            <BeneficiaryPicker product={product} selectedPublicId={selectedBenId} onSelect={applyBeneficiary} />
            <div className="form-row">
              <label>Account number</label>
              <input required value={form.accountNumber} onChange={(e) => setForm({ ...form, accountNumber: e.target.value })} />
            </div>
            <div className="form-row">
              <label>Bank name{product === 'IBFT' ? '' : ' (optional)'}</label>
              <input required={product === 'IBFT'} value={form.bankName} onChange={(e) => setForm({ ...form, bankName: e.target.value })} />
            </div>
            <div className="form-row">
              <label>Amount (PKR)</label>
              <input required type="number" min="0.01" step="0.01" value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} />
            </div>
            <div className="form-row">
              <label>Beneficiary name</label>
              <input value={form.beneficiaryName} onChange={(e) => setForm({ ...form, beneficiaryName: e.target.value })} />
            </div>
            <div className="actions">
              <button className="btn btn-primary" type="submit" disabled={loading}>
                {loading ? 'Submitting…' : `Submit mock ${product}`}
              </button>
            </div>
          </form>
        ) : (
          <form className="form-grid" onSubmit={submitBulk}>
            <h3 className="form-section-title">{product} — bulk CSV (mock)</h3>
            <div className="actions" style={{ marginTop: 0 }}>
              <button type="button" className="btn btn-ghost btn-sm" onClick={downloadTemplate}>Download CSV template</button>
            </div>
            <div className="form-row">
              <label>CSV file</label>
              <input type="file" accept=".csv,.txt" onChange={(e) => setFile(e.target.files?.[0] || null)} />
            </div>
            <div className="actions">
              <button className="btn btn-primary" type="submit" disabled={loading || !file}>
                {loading ? 'Uploading…' : `Upload mock bulk ${product}`}
              </button>
            </div>
          </form>
        )}
      </div>

      {lastLive && (
        <div className="panel panel--wide" style={{ marginTop: '1.25rem' }}>
          <h3>Last live response</h3>
          <p>
            <span className={`status ${isLiveOk(lastLive) ? 'status-ACTIVE' : 'status-REJECTED'}`}>
              {lastLive.responsecode}
            </span>{' '}
            {lastLive.messages}
          </p>
          <pre className="transfer-pre">{JSON.stringify(lastLive.data ?? lastLive.raw, null, 2)}</pre>
        </div>
      )}

      <div className="panel panel--wide" style={{ marginTop: '1.25rem' }}>
        <h3>{product} history</h3>
        {history.length === 0 && <p className="muted">No transfers yet.</p>}
        {history.map((t) => (
          <div className="doc-row" key={t.id}>
            <div>
              <strong>{t.mode}</strong> · {t.mockTxnRef}
              <div className="muted">
                {t.amount != null ? `PKR ${t.amount}` : '—'}
                {t.accountNumber ? ` · ${t.accountNumber}` : ''}
                {t.createdAt ? ` · ${new Date(t.createdAt).toLocaleString()}` : ''}
              </div>
            </div>
            <span className="status status-ACTIVE">{t.status}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

export function FtTransferPage() {
  return <AccountRailTransferPage product="FT" />;
}

export function IbftTransferPage() {
  return <AccountRailTransferPage product="IBFT" />;
}
