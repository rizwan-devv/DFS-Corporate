import { type FormEvent, useCallback, useEffect, useState } from 'react';
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
  createdAt?: string;
};

const PRODUCTS: { id: Product; label: string; bulk: boolean; blurb: string }[] = [
  { id: 'FT', label: 'FT', bulk: true, blurb: 'Fund transfer (mock)' },
  { id: 'IBFT', label: 'IBFT', bulk: true, blurb: 'Interbank fund transfer (mock)' },
  { id: 'UBP', label: 'UBP', bulk: true, blurb: 'Utility / bill pay (mock — API later)' },
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
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');

  const meta = PRODUCTS.find((p) => p.id === product)!;
  const bulkAllowed = meta.bulk;

  const load = useCallback(async () => {
    if (!session?.token) return;
    try {
      const list = await api<MockTransfer[]>('/api/transfers/mock', { token: session.token });
      setHistory(list);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load mock transfers');
    }
  }, [session?.token]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!bulkAllowed && tab === 'bulk') setTab('single');
  }, [bulkAllowed, tab]);

  async function submitSingle(e: FormEvent) {
    e.preventDefault();
    if (!session?.token) return;
    setError('');
    setOk('');
    setLoading(true);
    try {
      const amount = Number(form.amount);
      if (!Number.isFinite(amount) || amount <= 0) {
        throw new Error('Enter a valid amount');
      }
      const body = {
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
      setOk(`Mock ${product} success — ${res.mockTxnRef}`);
      setForm(emptyForm);
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
        subtitle="Portal mocks for FT, IBFT, UBP (single + CSV bulk) and Raast (single + QR). Not connected to live AgentApp."
        actions={
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => void load()}>
            Refresh history
          </button>
        }
      />

      <div className="alert alert-info" style={{ marginBottom: '1rem' }}>
        <strong>Mock mode</strong> — no money moves. Bulk uses CSV (Excel → Save As CSV). Raast has no bulk.
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
            onClick={() => setProduct(p.id)}
          >
            {p.label}
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
            <h3 className="form-section-title">{product} — single (mock)</h3>
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
              <label>Amount</label>
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
            <div className="actions">
              <button className="btn btn-primary" type="submit" disabled={loading}>
                {loading ? 'Submitting…' : `Submit mock ${product}`}
              </button>
            </div>
          </form>
        ) : (
          <form className="form-grid" onSubmit={submitBulk}>
            <h3 className="form-section-title">{product} — bulk CSV (mock)</h3>
            <p className="muted">
              Columns: account_number, ipin, bank_name, amount, cnic, mobile
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
            <span className={`status status-ACTIVE`}>{last.status}</span>{' '}
            <strong>{last.mockTxnRef}</strong> · {last.productType} · {last.mode}
          </p>
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
              <strong>{t.productType}</strong> · {t.mode} · {t.mockTxnRef}
              <div className="muted">
                {t.amount != null ? `PKR ${t.amount}` : '—'}
                {t.accountNumber ? ` · ${t.accountNumber}` : ''}
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
