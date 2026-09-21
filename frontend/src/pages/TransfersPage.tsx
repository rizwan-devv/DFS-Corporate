import { type FormEvent, useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { PageHeader } from '../components/PageHeader';
import { api, apiUrl } from '../lib/api';
import { useAuth } from '../auth/AuthContext';

type Product = 'FT' | 'IBFT' | 'UBP' | 'RAAST';

type MockTransfer = {
  id: number;
  publicId: string;
  productType: Product;
  mode: 'SINGLE' | 'BULK';
  status: string;
  mockTxnRef: string;
  accountNumber?: string;
  ipin?: string;
  bankName?: string;
  amount?: number;
  cnic?: string;
  mobile?: string;
  beneficiaryName?: string;
  notes?: string;
  bulkFileName?: string;
  bulkRowCount?: number;
  bulkSummary?: string;
  raastQrPayload?: string;
  raastQrDataUrl?: string;
  ubpCategory?: string;
  ubpCompany?: string;
  consumerNumber?: string;
  billingMonth?: string;
  billDueDate?: string;
  createdAt?: string;
};

type UbpBiller = { code: string; name: string };
type UbpCategory = {
  code: string;
  label: string;
  consumerLabel: string;
  companies: UbpBiller[];
};

type UbpBill = {
  customerName?: string;
  billingMonth?: string;
  dueDate?: string;
  dueAmount?: number;
  companyName?: string;
  consumerNumber?: string;
  categoryLabel?: string;
  message?: string;
};

const PRODUCTS: { id: Product; label: string; bulk: boolean; blurb: string }[] = [
  { id: 'FT', label: 'FT', bulk: true, blurb: 'Fund transfer (mock)' },
  { id: 'IBFT', label: 'IBFT', bulk: true, blurb: 'Interbank fund transfer (mock)' },
  {
    id: 'UBP',
    label: 'UBP',
    bulk: true,
    blurb: 'Utility Bill Payment — electricity, gas, water, internet, mobile & tickets (Pakistan mock)',
  },
  { id: 'RAAST', label: 'Raast', bulk: false, blurb: 'Single + QR only (no bulk)' },
];

const emptyForm = {
  accountNumber: '',
  ipin: '',
  bankName: '',
  amount: '',
  cnic: '',
  mobile: '',
  beneficiaryName: '',
  notes: '',
  ubpCategory: '',
  ubpCompany: '',
  consumerNumber: '',
  billingMonth: '',
  billDueDate: '',
};

export function TransfersPage() {
  const { session } = useAuth();
  const [product, setProduct] = useState<Product>('FT');
  const [tab, setTab] = useState<'single' | 'bulk'>('single');
  const [form, setForm] = useState(emptyForm);
  const [file, setFile] = useState<File | null>(null);
  const [history, setHistory] = useState<MockTransfer[]>([]);
  const [last, setLast] = useState<MockTransfer | null>(null);
  const [loading, setLoading] = useState(false);
  const [fetchingBill, setFetchingBill] = useState(false);
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');
  const [ubpCategories, setUbpCategories] = useState<UbpCategory[]>([]);
  const [fetchedBill, setFetchedBill] = useState<UbpBill | null>(null);

  const meta = PRODUCTS.find((p) => p.id === product)!;
  const bulkAllowed = meta.bulk;
  const selectedCategory = useMemo(
    () => ubpCategories.find((c) => c.code === form.ubpCategory) ?? null,
    [ubpCategories, form.ubpCategory],
  );

  const load = useCallback(async () => {
    if (!session?.token) return;
    try {
      const list = await api<MockTransfer[]>('/api/transfers/mock', { token: session.token });
      setHistory(list);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load mock transfers');
    }
  }, [session?.token]);

  const loadUbpCatalog = useCallback(async () => {
    if (!session?.token) return;
    try {
      const data = await api<{ categories: UbpCategory[] }>('/api/transfers/mock/ubp/catalog', {
        token: session.token,
      });
      setUbpCategories(data.categories || []);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load UBP catalog');
    }
  }, [session?.token]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (product === 'UBP') void loadUbpCatalog();
  }, [product, loadUbpCatalog]);

  useEffect(() => {
    if (!bulkAllowed && tab === 'bulk') setTab('single');
  }, [bulkAllowed, tab]);

  useEffect(() => {
    setFetchedBill(null);
    setForm((f) => ({
      ...f,
      ubpCompany: '',
      consumerNumber: '',
      amount: '',
      billingMonth: '',
      billDueDate: '',
      beneficiaryName: '',
    }));
  }, [form.ubpCategory]);

  async function fetchBill() {
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
        mobile: f.ubpCategory === 'MOBILE' ? f.consumerNumber : f.mobile,
      }));
      setOk(`Mock bill fetched — ${bill.customerName || 'customer'} · due PKR ${bill.dueAmount}`);
    } catch (err) {
      setFetchedBill(null);
      setError(err instanceof Error ? err.message : 'Fetch bill failed');
    } finally {
      setFetchingBill(false);
    }
  }

  async function submitSingle(e: FormEvent) {
    e.preventDefault();
    if (!session?.token) return;
    setError('');
    setOk('');
    setLoading(true);
    try {
      const amount = Number(form.amount);
      if (!Number.isFinite(amount) || amount <= 0) {
        throw new Error('Enter a valid amount (PKR)');
      }
      const body =
        product === 'UBP'
          ? {
              productType: product,
              amount,
              ubpCategory: form.ubpCategory || undefined,
              ubpCompany: form.ubpCompany || undefined,
              consumerNumber: form.consumerNumber || undefined,
              billingMonth: form.billingMonth || undefined,
              billDueDate: form.billDueDate || undefined,
              mobile: form.mobile || (form.ubpCategory === 'MOBILE' ? form.consumerNumber : undefined),
              beneficiaryName: form.beneficiaryName || undefined,
              notes: form.notes || undefined,
            }
          : {
              productType: product,
              accountNumber: form.accountNumber || undefined,
              ipin: form.ipin || undefined,
              bankName: form.bankName || undefined,
              amount,
              cnic: form.cnic || undefined,
              mobile: form.mobile || undefined,
              beneficiaryName: form.beneficiaryName || undefined,
              notes: form.notes || undefined,
            };
      const res = await api<MockTransfer>('/api/transfers/mock/single', {
        method: 'POST',
        token: session.token,
        body: JSON.stringify(body),
      });
      setLast(res);
      setOk(`Mock ${product === 'UBP' ? 'UBP payment' : product} success — ${res.mockTxnRef}`);
      setForm(emptyForm);
      setFetchedBill(null);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Mock transfer failed');
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
      setLast(res);
      setOk(`Mock bulk ${product}: ${res.bulkRowCount} row(s) — ${res.mockTxnRef}`);
      setFile(null);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Bulk mock failed');
    } finally {
      setLoading(false);
    }
  }

  function downloadTemplate() {
    if (!session?.token) return;
    void (async () => {
      try {
        const qs = product === 'UBP' ? '?productType=UBP' : '';
        const res = await fetch(apiUrl(`/api/transfers/mock/template.csv${qs}`), {
          headers: { Authorization: `Bearer ${session.token}` },
        });
        if (!res.ok) throw new Error('Template download failed');
        const blob = await res.blob();
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = product === 'UBP' ? 'dfs-ubp-bulk-template.csv' : 'dfs-bulk-transfer-template.csv';
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
        <PageHeader eyebrow="Transfers" title="Login required" subtitle="Sign in as an ACTIVE corporate to use mock transfers." />
        <Link className="btn btn-primary" to="/login">Login</Link>
      </div>
    );
  }

  return (
    <div className="portal-page">
      <PageHeader
        eyebrow="Finance · Mock"
        title="Transfers"
        subtitle="Portal mocks for FT, IBFT, UBP (Pakistan bill pay) and Raast. Not connected to live AgentApp."
        actions={
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => void load()}>
            Refresh history
          </button>
        }
      />

      <div className="alert alert-info" style={{ marginBottom: '1rem' }}>
        <strong>Mock mode</strong> — no money moves. UBP uses Pakistan billers (LESCO, SNGPL, Jazz, PTCL, Railways…). Bulk uses CSV.
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {ok && <div className="alert alert-ok">{ok}</div>}

      <div className="transfer-product-tabs" role="tablist" aria-label="Product">
        {PRODUCTS.map((p) => (
          <button
            key={p.id}
            type="button"
            role="tab"
            className={`transfer-tab ${product === p.id ? 'active' : ''}`}
            onClick={() => {
              setProduct(p.id);
              setFetchedBill(null);
              setForm(emptyForm);
            }}
          >
            {p.id === 'UBP' ? 'UBP · Bills' : p.label}
          </button>
        ))}
      </div>
      <p className="muted" style={{ marginTop: '0.5rem' }}>{meta.blurb}</p>

      <div className="transfer-mode-tabs" style={{ marginTop: '1rem' }}>
        <button
          type="button"
          className={`btn btn-sm ${tab === 'single' ? 'btn-primary' : 'btn-ghost'}`}
          onClick={() => setTab('single')}
        >
          Single
        </button>
        {bulkAllowed && (
          <button
            type="button"
            className={`btn btn-sm ${tab === 'bulk' ? 'btn-primary' : 'btn-ghost'}`}
            onClick={() => setTab('bulk')}
            style={{ marginLeft: '0.5rem' }}
          >
            Bulk (CSV)
          </button>
        )}
      </div>

      <div className="panel panel--wide" style={{ marginTop: '1.25rem' }}>
        {tab === 'single' ? (
          <form className="form-grid" onSubmit={submitSingle}>
            <h3 className="form-section-title">
              {product === 'UBP' ? 'Utility Bill Payment — single (mock)' : `${product} — single (mock)`}
            </h3>

            {product === 'UBP' ? (
              <>
                <div className="form-row">
                  <label>Bill category</label>
                  <select
                    required
                    value={form.ubpCategory}
                    onChange={(e) => setForm({ ...form, ubpCategory: e.target.value })}
                  >
                    <option value="">Select category</option>
                    {ubpCategories.map((c) => (
                      <option key={c.code} value={c.code}>{c.label}</option>
                    ))}
                  </select>
                </div>
                <div className="form-row">
                  <label>Company / biller</label>
                  <select
                    required
                    disabled={!selectedCategory}
                    value={form.ubpCompany}
                    onChange={(e) => {
                      setFetchedBill(null);
                      setForm({ ...form, ubpCompany: e.target.value });
                    }}
                  >
                    <option value="">Select company</option>
                    {(selectedCategory?.companies || []).map((b) => (
                      <option key={b.code} value={b.code}>{b.name}</option>
                    ))}
                  </select>
                </div>
                <div className="form-row">
                  <label>{selectedCategory?.consumerLabel || 'Consumer / reference number'}</label>
                  <input
                    required
                    placeholder={form.ubpCategory === 'MOBILE' ? '03XXXXXXXXX' : 'Consumer / bill number'}
                    value={form.consumerNumber}
                    onChange={(e) => {
                      setFetchedBill(null);
                      setForm({ ...form, consumerNumber: e.target.value });
                    }}
                  />
                </div>
                <div className="actions" style={{ marginTop: 0 }}>
                  <button
                    type="button"
                    className="btn btn-ghost btn-sm"
                    disabled={fetchingBill || !form.ubpCategory || !form.ubpCompany || !form.consumerNumber}
                    onClick={() => void fetchBill()}
                  >
                    {fetchingBill ? 'Fetching…' : 'Fetch bill (mock)'}
                  </button>
                </div>
                {fetchedBill && (
                  <div className="alert alert-info" style={{ margin: '0.5rem 0 0' }}>
                    <strong>{fetchedBill.customerName}</strong>
                    {' · '}
                    {fetchedBill.categoryLabel} / {fetchedBill.companyName}
                    <br />
                    Bill month {fetchedBill.billingMonth} · Due {fetchedBill.dueDate} ·{' '}
                    <strong>PKR {fetchedBill.dueAmount}</strong>
                    <div className="muted" style={{ marginTop: '0.25rem' }}>{fetchedBill.message}</div>
                  </div>
                )}
                <div className="form-row">
                  <label>Amount (PKR)</label>
                  <input
                    required
                    type="number"
                    min="0.01"
                    step="0.01"
                    value={form.amount}
                    onChange={(e) => setForm({ ...form, amount: e.target.value })}
                  />
                </div>
                {(form.ubpCategory === 'ELECTRICITY' || form.ubpCategory === 'GAS' || form.ubpCategory === 'WATER') && (
                  <div className="form-row">
                    <label>Billing month</label>
                    <input
                      value={form.billingMonth}
                      onChange={(e) => setForm({ ...form, billingMonth: e.target.value })}
                      placeholder="e.g. Aug 2026"
                    />
                  </div>
                )}
                {(form.ubpCategory === 'TICKETS' || form.ubpCategory === 'EDUCATION') && (
                  <div className="form-row">
                    <label>{form.ubpCategory === 'TICKETS' ? 'Passenger / booking name' : 'Student name'}</label>
                    <input
                      value={form.beneficiaryName}
                      onChange={(e) => setForm({ ...form, beneficiaryName: e.target.value })}
                    />
                  </div>
                )}
                {form.ubpCategory !== 'MOBILE' && (
                  <div className="form-row">
                    <label>Mobile (optional)</label>
                    <input
                      value={form.mobile}
                      onChange={(e) => setForm({ ...form, mobile: e.target.value })}
                      placeholder="03XXXXXXXXX"
                    />
                  </div>
                )}
                <div className="form-row">
                  <label>Notes</label>
                  <input value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} />
                </div>
              </>
            ) : (
              <>
                {product !== 'RAAST' && (
                  <>
                    <div className="form-row">
                      <label>Account number</label>
                      <input
                        required
                        value={form.accountNumber}
                        onChange={(e) => setForm({ ...form, accountNumber: e.target.value })}
                      />
                    </div>
                    <div className="form-row">
                      <label>IPIN</label>
                      <input value={form.ipin} onChange={(e) => setForm({ ...form, ipin: e.target.value })} />
                    </div>
                    <div className="form-row">
                      <label>Bank name</label>
                      <input value={form.bankName} onChange={(e) => setForm({ ...form, bankName: e.target.value })} />
                    </div>
                  </>
                )}
                <div className="form-row">
                  <label>Amount (PKR)</label>
                  <input
                    required
                    type="number"
                    min="0.01"
                    step="0.01"
                    value={form.amount}
                    onChange={(e) => setForm({ ...form, amount: e.target.value })}
                  />
                </div>
                <div className="form-row">
                  <label>CNIC</label>
                  <input value={form.cnic} onChange={(e) => setForm({ ...form, cnic: e.target.value })} />
                </div>
                <div className="form-row">
                  <label>Mobile</label>
                  <input value={form.mobile} onChange={(e) => setForm({ ...form, mobile: e.target.value })} />
                </div>
                <div className="form-row">
                  <label>Beneficiary name</label>
                  <input
                    value={form.beneficiaryName}
                    onChange={(e) => setForm({ ...form, beneficiaryName: e.target.value })}
                  />
                </div>
                <div className="form-row">
                  <label>Notes</label>
                  <input value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} />
                </div>
              </>
            )}

            <div className="actions">
              <button className="btn btn-primary" type="submit" disabled={loading}>
                {loading ? 'Submitting…' : product === 'UBP' ? 'Pay bill (mock)' : `Submit mock ${product}`}
              </button>
            </div>
          </form>
        ) : (
          <form className="form-grid" onSubmit={submitBulk}>
            <h3 className="form-section-title">
              {product === 'UBP' ? 'UBP — bulk CSV (mock)' : `${product} — bulk CSV (mock)`}
            </h3>
            <p className="muted">
              {product === 'UBP'
                ? 'Columns: category, company, consumer_number, amount, mobile, notes'
                : 'Columns: account_number, ipin, bank_name, amount, cnic, mobile'}
            </p>
            <div className="actions" style={{ marginTop: 0 }}>
              <button type="button" className="btn btn-ghost btn-sm" onClick={downloadTemplate}>
                Download CSV template
              </button>
            </div>
            <div className="form-row">
              <label>CSV file</label>
              <input
                type="file"
                accept=".csv,.txt"
                onChange={(e) => setFile(e.target.files?.[0] || null)}
              />
            </div>
            <div className="actions">
              <button className="btn btn-primary" type="submit" disabled={loading || !file}>
                {loading ? 'Uploading…' : `Upload mock bulk ${product}`}
              </button>
            </div>
          </form>
        )}
      </div>

      {last && (
        <div className="panel panel--wide" style={{ marginTop: '1.25rem' }}>
          <h3>Last mock result</h3>
          <p>
            <span className="status status-ACTIVE">{last.status}</span>{' '}
            <strong>{last.mockTxnRef}</strong> · {last.productType} · {last.mode}
          </p>
          {last.productType === 'UBP' && (
            <p className="muted">
              {last.ubpCategory || '—'} / {last.ubpCompany || '—'}
              {last.consumerNumber ? ` · ${last.consumerNumber}` : ''}
              {last.billingMonth ? ` · ${last.billingMonth}` : ''}
              {last.amount != null ? ` · PKR ${last.amount}` : ''}
              {last.beneficiaryName ? ` · ${last.beneficiaryName}` : ''}
            </p>
          )}
          {last.bulkSummary && <pre className="transfer-pre">{last.bulkSummary}</pre>}
          {last.raastQrDataUrl && (
            <div className="transfer-qr">
              <img src={last.raastQrDataUrl} alt="Mock Raast QR" width={200} height={200} />
              <p className="muted">Mock Raast QR (portal display). Payload: {last.raastQrPayload}</p>
            </div>
          )}
        </div>
      )}

      <div className="panel panel--wide" style={{ marginTop: '1.25rem' }}>
        <h3>Mock history</h3>
        {history.length === 0 && <p className="muted">No mock transfers yet.</p>}
        {history.map((t) => (
          <div className="doc-row" key={t.id}>
            <div>
              <strong>{t.productType === 'UBP' ? 'UBP · Bill' : t.productType}</strong> · {t.mode} · {t.mockTxnRef}
              <div className="muted">
                {t.amount != null ? `PKR ${t.amount}` : '—'}
                {t.productType === 'UBP'
                  ? [
                      t.ubpCategory,
                      t.ubpCompany,
                      t.consumerNumber,
                      t.billingMonth,
                    ]
                      .filter(Boolean)
                      .map((x) => ` · ${x}`)
                      .join('')
                  : t.accountNumber
                    ? ` · ${t.accountNumber}`
                    : ''}
                {t.bulkRowCount != null ? ` · ${t.bulkRowCount} rows` : ''}
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
