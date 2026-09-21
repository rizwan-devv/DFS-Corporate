import { type FormEvent, useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { PageHeader } from '../../components/PageHeader';
import { TransferBackBar } from '../../components/TransferBackBar';
import { BeneficiaryPicker } from '../../components/BeneficiaryPicker';
import { api, apiUrl } from '../../lib/api';
import { useAuth } from '../../auth/AuthContext';
import type { Beneficiary, MockTransfer, TransferProduct } from '../../lib/transferTypes';
import { TRANSFER_PRODUCTS } from '../../lib/transferTypes';

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

type Props = { product: 'FT' | 'IBFT' };

export function AccountRailTransferPage({ product }: Props) {
  const { session } = useAuth();
  const meta = TRANSFER_PRODUCTS.find((p) => p.id === product)!;
  const [tab, setTab] = useState<'single' | 'bulk'>('single');
  const [form, setForm] = useState(emptyForm);
  const [selectedBenId, setSelectedBenId] = useState<string | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [history, setHistory] = useState<MockTransfer[]>([]);
  const [last, setLast] = useState<MockTransfer | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');

  const load = useCallback(async () => {
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

  useEffect(() => {
    void load();
  }, [load]);

  function applyBeneficiary(b: Beneficiary | null) {
    setSelectedBenId(b?.publicId ?? null);
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

  async function submitSingle(e: FormEvent) {
    e.preventDefault();
    if (!session?.token) return;
    setError('');
    setOk('');
    setLoading(true);
    try {
      const amount = Number(form.amount);
      if (!Number.isFinite(amount) || amount <= 0) throw new Error('Enter a valid amount (PKR)');
      if (!form.accountNumber.trim()) throw new Error('Account number is required');
      if (product === 'IBFT' && !form.bankName.trim()) throw new Error('Bank name is required for IBFT');

      const res = await api<MockTransfer>('/api/transfers/mock/single', {
        method: 'POST',
        token: session.token,
        body: JSON.stringify({
          productType: product as TransferProduct,
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
      setLast(res);
      setOk(`Mock ${product} success — ${res.mockTxnRef}`);
      setForm(emptyForm);
      setSelectedBenId(null);
      await load();
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
      setLast(res);
      setOk(`Mock bulk ${product}: ${res.bulkRowCount} row(s) — ${res.mockTxnRef}`);
      setFile(null);
      await load();
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
      <TransferBackBar />
      <PageHeader
        eyebrow="Transfers"
        title={meta.title}
        subtitle={meta.blurb}
        actions={
          <div className="actions" style={{ marginTop: 0 }}>
            <Link className="btn btn-ghost btn-sm" to="/beneficiaries">Beneficiaries</Link>
            <button type="button" className="btn btn-ghost btn-sm" onClick={() => void load()}>
              Refresh
            </button>
          </div>
        }
      />

      {error && <div className="alert alert-error">{error}</div>}
      {ok && <div className="alert alert-ok">{ok}</div>}

      <div className="transfer-mode-tabs" style={{ marginTop: '0.25rem' }}>
        <button
          type="button"
          className={`btn btn-sm ${tab === 'single' ? 'btn-primary' : 'btn-ghost'}`}
          onClick={() => setTab('single')}
        >
          Single
        </button>
        <button
          type="button"
          className={`btn btn-sm ${tab === 'bulk' ? 'btn-primary' : 'btn-ghost'}`}
          onClick={() => setTab('bulk')}
          style={{ marginLeft: '0.5rem' }}
        >
          Bulk (CSV)
        </button>
      </div>

      <div className="panel panel--wide" style={{ marginTop: '1.25rem' }}>
        {tab === 'single' ? (
          <form className="form-grid" onSubmit={submitSingle}>
            <h3 className="form-section-title">{product} — single payment</h3>
            <BeneficiaryPicker
              product={product}
              selectedPublicId={selectedBenId}
              onSelect={applyBeneficiary}
            />
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
              <label>Bank name{product === 'IBFT' ? '' : ' (optional)'}</label>
              <input
                required={product === 'IBFT'}
                value={form.bankName}
                onChange={(e) => setForm({ ...form, bankName: e.target.value })}
              />
            </div>
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
              <label>Beneficiary name</label>
              <input
                value={form.beneficiaryName}
                onChange={(e) => setForm({ ...form, beneficiaryName: e.target.value })}
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
            <h3 className="form-section-title">{product} — bulk CSV</h3>
            <p className="muted">
              Columns: account_number, ipin, bank_name, amount, cnic, mobile.
              Use saved beneficiaries for single payments; bulk still uses CSV rows.
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
          <h3>Last result</h3>
          <p>
            <span className="status status-ACTIVE">{last.status}</span>{' '}
            <strong>{last.mockTxnRef}</strong> · {last.mode}
          </p>
          {last.bulkSummary && <pre className="transfer-pre">{last.bulkSummary}</pre>}
        </div>
      )}

      <div className="panel panel--wide" style={{ marginTop: '1.25rem' }}>
        <h3>{product} history</h3>
        {history.length === 0 && <p className="muted">No {product} transfers yet.</p>}
        {history.map((t) => (
          <div className="doc-row" key={t.id}>
            <div>
              <strong>{t.mode}</strong> · {t.mockTxnRef}
              <div className="muted">
                {t.amount != null ? `PKR ${t.amount}` : '—'}
                {t.accountNumber ? ` · ${t.accountNumber}` : ''}
                {t.bankName ? ` · ${t.bankName}` : ''}
                {t.beneficiaryName ? ` · ${t.beneficiaryName}` : ''}
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

export function FtTransferPage() {
  return <AccountRailTransferPage product="FT" />;
}

export function IbftTransferPage() {
  return <AccountRailTransferPage product="IBFT" />;
}
