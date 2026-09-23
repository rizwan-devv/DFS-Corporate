import { type FormEvent, useCallback, useEffect, useState } from 'react';
import { PageHeader } from '../components/PageHeader';
import { api, apiUrl } from '../lib/api';
import { useAuth } from '../auth/AuthContext';

type EmployeeRow = {
  publicId: string;
  lineNo: number;
  status: string;
  employeeCode?: string;
  fullName?: string;
  fatherName?: string;
  mobile?: string;
  cnic?: string;
  dateOfBirth?: string;
  gender?: string;
  email?: string;
  department?: string;
  parkRef?: string;
  dfsAccountNo?: string;
  dfsCustomerId?: string;
  responseMessage?: string;
  parkedAt?: string;
  confirmedAt?: string;
};

type EmployeeBatch = {
  publicId: string;
  status: string;
  fileName?: string;
  totalRows: number;
  parkedRows: number;
  openRows: number;
  failedRows: number;
  errorMessage?: string;
  createdAt?: string;
  parkedAt?: string;
  finishedAt?: string;
  rows?: EmployeeRow[];
};

function statusClass(st: string): string {
  if (st === 'OPEN' || st === 'COMPLETED') return 'ACTIVE';
  if (st === 'FAILED' || st === 'REJECTED' || st === 'INVALID') return 'REJECTED';
  if (st === 'PARKED' || st === 'PARTIAL') return 'PENDING';
  return 'SUBMITTED';
}

export function EmployeeOnboardPage() {
  const { session } = useAuth();
  const token = session?.token || '';
  const [file, setFile] = useState<File | null>(null);
  const [batch, setBatch] = useState<EmployeeBatch | null>(null);
  const [batches, setBatches] = useState<EmployeeBatch[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');

  const loadList = useCallback(async () => {
    if (!token) return;
    try {
      setBatches(await api<EmployeeBatch[]>('/api/employees/bulk', { token }));
    } catch {
      /* ignore */
    }
  }, [token]);

  useEffect(() => {
    void loadList();
  }, [loadList]);

  async function downloadTemplate() {
    setError('');
    try {
      const res = await fetch(apiUrl('/api/employees/bulk/template.csv'), {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!res.ok) throw new Error('Template download failed');
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'employee-bulk-template.csv';
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Template download failed');
    }
  }

  async function upload(e: FormEvent) {
    e.preventDefault();
    if (!file || !token) {
      setError('Choose a CSV file first');
      return;
    }
    setError('');
    setOk('');
    setLoading(true);
    try {
      const fd = new FormData();
      fd.append('file', file);
      const b = await api<EmployeeBatch>('/api/employees/bulk', {
        method: 'POST',
        token,
        body: fd,
      });
      setBatch(b);
      setFile(null);
      const invalid = (b.rows || []).filter((r) => r.status === 'INVALID').length;
      setOk(
        invalid
          ? `Uploaded ${b.totalRows} row(s) — ${invalid} invalid. Fix CSV or park valid rows only.`
          : `Uploaded ${b.totalRows} row(s) as DRAFT — click Park on DFS to submit.`,
      );
      void loadList();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Upload failed');
    } finally {
      setLoading(false);
    }
  }

  async function parkBatch() {
    if (!batch?.publicId || !token) return;
    setError('');
    setOk('');
    setLoading(true);
    try {
      const b = await api<EmployeeBatch>(`/api/employees/bulk/${batch.publicId}/park`, {
        method: 'POST',
        token,
        body: JSON.stringify({}),
      });
      setBatch(b);
      setOk(`Parked ${b.parkedRows} · opened ${b.openRows} · failed ${b.failedRows}. Check row statuses.`);
      void loadList();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Park failed');
    } finally {
      setLoading(false);
    }
  }

  async function openBatch(publicId: string) {
    if (!token) return;
    setError('');
    setOk('');
    try {
      setBatch(await api<EmployeeBatch>(`/api/employees/bulk/${publicId}`, { token }));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Load failed');
    }
  }

  async function downloadResult() {
    if (!batch?.publicId || !token) return;
    try {
      const res = await fetch(apiUrl(`/api/employees/bulk/${batch.publicId}/result.csv`), {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!res.ok) throw new Error('Result download failed');
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `employee-bulk-result-${batch.publicId}.csv`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Result download failed');
    }
  }

  async function simulateConfirm(row: EmployeeRow, success: boolean) {
    if (!token) return;
    setLoading(true);
    setError('');
    try {
      await api('/api/employees/bulk/confirm', {
        method: 'POST',
        token,
        body: JSON.stringify({
          rowPublicId: row.publicId,
          status: success ? 'OPEN' : 'FAILED',
          dfsAccountNo: success ? row.mobile : undefined,
          dfsCustomerId: success ? `CUST-${row.lineNo}` : undefined,
          message: success ? 'Simulated DFS account open' : 'Simulated DFS failure',
        }),
      });
      if (batch?.publicId) {
        setBatch(await api<EmployeeBatch>(`/api/employees/bulk/${batch.publicId}`, { token }));
      }
      setOk(success ? `Confirmed OPEN for ${row.fullName || row.mobile}` : `Marked FAILED for ${row.fullName || row.mobile}`);
      void loadList();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Confirm failed');
    } finally {
      setLoading(false);
    }
  }

  if (!session) {
    return <div className="alert alert-info">Login required.</div>;
  }

  return (
    <div className="animate-in">
      <PageHeader
        eyebrow="Network"
        title="Employee onboarding"
        subtitle="Upload staff CSV → park on DFS → when DFS confirms account creation, the row becomes OPEN. Employees use the consumer app."
      />

      <div className="alert alert-info">
        Save Excel as <strong>CSV</strong> first. Columns: employee_code, full_name, father_name, mobile, cnic,
        date_of_birth, gender, email, department. <strong>Park on DFS</strong> calls live{' '}
        <code>bulkAccounts</code> (mobile + CNIC + name) via the corporate portal key.
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {ok && <div className="alert alert-ok">{ok}</div>}

      <div className="panel panel--wide">
        <form className="form-grid" onSubmit={upload}>
          <h3 className="form-section-title" style={{ marginTop: 0 }}>Upload employee list</h3>
          <div className="actions" style={{ marginTop: 0 }}>
            <button type="button" className="btn btn-ghost btn-sm" onClick={() => void downloadTemplate()}>
              Download CSV template
            </button>
          </div>
          <div className="form-row">
            <label>CSV file</label>
            <input type="file" accept=".csv,.txt" onChange={(e) => setFile(e.target.files?.[0] || null)} />
          </div>
          <div className="actions">
            <button className="btn btn-primary" type="submit" disabled={loading || !file}>
              {loading ? 'Uploading…' : 'Upload'}
            </button>
          </div>
        </form>
      </div>

      {batch && (
        <div className="panel panel--wide" style={{ marginTop: '1.25rem' }}>
          <div className="txn-panel-head">
            <div>
              <h3 style={{ margin: 0 }}>
                Batch <code>{batch.publicId.slice(0, 8)}…</code>{' '}
                <span className={`status status-${statusClass(batch.status)}`}>{batch.status}</span>
              </h3>
              <p className="muted" style={{ marginBottom: 0 }}>
                {batch.fileName || '—'} · {batch.totalRows} rows · parked {batch.parkedRows} · open {batch.openRows} ·
                failed {batch.failedRows}
                {batch.errorMessage ? ` · ${batch.errorMessage}` : ''}
              </p>
            </div>
            <div className="txn-panel-tools">
              {batch.status === 'DRAFT' && (
                <button type="button" className="btn btn-primary btn-sm" disabled={loading} onClick={() => void parkBatch()}>
                  {loading ? 'Parking…' : 'Park on DFS'}
                </button>
              )}
              <button type="button" className="btn btn-ghost btn-sm" onClick={() => void downloadResult()}>
                Result CSV
              </button>
              <button type="button" className="btn btn-ghost btn-sm" onClick={() => void openBatch(batch.publicId)}>
                Refresh
              </button>
            </div>
          </div>

          {batch.rows && batch.rows.length > 0 && (
            <div className="table-wrap" style={{ marginTop: '1rem' }}>
              <table className="data-table">
                <thead>
                  <tr>
                    <th>#</th>
                    <th>Name</th>
                    <th>Mobile</th>
                    <th>CNIC</th>
                    <th>Dept</th>
                    <th>Status</th>
                    <th>Account</th>
                    <th>Message / actions</th>
                  </tr>
                </thead>
                <tbody>
                  {batch.rows.map((r) => (
                    <tr key={r.publicId}>
                      <td>{r.lineNo}</td>
                      <td>
                        {r.fullName || '—'}
                        {r.employeeCode ? <div className="muted" style={{ fontSize: '0.78rem' }}>{r.employeeCode}</div> : null}
                      </td>
                      <td className="txn-mono">{r.mobile || '—'}</td>
                      <td className="txn-mono">{r.cnic || '—'}</td>
                      <td>{r.department || '—'}</td>
                      <td>
                        <span className={`status status-${statusClass(r.status)}`}>{r.status}</span>
                      </td>
                      <td className="txn-mono">{r.dfsAccountNo || r.parkRef || '—'}</td>
                      <td>
                        <div className="muted" style={{ fontSize: '0.8rem', marginBottom: r.status === 'PARKED' ? '0.35rem' : 0 }}>
                          {r.responseMessage || '—'}
                        </div>
                        {r.status === 'PARKED' && (
                          <div className="txn-actions">
                            <button
                              type="button"
                              className="btn btn-primary btn-sm"
                              disabled={loading}
                              onClick={() => void simulateConfirm(r, true)}
                            >
                              Simulate OPEN
                            </button>
                            <button
                              type="button"
                              className="btn btn-ghost btn-sm"
                              disabled={loading}
                              onClick={() => void simulateConfirm(r, false)}
                            >
                              Simulate FAIL
                            </button>
                          </div>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {batches.length > 0 && (
        <div className="panel panel--wide" style={{ marginTop: '1.25rem' }}>
          <h3 style={{ marginTop: 0 }}>Recent batches</h3>
          <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
            {batches.map((b) => (
              <li key={b.publicId} style={{ marginBottom: '0.35rem' }}>
                <button type="button" className="btn btn-ghost btn-sm" onClick={() => void openBatch(b.publicId)}>
                  {b.status} · open {b.openRows}/{b.totalRows} · {b.fileName || b.publicId.slice(0, 8)}
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
