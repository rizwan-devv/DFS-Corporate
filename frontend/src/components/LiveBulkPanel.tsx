import { type FormEvent, useCallback, useEffect, useRef, useState } from 'react';
import { api, apiUrl } from '../lib/api';

export type LiveBulkRow = {
  lineNo: number;
  status: string;
  beneficiaryAccount?: string;
  bankImd?: string;
  utilityCompanyCode?: string;
  consumerNo?: string;
  amount?: string;
  narration?: string;
  responseCode?: string;
  responseMessage?: string;
  txnRef?: string;
  processedAt?: string;
};

export type LiveBulkBatch = {
  publicId: string;
  productType: string;
  status: string;
  fileName?: string;
  totalRows: number;
  successRows: number;
  failedRows: number;
  errorMessage?: string;
  createdAt?: string;
  startedAt?: string;
  finishedAt?: string;
  rows?: LiveBulkRow[];
};

type Props = {
  product: 'FT' | 'IBFT' | 'UBP';
  token: string;
};

const TERMINAL = new Set(['COMPLETED', 'PARTIAL', 'FAILED']);

export function LiveBulkPanel({ product, token }: Props) {
  const [file, setFile] = useState<File | null>(null);
  const [mpin, setMpin] = useState('');
  const [batch, setBatch] = useState<LiveBulkBatch | null>(null);
  const [batches, setBatches] = useState<LiveBulkBatch[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');
  const pollRef = useRef<number | null>(null);

  const loadList = useCallback(async () => {
    try {
      const list = await api<LiveBulkBatch[]>(
        `/api/transfers/live/bulk?productType=${product}`,
        { token },
      );
      setBatches(list);
    } catch {
      /* ignore list errors */
    }
  }, [product, token]);

  useEffect(() => {
    void loadList();
    return () => {
      if (pollRef.current != null) window.clearInterval(pollRef.current);
    };
  }, [loadList]);

  function stopPoll() {
    if (pollRef.current != null) {
      window.clearInterval(pollRef.current);
      pollRef.current = null;
    }
  }

  function startPoll(publicId: string) {
    stopPoll();
    pollRef.current = window.setInterval(() => {
      void (async () => {
        try {
          const b = await api<LiveBulkBatch>(`/api/transfers/live/bulk/${publicId}`, { token });
          setBatch(b);
          if (TERMINAL.has(b.status)) {
            stopPoll();
            setOk(`Bulk ${b.status}: ${b.successRows} ok, ${b.failedRows} failed`);
            void loadList();
          }
        } catch (e) {
          stopPoll();
          setError(e instanceof Error ? e.message : 'Poll failed');
        }
      })();
    }, 2000);
  }

  async function downloadTemplate() {
    setError('');
    try {
      const res = await fetch(
        apiUrl(`/api/transfers/live/bulk/template.csv?productType=${product}`),
        { headers: { Authorization: `Bearer ${token}` } },
      );
      if (!res.ok) throw new Error('Template download failed');
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `live-bulk-${product.toLowerCase()}-template.csv`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Template download failed');
    }
  }

  async function upload(e: FormEvent) {
    e.preventDefault();
    if (!file) {
      setError('Choose a CSV file first');
      return;
    }
    setError('');
    setOk('');
    setLoading(true);
    stopPoll();
    try {
      const fd = new FormData();
      fd.append('productType', product);
      fd.append('file', file);
      const b = await api<LiveBulkBatch>('/api/transfers/live/bulk', {
        method: 'POST',
        token,
        body: fd,
      });
      setBatch(b);
      setFile(null);
      setOk(`Uploaded ${b.totalRows} row(s) as DRAFT — enter ${product === 'FT' ? 'MPIN and ' : ''}Start to process`);
      void loadList();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Upload failed');
    } finally {
      setLoading(false);
    }
  }

  async function startBatch() {
    if (!batch?.publicId) return;
    if (product === 'FT' && mpin.trim().length < 4) {
      setError('Customer MPIN is required for FT bulk');
      return;
    }
    setError('');
    setOk('');
    setLoading(true);
    try {
      const b = await api<LiveBulkBatch>(`/api/transfers/live/bulk/${batch.publicId}/start`, {
        method: 'POST',
        token,
        body: JSON.stringify(product === 'FT' ? { mpin: mpin.trim() } : {}),
      });
      setBatch(b);
      setOk('Batch queued — processing each row via live DFS…');
      setMpin('');
      startPoll(b.publicId);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Start failed');
    } finally {
      setLoading(false);
    }
  }

  async function openBatch(publicId: string) {
    setError('');
    setOk('');
    try {
      const b = await api<LiveBulkBatch>(`/api/transfers/live/bulk/${publicId}`, { token });
      setBatch(b);
      if (!TERMINAL.has(b.status) && b.status !== 'DRAFT') {
        startPoll(publicId);
      } else {
        stopPoll();
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Load failed');
    }
  }

  async function downloadResult() {
    if (!batch?.publicId) return;
    try {
      const res = await fetch(apiUrl(`/api/transfers/live/bulk/${batch.publicId}/result.csv`), {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!res.ok) throw new Error('Result download failed');
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `live-bulk-result-${batch.publicId}.csv`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Result download failed');
    }
  }

  return (
    <div>
      {error && <div className="alert alert-error">{error}</div>}
      {ok && <div className="alert alert-ok">{ok}</div>}

      <form className="form-grid" onSubmit={upload}>
        <h3 className="form-section-title">{product} — live bulk (CSV)</h3>
        <p style={{ margin: 0, opacity: 0.85 }}>
          Separate from single {product}. Each CSV row calls the same live DFS APIs (async). Max 100 rows.
        </p>
        <div className="actions" style={{ marginTop: 0 }}>
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => void downloadTemplate()}>
            Download live CSV template
          </button>
        </div>
        <div className="form-row">
          <label>CSV file</label>
          <input type="file" accept=".csv,.txt" onChange={(e) => setFile(e.target.files?.[0] || null)} />
        </div>
        <div className="actions">
          <button className="btn btn-primary" type="submit" disabled={loading || !file}>
            {loading ? 'Uploading…' : `Upload live bulk ${product}`}
          </button>
        </div>
      </form>

      {batch && (
        <div style={{ marginTop: '1.25rem' }}>
          <h4>
            Batch <code>{batch.publicId.slice(0, 8)}…</code>{' '}
            <span className={`status status-${batch.status === 'COMPLETED' ? 'ACTIVE' : batch.status === 'FAILED' ? 'REJECTED' : 'PENDING'}`}>
              {batch.status}
            </span>
          </h4>
          <p>
            {batch.fileName || '—'} · {batch.successRows}/{batch.totalRows} ok · {batch.failedRows} failed
            {batch.errorMessage ? ` · ${batch.errorMessage}` : ''}
          </p>

          {batch.status === 'DRAFT' && (
            <div className="form-grid" style={{ marginTop: '0.75rem' }}>
              {product === 'FT' && (
                <div className="form-row">
                  <label>Customer MPIN (applied to every FT row)</label>
                  <input
                    type="password"
                    autoComplete="one-time-code"
                    value={mpin}
                    onChange={(e) => setMpin(e.target.value)}
                    placeholder="Required for FT bulk"
                  />
                </div>
              )}
              <div className="actions">
                <button type="button" className="btn btn-primary" disabled={loading} onClick={() => void startBatch()}>
                  {loading ? 'Starting…' : 'Start live bulk'}
                </button>
              </div>
            </div>
          )}

          {TERMINAL.has(batch.status) && (
            <div className="actions" style={{ marginTop: '0.75rem' }}>
              <button type="button" className="btn btn-ghost btn-sm" onClick={() => void downloadResult()}>
                Download result CSV
              </button>
            </div>
          )}

          {batch.rows && batch.rows.length > 0 && (
            <div className="table-wrap" style={{ marginTop: '1rem' }}>
              <table className="data-table">
                <thead>
                  <tr>
                    <th>#</th>
                    <th>Status</th>
                    <th>Amount</th>
                    <th>Target</th>
                    <th>Code</th>
                    <th>Message</th>
                  </tr>
                </thead>
                <tbody>
                  {batch.rows.map((r) => (
                    <tr key={r.lineNo}>
                      <td>{r.lineNo}</td>
                      <td>{r.status}</td>
                      <td>{r.amount || '—'}</td>
                      <td>
                        {r.beneficiaryAccount || r.consumerNo || '—'}
                        {r.bankImd ? ` / ${r.bankImd}` : ''}
                        {r.utilityCompanyCode ? ` (${r.utilityCompanyCode})` : ''}
                      </td>
                      <td>{r.responseCode || '—'}</td>
                      <td>{r.responseMessage || r.txnRef || '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {batches.length > 0 && (
        <div style={{ marginTop: '1.5rem' }}>
          <h4>Recent live bulk batches</h4>
          <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
            {batches.map((b) => (
              <li key={b.publicId} style={{ marginBottom: '0.35rem' }}>
                <button type="button" className="btn btn-ghost btn-sm" onClick={() => void openBatch(b.publicId)}>
                  {b.status} · {b.successRows}/{b.totalRows} · {b.fileName || b.publicId.slice(0, 8)}
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
