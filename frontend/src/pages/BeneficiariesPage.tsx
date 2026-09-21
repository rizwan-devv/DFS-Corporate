import { type FormEvent, useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { PageHeader } from '../components/PageHeader';
import { TransferBackBar } from '../components/TransferBackBar';
import { api } from '../lib/api';
import { useAuth } from '../auth/AuthContext';
import type { Beneficiary, BeneficiaryRail } from '../lib/transferTypes';
import { railLabel } from '../lib/transferTypes';

const emptyForm = {
  aliasName: '',
  fullName: '',
  accountNumber: '',
  bankName: '',
  raastId: '',
  mobile: '',
  cnic: '',
  railScope: 'FT_IBFT' as BeneficiaryRail,
  notes: '',
  active: true,
};

export function BeneficiariesPage() {
  const { session } = useAuth();
  const [list, setList] = useState<Beneficiary[]>([]);
  const [form, setForm] = useState(emptyForm);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [showInactive, setShowInactive] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');

  const load = useCallback(async () => {
    if (!session?.token) return;
    try {
      const qs = showInactive ? '' : '?activeOnly=true';
      const rows = await api<Beneficiary[]>(`/api/beneficiaries${qs}`, { token: session.token });
      setList(rows);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load beneficiaries');
    }
  }, [session?.token, showInactive]);

  useEffect(() => {
    void load();
  }, [load]);

  function startEdit(b: Beneficiary) {
    setEditingId(b.publicId);
    setForm({
      aliasName: b.aliasName,
      fullName: b.fullName,
      accountNumber: b.accountNumber || '',
      bankName: b.bankName || '',
      raastId: b.raastId || '',
      mobile: b.mobile || '',
      cnic: b.cnic || '',
      railScope: b.railScope,
      notes: b.notes || '',
      active: b.active,
    });
    setError('');
    setOk('');
  }

  function resetForm() {
    setEditingId(null);
    setForm(emptyForm);
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!session?.token) return;
    setError('');
    setOk('');
    setLoading(true);
    try {
      const body = {
        aliasName: form.aliasName.trim(),
        fullName: form.fullName.trim(),
        accountNumber: form.accountNumber.trim() || undefined,
        bankName: form.bankName.trim() || undefined,
        raastId: form.raastId.trim() || undefined,
        mobile: form.mobile.trim() || undefined,
        cnic: form.cnic.trim() || undefined,
        railScope: form.railScope,
        notes: form.notes.trim() || undefined,
        active: form.active,
      };
      if (editingId) {
        await api(`/api/beneficiaries/${editingId}`, {
          method: 'PUT',
          token: session.token,
          body: JSON.stringify(body),
        });
        setOk('Beneficiary updated');
      } else {
        await api('/api/beneficiaries', {
          method: 'POST',
          token: session.token,
          body: JSON.stringify(body),
        });
        setOk('Beneficiary added');
      }
      resetForm();
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Save failed');
    } finally {
      setLoading(false);
    }
  }

  async function deactivate(publicId: string) {
    if (!session?.token) return;
    if (!window.confirm('Deactivate this beneficiary?')) return;
    setError('');
    try {
      await api(`/api/beneficiaries/${publicId}`, {
        method: 'DELETE',
        token: session.token,
      });
      setOk('Beneficiary deactivated');
      if (editingId === publicId) resetForm();
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Deactivate failed');
    }
  }

  const isRaast = form.railScope === 'RAAST';
  const needsBank = form.railScope === 'IBFT' || form.railScope === 'FT_IBFT';

  if (!session) {
    return (
      <div className="portal-page">
        <PageHeader eyebrow="Beneficiaries" title="Login required" subtitle="Sign in to manage payees." />
        <Link className="btn btn-primary" to="/login">Login</Link>
      </div>
    );
  }

  return (
    <div className="portal-page">
      <TransferBackBar />
      <PageHeader
        eyebrow="Finance"
        title="Beneficiaries"
        subtitle="Maintain payees once, then select them on Fund Transfer, IBFT, and Raast."
        actions={
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => void load()}>
            Refresh
          </button>
        }
      />

      {error && <div className="alert alert-error">{error}</div>}
      {ok && <div className="alert alert-ok">{ok}</div>}

      <div className="panel panel--wide">
        <form className="form-grid" onSubmit={submit}>
          <h3 className="form-section-title">{editingId ? 'Edit beneficiary' : 'Add beneficiary'}</h3>
          <div className="form-row">
            <label>Alias / nickname</label>
            <input
              required
              value={form.aliasName}
              onChange={(e) => setForm({ ...form, aliasName: e.target.value })}
              placeholder="e.g. Payroll — ABC Ltd"
            />
          </div>
          <div className="form-row">
            <label>Full name</label>
            <input
              required
              value={form.fullName}
              onChange={(e) => setForm({ ...form, fullName: e.target.value })}
            />
          </div>
          <div className="form-row">
            <label>Rail</label>
            <select
              value={form.railScope}
              onChange={(e) => setForm({ ...form, railScope: e.target.value as BeneficiaryRail })}
            >
              <option value="FT">FT only</option>
              <option value="IBFT">IBFT only</option>
              <option value="FT_IBFT">FT &amp; IBFT</option>
              <option value="RAAST">Raast</option>
            </select>
          </div>
          {isRaast ? (
            <div className="form-row">
              <label>Raast ID / IBAN</label>
              <input
                required
                value={form.raastId}
                onChange={(e) => setForm({ ...form, raastId: e.target.value })}
              />
            </div>
          ) : (
            <>
              <div className="form-row">
                <label>Account number</label>
                <input
                  required
                  value={form.accountNumber}
                  onChange={(e) => setForm({ ...form, accountNumber: e.target.value })}
                />
              </div>
              {needsBank && (
                <div className="form-row">
                  <label>Bank name</label>
                  <input
                    required
                    value={form.bankName}
                    onChange={(e) => setForm({ ...form, bankName: e.target.value })}
                  />
                </div>
              )}
            </>
          )}
          <div className="form-row">
            <label>Mobile</label>
            <input value={form.mobile} onChange={(e) => setForm({ ...form, mobile: e.target.value })} />
          </div>
          <div className="form-row">
            <label>CNIC</label>
            <input value={form.cnic} onChange={(e) => setForm({ ...form, cnic: e.target.value })} />
          </div>
          <div className="form-row">
            <label>Notes</label>
            <input value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} />
          </div>
          {editingId && (
            <div className="form-row">
              <label>
                <input
                  type="checkbox"
                  checked={form.active}
                  onChange={(e) => setForm({ ...form, active: e.target.checked })}
                />{' '}
                Active
              </label>
            </div>
          )}
          <div className="actions">
            <button className="btn btn-primary" type="submit" disabled={loading}>
              {loading ? 'Saving…' : editingId ? 'Update' : 'Add beneficiary'}
            </button>
            {editingId && (
              <button type="button" className="btn btn-ghost" onClick={resetForm}>
                Cancel
              </button>
            )}
          </div>
        </form>
      </div>

      <div className="panel panel--wide" style={{ marginTop: '1.25rem' }}>
        <div className="beneficiary-list-head">
          <h3>Saved beneficiaries</h3>
          <label className="muted" style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
            <input
              type="checkbox"
              checked={showInactive}
              onChange={(e) => setShowInactive(e.target.checked)}
            />
            Show inactive
          </label>
        </div>
        {list.length === 0 && <p className="muted">No beneficiaries yet.</p>}
        {list.map((b) => (
          <div className="doc-row" key={b.publicId}>
            <div>
              <strong>{b.aliasName}</strong>
              {!b.active && <span className="muted"> · inactive</span>}
              <div className="muted">
                {b.fullName} · {railLabel(b.railScope)}
                {b.accountNumber ? ` · ${b.accountNumber}` : ''}
                {b.raastId ? ` · ${b.raastId}` : ''}
                {b.bankName ? ` · ${b.bankName}` : ''}
              </div>
            </div>
            <div className="actions" style={{ marginTop: 0 }}>
              <button type="button" className="btn btn-ghost btn-sm" onClick={() => startEdit(b)}>
                Edit
              </button>
              {b.active && (
                <button
                  type="button"
                  className="btn btn-ghost btn-sm"
                  onClick={() => void deactivate(b.publicId)}
                >
                  Deactivate
                </button>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
