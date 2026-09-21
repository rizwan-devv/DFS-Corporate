import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../lib/api';
import { useAuth } from '../auth/AuthContext';
import type { Beneficiary, TransferProduct } from '../lib/transferTypes';
import { railLabel } from '../lib/transferTypes';

type Props = {
  product: Exclude<TransferProduct, 'UBP'>;
  onSelect: (b: Beneficiary | null) => void;
  selectedPublicId?: string | null;
};

export function BeneficiaryPicker({ product, onSelect, selectedPublicId }: Props) {
  const { session } = useAuth();
  const [list, setList] = useState<Beneficiary[]>([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    if (!session?.token) return;
    setLoading(true);
    setError('');
    try {
      const rows = await api<Beneficiary[]>(
        `/api/beneficiaries?forProduct=${product}&activeOnly=true`,
        { token: session.token },
      );
      setList(rows);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load beneficiaries');
    } finally {
      setLoading(false);
    }
  }, [session?.token, product]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div className="beneficiary-picker">
      <div className="beneficiary-picker-head">
        <label htmlFor="beneficiary-select">Saved beneficiary</label>
        <Link to="/beneficiaries" className="btn btn-ghost btn-sm">
          Manage
        </Link>
      </div>
      <select
        id="beneficiary-select"
        value={selectedPublicId || ''}
        disabled={loading}
        onChange={(e) => {
          const id = e.target.value;
          if (!id) {
            onSelect(null);
            return;
          }
          onSelect(list.find((b) => b.publicId === id) || null);
        }}
      >
        <option value="">{loading ? 'Loading…' : 'Enter details manually'}</option>
        {list.map((b) => (
          <option key={b.publicId} value={b.publicId}>
            {b.aliasName} — {b.fullName}
            {b.accountNumber ? ` · ${b.accountNumber}` : b.raastId ? ` · ${b.raastId}` : ''}
            {` (${railLabel(b.railScope)})`}
          </option>
        ))}
      </select>
      {error && <p className="muted" style={{ marginTop: '0.35rem', color: 'var(--danger, #f87171)' }}>{error}</p>}
      {!loading && list.length === 0 && (
        <p className="muted" style={{ marginTop: '0.35rem' }}>
          No saved beneficiaries for {product}.{' '}
          <Link to="/beneficiaries">Add one</Link> to speed up transfers.
        </p>
      )}
    </div>
  );
}
