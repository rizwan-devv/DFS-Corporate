import { type FormEvent, useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { PageHeader } from '../../components/PageHeader';
import { LiveBulkPanel } from '../../components/LiveBulkPanel';
import { api, apiUrl } from '../../lib/api';
import { useAuth } from '../../auth/AuthContext';
import type { MockTransfer, UbpBill, UbpCategory } from '../../lib/transferTypes';
import {
  billersFromResponse,
  fetchLiveStatus,
  isLiveOk,
  type DfsTxnResponse,
  type LiveStatus,
  type UbpBillerLive,
} from '../../lib/liveTransfers';

const emptyForm = {
  amount: '',
  mobile: '',
  beneficiaryName: '',
  notes: '',
  ubpCategory: '',
  ubpCompany: '',
  utilityCompanyCode: '',
  consumerNumber: '',
  billingMonth: '',
  billDueDate: '',
};

export function UbpTransferPage() {
  const { session } = useAuth();
  const [live, setLive] = useState<LiveStatus | null>(null);
  const [tab, setTab] = useState<'single' | 'bulk'>('single');
  const [form, setForm] = useState(emptyForm);
  const [file, setFile] = useState<File | null>(null);
  const [history, setHistory] = useState<MockTransfer[]>([]);
  const [last, setLast] = useState<MockTransfer | null>(null);
  const [lastLive, setLastLive] = useState<DfsTxnResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [fetchingBill, setFetchingBill] = useState(false);
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');
  const [ubpCategories, setUbpCategories] = useState<UbpCategory[]>([]);
  const [liveBillers, setLiveBillers] = useState<UbpBillerLive[]>([]);
  const [fetchedBill, setFetchedBill] = useState<UbpBill | null>(null);
  const [inquiry, setInquiry] = useState<DfsTxnResponse | null>(null);

  const liveOn = !!live?.liveEnabled;

  const selectedCategory = useMemo(
    () => ubpCategories.find((c) => c.code === form.ubpCategory) ?? null,
    [ubpCategories, form.ubpCategory],
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

  const load = useCallback(async () => {
    if (!session?.token) return;
    try {
      setHistory(await api<MockTransfer[]>('/api/transfers/mock?productType=UBP', { token: session.token }));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load history');
    }
  }, [session?.token]);

  const loadUbpCatalog = useCallback(async () => {
    if (!session?.token || liveOn) return;
    try {
      const data = await api<{ categories: UbpCategory[] }>('/api/transfers/mock/ubp/catalog', {
        token: session.token,
      });
      setUbpCategories(data.categories || []);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load UBP catalog');
    }
  }, [session?.token, liveOn]);

  const loadLiveBillers = useCallback(async () => {
    if (!session?.token || !liveOn) return;
    try {
      const r = await api<DfsTxnResponse>('/api/transfers/live/ubp/billers', { token: session.token });
      setLiveBillers(billersFromResponse(r));
      if (!isLiveOk(r) && r.messages) setError(r.messages);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load billers');
    }
  }, [session?.token, liveOn]);

  useEffect(() => {
    void loadStatus();
  }, [loadStatus]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    void loadUbpCatalog();
  }, [loadUbpCatalog]);

  useEffect(() => {
    void loadLiveBillers();
  }, [loadLiveBillers]);

  async function fetchBillMock() {
    if (!session?.token) return;
    setError('');
    setOk('');
    setFetchingBill(true);
    try {
      const bill = await api<UbpBill>('/api/transfers/mock/ubp/fetch', {
        method: 'POST',
        token: session.token,
        body: JSON.stringify({
          ubpCategory: form.ubpCategory,
          ubpCompany: form.ubpCompany,
          consumerNumber: form.consumerNumber,
        }),
      });
      setFetchedBill(bill);
      setForm((f) => ({
        ...f,
        amount: bill.dueAmount != null ? String(bill.dueAmount) : f.amount,
        billingMonth: bill.billingMonth || '',
        billDueDate: bill.dueDate || '',
        beneficiaryName: bill.customerName || '',
      }));
      setOk(`Mock bill fetched — ${bill.customerName || 'customer'}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Fetch bill failed');
    } finally {
      setFetchingBill(false);
    }
  }

  async function liveInquiry(e?: FormEvent) {
    e?.preventDefault();
    if (!session?.token) return;
    setError('');
    setOk('');
    setFetchingBill(true);
    setInquiry(null);
    try {
      const r = await api<DfsTxnResponse>('/api/transfers/live/ubp/inquiry', {
        method: 'POST',
        token: session.token,
        body: JSON.stringify({
          utilityCompanyCode: form.utilityCompanyCode,
          consumerNo: form.consumerNumber,
        }),
      });
      setInquiry(r);
      if (isLiveOk(r)) {
        setOk(r.messages || 'Bill inquiry OK');
        const data = r.data as { dueAmount?: number | string; amount?: number | string; customerName?: string } | undefined;
        const amt = data?.dueAmount ?? data?.amount;
        if (amt != null) setForm((f) => ({ ...f, amount: String(amt) }));
        if (data?.customerName) setForm((f) => ({ ...f, beneficiaryName: data.customerName! }));
      } else {
        setError(r.messages || `Inquiry failed (${r.responsecode})`);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Bill inquiry failed');
    } finally {
      setFetchingBill(false);
    }
  }

  async function livePay(e: FormEvent) {
    e.preventDefault();
    if (!session?.token) return;
    setError('');
    setOk('');
    setLoading(true);
    try {
      const r = await api<DfsTxnResponse>('/api/transfers/live/ubp/pay', {
        method: 'POST',
        token: session.token,
        body: JSON.stringify({
          utilityCompanyCode: form.utilityCompanyCode,
          consumerNo: form.consumerNumber,
          amount: form.amount,
          beneficiaryName: form.beneficiaryName || undefined,
          notes: form.notes || undefined,
        }),
      });
      setLastLive(r);
      if (isLiveOk(r)) {
        setOk(r.messages || 'Bill payment success');
        setForm(emptyForm);
        setInquiry(null);
        await load();
      } else {
        setError(r.messages || `Payment failed (${r.responsecode})`);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Bill payment failed');
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
          productType: 'UBP',
          amount,
          ubpCategory: form.ubpCategory || undefined,
          ubpCompany: form.ubpCompany || undefined,
          consumerNumber: form.consumerNumber || undefined,
          billingMonth: form.billingMonth || undefined,
          billDueDate: form.billDueDate || undefined,
          mobile: form.mobile || undefined,
          beneficiaryName: form.beneficiaryName || undefined,
          notes: form.notes || undefined,
        }),
      });
      setLast(res);
      setOk(`Mock UBP success — ${res.mockTxnRef}`);
      setForm(emptyForm);
      setFetchedBill(null);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'UBP payment failed');
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
      fd.append('productType', 'UBP');
      fd.append('file', file);
      const res = await api<MockTransfer>('/api/transfers/mock/bulk', {
        method: 'POST',
        token: session.token,
        body: fd,
      });
      setLast(res);
      setOk(`Mock bulk UBP: ${res.bulkRowCount} row(s)`);
      setFile(null);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Bulk failed');
    } finally {
      setLoading(false);
    }
  }

  if (!session) {
    return (
      <div className="portal-page">
        <PageHeader eyebrow="UBP" title="Login required" subtitle="Sign in to continue." />
        <Link className="btn btn-primary" to="/login">Login</Link>
      </div>
    );
  }

  return (
    <div className="portal-page">
      <PageHeader
        eyebrow="Transfers"
        title="Utility Bill Payment"
        subtitle={liveOn ? 'Live DFS billers · inquiry · payment' : 'Mock Pakistan billers'}
        actions={
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => { void loadStatus(); void load(); void loadLiveBillers(); }}>
            Refresh
          </button>
        }
      />

      <div className={`alert ${liveOn ? 'alert-ok' : 'alert-info'}`} style={{ marginBottom: '1rem' }}>
        {liveOn ? (
          <><strong>Live mode</strong> — payer {live?.fromAccountNo || '—'}</>
        ) : (
          <><strong>Mock mode</strong> — enable portal API key for live getbiller / billInquiry / billPayment</>
        )}
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {ok && <div className="alert alert-ok">{ok}</div>}

      <div className="transfer-mode-tabs">
        <button type="button" className={`btn btn-sm ${tab === 'single' ? 'btn-primary' : 'btn-ghost'}`} onClick={() => setTab('single')}>
          Single UBP
        </button>
        <button type="button" className={`btn btn-sm ${tab === 'bulk' ? 'btn-primary' : 'btn-ghost'}`} onClick={() => setTab('bulk')} style={{ marginLeft: '0.5rem' }}>
          Bulk CSV {liveOn ? '(live)' : '(mock)'}
        </button>
      </div>

      <div className="panel panel--wide" style={{ marginTop: '1.25rem' }}>
        {tab === 'bulk' && liveOn ? (
          <LiveBulkPanel product="UBP" token={session.token} />
        ) : tab === 'bulk' && !liveOn ? (
          <form className="form-grid" onSubmit={submitBulk}>
            <h3 className="form-section-title">UBP — bulk CSV (mock)</h3>
            <div className="actions" style={{ marginTop: 0 }}>
              <button
                type="button"
                className="btn btn-ghost btn-sm"
                onClick={() => {
                  if (!session.token) return;
                  void fetch(apiUrl('/api/transfers/mock/template.csv?productType=UBP'), {
                    headers: { Authorization: `Bearer ${session.token}` },
                  }).then(async (res) => {
                    const blob = await res.blob();
                    const url = URL.createObjectURL(blob);
                    const a = document.createElement('a');
                    a.href = url;
                    a.download = 'dfs-ubp-bulk-template.csv';
                    a.click();
                    URL.revokeObjectURL(url);
                  });
                }}
              >
                Download CSV template
              </button>
            </div>
            <div className="form-row">
              <label>CSV file</label>
              <input type="file" accept=".csv,.txt" onChange={(e) => setFile(e.target.files?.[0] || null)} />
            </div>
            <div className="actions">
              <button className="btn btn-primary" type="submit" disabled={loading || !file}>Upload mock bulk UBP</button>
            </div>
          </form>
        ) : liveOn ? (
          <form className="form-grid" onSubmit={inquiry && isLiveOk(inquiry) ? livePay : liveInquiry}>
            <h3 className="form-section-title">UBP — live single (billers → inquiry → pay)</h3>
            <div className="form-row">
              <label>Biller</label>
              <select
                required
                value={form.utilityCompanyCode}
                onChange={(e) => {
                  setInquiry(null);
                  setForm({ ...form, utilityCompanyCode: e.target.value });
                }}
              >
                <option value="">Select biller</option>
                {liveBillers.map((b) => (
                  <option key={String(b.code || b.id)} value={String(b.code)}>
                    {b.name} ({b.code})
                  </option>
                ))}
              </select>
            </div>
            <div className="form-row">
              <label>Consumer number</label>
              <input
                required
                value={form.consumerNumber}
                onChange={(e) => {
                  setInquiry(null);
                  setForm({ ...form, consumerNumber: e.target.value });
                }}
              />
            </div>
            {inquiry && isLiveOk(inquiry) && (
              <>
                <div className="alert alert-info">Inquiry OK — confirm amount then pay</div>
                <div className="form-row">
                  <label>Amount (PKR)</label>
                  <input required value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} />
                </div>
                <div className="form-row">
                  <label>Customer name</label>
                  <input value={form.beneficiaryName} onChange={(e) => setForm({ ...form, beneficiaryName: e.target.value })} />
                </div>
                <div className="form-row">
                  <label>Notes</label>
                  <input value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} />
                </div>
              </>
            )}
            <div className="actions">
              <button className="btn btn-primary" type="submit" disabled={loading || fetchingBill}>
                {loading || fetchingBill
                  ? 'Working…'
                  : inquiry && isLiveOk(inquiry)
                    ? 'Pay bill'
                    : 'Bill inquiry'}
              </button>
              {inquiry && (
                <button type="button" className="btn btn-ghost" onClick={() => setInquiry(null)}>Reset inquiry</button>
              )}
            </div>
          </form>
        ) : (
          <form className="form-grid" onSubmit={submitMockSingle}>
            <h3 className="form-section-title">Pay a bill (mock)</h3>
            <div className="form-row">
              <label>Bill category</label>
              <select required value={form.ubpCategory} onChange={(e) => setForm({ ...form, ubpCategory: e.target.value, ubpCompany: '' })}>
                <option value="">Select category</option>
                {ubpCategories.map((c) => (
                  <option key={c.code} value={c.code}>{c.label}</option>
                ))}
              </select>
            </div>
            <div className="form-row">
              <label>Company / biller</label>
              <select required disabled={!selectedCategory} value={form.ubpCompany} onChange={(e) => setForm({ ...form, ubpCompany: e.target.value })}>
                <option value="">Select company</option>
                {(selectedCategory?.companies || []).map((b) => (
                  <option key={b.code} value={b.code}>{b.name}</option>
                ))}
              </select>
            </div>
            <div className="form-row">
              <label>{selectedCategory?.consumerLabel || 'Consumer number'}</label>
              <input required value={form.consumerNumber} onChange={(e) => setForm({ ...form, consumerNumber: e.target.value })} />
            </div>
            <div className="actions" style={{ marginTop: 0 }}>
              <button type="button" className="btn btn-ghost btn-sm" disabled={fetchingBill} onClick={() => void fetchBillMock()}>
                {fetchingBill ? 'Fetching…' : 'Fetch bill (mock)'}
              </button>
            </div>
            {fetchedBill && (
              <div className="alert alert-info">
                <strong>{fetchedBill.customerName}</strong> · PKR {fetchedBill.dueAmount}
              </div>
            )}
            <div className="form-row">
              <label>Amount (PKR)</label>
              <input required type="number" min="0.01" step="0.01" value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} />
            </div>
            <div className="actions">
              <button className="btn btn-primary" type="submit" disabled={loading}>{loading ? 'Submitting…' : 'Pay bill (mock)'}</button>
            </div>
          </form>
        )}
      </div>

      {lastLive && (
        <div className="panel panel--wide" style={{ marginTop: '1.25rem' }}>
          <h3>Last live response</h3>
          <p><span className="status status-ACTIVE">{lastLive.responsecode}</span> {lastLive.messages}</p>
          <pre className="transfer-pre">{JSON.stringify(lastLive.data ?? lastLive.raw, null, 2)}</pre>
        </div>
      )}

      {last && !liveOn && (
        <div className="panel panel--wide" style={{ marginTop: '1.25rem' }}>
          <h3>Last mock result</h3>
          <p><strong>{last.mockTxnRef}</strong> · {last.status}</p>
        </div>
      )}

      <div className="panel panel--wide" style={{ marginTop: '1.25rem' }}>
        <h3>UBP history</h3>
        {history.length === 0 && <p className="muted">No UBP payments yet.</p>}
        {history.map((t) => (
          <div className="doc-row" key={t.id}>
            <div>
              <strong>{t.mockTxnRef}</strong>
              <div className="muted">
                {t.amount != null ? `PKR ${t.amount}` : '—'}
                {t.consumerNumber ? ` · ${t.consumerNumber}` : ''}
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
