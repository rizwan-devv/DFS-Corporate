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
import {
  UBP_UI_CATEGORIES,
  LIVE_UBP_STEPS,
  billersInCategory,
  categoryCounts,
  type LiveUbpStep,
} from '../../lib/ubpCategories';

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

function CategoryIcon({ id }: { id: string }) {
  const common = { width: 28, height: 28, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 1.6, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const };
  switch (id) {
    case 'UTILITY':
      return (
        <svg {...common} aria-hidden>
          <path d="M13 2 4 14h7l-1 8 10-14h-7l1-6z" />
        </svg>
      );
    case 'TELCO':
      return (
        <svg {...common} aria-hidden>
          <rect x="7" y="2" width="10" height="20" rx="2" />
          <path d="M11 18h2" />
        </svg>
      );
    case 'INTERNET':
      return (
        <svg {...common} aria-hidden>
          <path d="M5 12.5a9 9 0 0 1 14 0" />
          <path d="M8.5 15a5 5 0 0 1 7 0" />
          <circle cx="12" cy="18" r="1.2" fill="currentColor" stroke="none" />
        </svg>
      );
    case 'EDUCATION':
      return (
        <svg {...common} aria-hidden>
          <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
          <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" />
          <path d="M8 7h8M8 11h6" />
        </svg>
      );
    case 'GOVERNMENT':
      return (
        <svg {...common} aria-hidden>
          <path d="M3 21h18M5 21V10l7-5 7 5v11M9 21v-6h6v6" />
        </svg>
      );
    case 'INSURANCE':
      return (
        <svg {...common} aria-hidden>
          <path d="M12 3 4 7v5c0 5 3.5 8.5 8 9.5 4.5-1 8-4.5 8-9.5V7l-8-4z" />
        </svg>
      );
    default:
      return (
        <svg {...common} aria-hidden>
          <circle cx="12" cy="12" r="8" />
          <path d="M12 8v4l2.5 2.5" />
        </svg>
      );
  }
}

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
  const [step, setStep] = useState<LiveUbpStep>('category');
  const [categoryId, setCategoryId] = useState('');
  const [companyQuery, setCompanyQuery] = useState('');

  const liveOn = !!live?.liveEnabled;

  const selectedCategory = useMemo(
    () => ubpCategories.find((c) => c.code === form.ubpCategory) ?? null,
    [ubpCategories, form.ubpCategory],
  );

  const counts = useMemo(() => categoryCounts(liveBillers), [liveBillers]);

  const companies = useMemo(() => {
    const list = billersInCategory(liveBillers, categoryId || 'OTHER');
    const q = companyQuery.trim().toLowerCase();
    if (!q) return list;
    return list.filter((b) => `${b.name || ''} ${b.code || ''}`.toLowerCase().includes(q));
  }, [liveBillers, categoryId, companyQuery]);

  const selectedBiller = useMemo(
    () => liveBillers.find((b) => String(b.code) === form.utilityCompanyCode) ?? null,
    [liveBillers, form.utilityCompanyCode],
  );

  const uiCategory = useMemo(
    () => UBP_UI_CATEGORIES.find((c) => c.id === categoryId) ?? null,
    [categoryId],
  );

  const stepIndex = LIVE_UBP_STEPS.findIndex((s) => s.id === step);

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

  function resetLiveWizard(to: LiveUbpStep = 'category') {
    setStep(to);
    setInquiry(null);
    setOk('');
    if (to === 'category') {
      setCategoryId('');
      setCompanyQuery('');
      setForm(emptyForm);
    } else if (to === 'company') {
      setCompanyQuery('');
      setForm((f) => ({ ...f, utilityCompanyCode: '', consumerNumber: '', amount: '', beneficiaryName: '', notes: '' }));
    } else if (to === 'billId') {
      setForm((f) => ({ ...f, consumerNumber: '', amount: '', beneficiaryName: '', notes: '' }));
    }
  }

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

  async function liveInquiry() {
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
      setLastLive(r);
      if (isLiveOk(r)) {
        setOk(r.messages || 'Bill inquiry OK');
        const data = r.data as { dueAmount?: number | string; amount?: number | string; customerName?: string } | undefined;
        const amt = data?.dueAmount ?? data?.amount;
        if (amt != null) setForm((f) => ({ ...f, amount: String(amt) }));
        if (data?.customerName) setForm((f) => ({ ...f, beneficiaryName: data.customerName! }));
        setStep('pay');
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
        resetLiveWizard('category');
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
    <div className="portal-page ubp-page">
      <PageHeader
        eyebrow="Transfers"
        title="Utility Bill Payment"
        subtitle={liveOn ? 'Live DFS billers · categorize · inquire · pay' : 'Mock Pakistan billers'}
        actions={
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            onClick={() => { void loadStatus(); void load(); void loadLiveBillers(); }}
          >
            Refresh
          </button>
        }
      />

      <div className={`alert ${liveOn ? 'alert-ok' : 'alert-info'}`} style={{ marginBottom: '1rem' }}>
        {liveOn ? (
          <><strong>Live mode</strong> — payer {live?.fromAccountNo || '—'} · {liveBillers.length} billers loaded</>
        ) : (
          <><strong>Mock mode</strong> — enable portal API key for live getbiller / billInquiry / billPayment</>
        )}
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {ok && <div className="alert alert-ok">{ok}</div>}

      <div className="transfer-mode-tabs">
        <button type="button" className={`btn btn-sm ${tab === 'single' ? 'btn-primary' : 'btn-ghost'}`} onClick={() => setTab('single')}>
          New payment
        </button>
        <button type="button" className={`btn btn-sm ${tab === 'bulk' ? 'btn-primary' : 'btn-ghost'}`} onClick={() => setTab('bulk')} style={{ marginLeft: '0.5rem' }}>
          Bulk CSV {liveOn ? '(live)' : '(mock)'}
        </button>
      </div>

      <div className="panel panel--wide ubp-panel" style={{ marginTop: '1.25rem' }}>
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
          <div className="ubp-wizard">
            <nav className="ubp-stepper" aria-label="Payment steps">
              {LIVE_UBP_STEPS.map((s, i) => {
                const done = i < stepIndex;
                const active = s.id === step;
                return (
                  <button
                    key={s.id}
                    type="button"
                    className={`ubp-step ${active ? 'is-active' : ''} ${done ? 'is-done' : ''}`}
                    disabled={!done && !active}
                    onClick={() => {
                      if (done) {
                        setError('');
                        if (s.id === 'category') resetLiveWizard('category');
                        else if (s.id === 'company') resetLiveWizard('company');
                        else if (s.id === 'billId') { setInquiry(null); setStep('billId'); }
                        else setStep(s.id);
                      }
                    }}
                  >
                    <span className="ubp-step-index">{done ? '✓' : i + 1}</span>
                    <span className="ubp-step-label">{s.label}</span>
                  </button>
                );
              })}
            </nav>

            {step === 'category' && (
              <section className="ubp-step-body">
                <header className="ubp-step-head">
                  <h3>Select a bill category</h3>
                  <p className="muted">Billers are loaded from live DFS and grouped for faster selection.</p>
                </header>
                <div className="ubp-cat-grid">
                  {UBP_UI_CATEGORIES.map((cat) => {
                    const n = counts[cat.id] || 0;
                    const empty = n === 0 && cat.id !== 'OTHER';
                    return (
                      <button
                        key={cat.id}
                        type="button"
                        className={`ubp-cat-card ${categoryId === cat.id ? 'is-selected' : ''} ${empty ? 'is-empty' : ''}`}
                        disabled={empty && liveBillers.length > 0}
                        onClick={() => {
                          setCategoryId(cat.id);
                          setCompanyQuery('');
                          setForm((f) => ({ ...f, utilityCompanyCode: '' }));
                          setInquiry(null);
                          setError('');
                          setStep('company');
                        }}
                      >
                        <span className="ubp-cat-icon"><CategoryIcon id={cat.id} /></span>
                        <span className="ubp-cat-text">
                          <strong>{cat.label}</strong>
                          <em>{cat.blurb}</em>
                        </span>
                        <span className="ubp-cat-count">{n}</span>
                      </button>
                    );
                  })}
                </div>
                {liveBillers.length === 0 && (
                  <p className="muted ubp-hint">No billers yet — click Refresh or check live portal key.</p>
                )}
              </section>
            )}

            {step === 'company' && (
              <section className="ubp-step-body">
                <header className="ubp-step-head">
                  <button type="button" className="btn btn-ghost btn-sm" onClick={() => resetLiveWizard('category')}>← Categories</button>
                  <h3>{uiCategory?.label || 'Company'}</h3>
                  <p className="muted">Choose the utility company from live DFS billers.</p>
                </header>
                <div className="ubp-search">
                  <input
                    className="input"
                    placeholder="Search company name or code…"
                    value={companyQuery}
                    onChange={(e) => setCompanyQuery(e.target.value)}
                    autoFocus
                  />
                </div>
                <div className="ubp-company-list">
                  {companies.length === 0 && (
                    <p className="muted">No companies in this category. Try “Other billers” or another category.</p>
                  )}
                  {companies.map((b) => {
                    const code = String(b.code || '');
                    const selected = form.utilityCompanyCode === code;
                    return (
                      <button
                        key={code || String(b.id)}
                        type="button"
                        className={`ubp-company-row ${selected ? 'is-selected' : ''}`}
                        onClick={() => {
                          setForm((f) => ({ ...f, utilityCompanyCode: code }));
                          setInquiry(null);
                          setError('');
                          setStep('billId');
                        }}
                      >
                        <span className="ubp-company-name">{b.name || code}</span>
                        <span className="ubp-company-code">{code}</span>
                      </button>
                    );
                  })}
                </div>
              </section>
            )}

            {step === 'billId' && (
              <section className="ubp-step-body">
                <header className="ubp-step-head">
                  <button type="button" className="btn btn-ghost btn-sm" onClick={() => { setInquiry(null); setStep('company'); }}>← Companies</button>
                  <h3>Enter bill ID</h3>
                  <p className="muted">
                    {selectedBiller?.name || form.utilityCompanyCode}
                    {form.utilityCompanyCode ? ` · ${form.utilityCompanyCode}` : ''}
                  </p>
                </header>
                <div className="ubp-form-card">
                  <div className="form-row">
                    <label>Consumer / account / bill number</label>
                    <input
                      className="input"
                      required
                      autoFocus
                      value={form.consumerNumber}
                      onChange={(e) => {
                        setInquiry(null);
                        setForm({ ...form, consumerNumber: e.target.value });
                      }}
                      placeholder="Enter consumer number"
                    />
                  </div>
                  <div className="actions">
                    <button
                      type="button"
                      className="btn btn-primary"
                      disabled={!form.consumerNumber.trim()}
                      onClick={() => { setError(''); setStep('fetch'); }}
                    >
                      Continue
                    </button>
                  </div>
                </div>
              </section>
            )}

            {step === 'fetch' && (
              <section className="ubp-step-body">
                <header className="ubp-step-head">
                  <button type="button" className="btn btn-ghost btn-sm" onClick={() => setStep('billId')}>← Bill ID</button>
                  <h3>Fetch bill</h3>
                  <p className="muted">Live DFS bill inquiry for this consumer.</p>
                </header>
                <div className="ubp-summary-card">
                  <div><span className="muted">Biller</span><strong>{selectedBiller?.name || form.utilityCompanyCode}</strong></div>
                  <div><span className="muted">Code</span><strong className="ubp-mono">{form.utilityCompanyCode}</strong></div>
                  <div><span className="muted">Consumer</span><strong className="ubp-mono">{form.consumerNumber}</strong></div>
                </div>
                <div className="actions">
                  <button
                    type="button"
                    className="btn btn-primary"
                    disabled={fetchingBill}
                    onClick={() => void liveInquiry()}
                  >
                    {fetchingBill ? 'Fetching…' : 'Fetch bill details'}
                  </button>
                </div>
              </section>
            )}

            {step === 'pay' && (
              <section className="ubp-step-body">
                <header className="ubp-step-head">
                  <button type="button" className="btn btn-ghost btn-sm" onClick={() => { setInquiry(null); setStep('fetch'); }}>← Fetch</button>
                  <h3>Confirm &amp; pay</h3>
                  <p className="muted">Review amount, then submit live bill payment.</p>
                </header>
                <form className="ubp-form-card" onSubmit={livePay}>
                  {inquiry && isLiveOk(inquiry) && (
                    <div className="ubp-inquiry-badge">Inquiry OK · {inquiry.messages || 'ready to pay'}</div>
                  )}
                  <div className="ubp-summary-card ubp-summary-card--compact">
                    <div><span className="muted">Biller</span><strong>{selectedBiller?.name || form.utilityCompanyCode}</strong></div>
                    <div><span className="muted">Consumer</span><strong className="ubp-mono">{form.consumerNumber}</strong></div>
                  </div>
                  <div className="form-row">
                    <label>Amount (PKR)</label>
                    <input
                      className="input"
                      required
                      value={form.amount}
                      onChange={(e) => setForm({ ...form, amount: e.target.value })}
                    />
                  </div>
                  <div className="form-row">
                    <label>Customer name</label>
                    <input
                      className="input"
                      value={form.beneficiaryName}
                      onChange={(e) => setForm({ ...form, beneficiaryName: e.target.value })}
                    />
                  </div>
                  <div className="form-row">
                    <label>Notes</label>
                    <input
                      className="input"
                      value={form.notes}
                      onChange={(e) => setForm({ ...form, notes: e.target.value })}
                    />
                  </div>
                  <div className="actions">
                    <button className="btn btn-primary" type="submit" disabled={loading || !form.amount}>
                      {loading ? 'Paying…' : 'Pay bill'}
                    </button>
                    <button type="button" className="btn btn-ghost" onClick={() => resetLiveWizard('category')}>
                      Start over
                    </button>
                  </div>
                </form>
              </section>
            )}
          </div>
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
