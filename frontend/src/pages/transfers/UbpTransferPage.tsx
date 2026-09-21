import { type FormEvent, useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { PageHeader } from '../../components/PageHeader';
import { api, apiUrl } from '../../lib/api';
import { useAuth } from '../../auth/AuthContext';
import type { MockTransfer, UbpBill, UbpCategory } from '../../lib/transferTypes';

const emptyForm = {
  amount: '',
  mobile: '',
  beneficiaryName: '',
  notes: '',
  ubpCategory: '',
  ubpCompany: '',
  consumerNumber: '',
  billingMonth: '',
  billDueDate: '',
};

export function UbpTransferPage() {
  const { session } = useAuth();
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

  const selectedCategory = useMemo(
    () => ubpCategories.find((c) => c.code === form.ubpCategory) ?? null,
    [ubpCategories, form.ubpCategory],
  );

  const load = useCallback(async () => {
    if (!session?.token) return;
    try {
      const list = await api<MockTransfer[]>('/api/transfers/mock?productType=UBP', {
        token: session.token,
      });
      setHistory(list);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load history');
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
    void loadUbpCatalog();
  }, [load, loadUbpCatalog]);

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
    // Reset company/consumer when category changes only
    // eslint-disable-next-line react-hooks/exhaustive-deps
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
          mobile: form.mobile || (form.ubpCategory === 'MOBILE' ? form.consumerNumber : undefined),
          beneficiaryName: form.beneficiaryName || undefined,
          notes: form.notes || undefined,
        }),
      });
      setLast(res);
      setOk(`Mock UBP payment success — ${res.mockTxnRef}`);
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
    setError('');
    setOk('');
    setLoading(true);
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
      setOk(`Mock bulk UBP: ${res.bulkRowCount} row(s) — ${res.mockTxnRef}`);
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
        const res = await fetch(apiUrl('/api/transfers/mock/template.csv?productType=UBP'), {
          headers: { Authorization: `Bearer ${session.token}` },
        });
        if (!res.ok) throw new Error('Template download failed');
        const blob = await res.blob();
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'dfs-ubp-bulk-template.csv';
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
        subtitle="Pakistan billers — electricity, gas, water, internet, mobile & tickets (mock)."
        actions={
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => void load()}>
            Refresh
          </button>
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
          Single bill
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
            <h3 className="form-section-title">Pay a bill</h3>
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
            <div className="actions">
              <button className="btn btn-primary" type="submit" disabled={loading}>
                {loading ? 'Submitting…' : 'Pay bill (mock)'}
              </button>
            </div>
          </form>
        ) : (
          <form className="form-grid" onSubmit={submitBulk}>
            <h3 className="form-section-title">UBP — bulk CSV</h3>
            <p className="muted">Columns: category, company, consumer_number, amount, mobile, notes</p>
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
                {loading ? 'Uploading…' : 'Upload mock bulk UBP'}
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
          <p className="muted">
            {last.ubpCategory || '—'} / {last.ubpCompany || '—'}
            {last.consumerNumber ? ` · ${last.consumerNumber}` : ''}
            {last.amount != null ? ` · PKR ${last.amount}` : ''}
          </p>
          {last.bulkSummary && <pre className="transfer-pre">{last.bulkSummary}</pre>}
        </div>
      )}

      <div className="panel panel--wide" style={{ marginTop: '1.25rem' }}>
        <h3>UBP history</h3>
        {history.length === 0 && <p className="muted">No UBP payments yet.</p>}
        {history.map((t) => (
          <div className="doc-row" key={t.id}>
            <div>
              <strong>{t.mode}</strong> · {t.mockTxnRef}
              <div className="muted">
                {t.amount != null ? `PKR ${t.amount}` : '—'}
                {[t.ubpCategory, t.ubpCompany, t.consumerNumber, t.billingMonth]
                  .filter(Boolean)
                  .map((x) => ` · ${x}`)
                  .join('')}
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
