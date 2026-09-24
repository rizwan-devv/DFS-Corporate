import { type FormEvent, useEffect, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { api } from '../lib/api';
import { useAuth } from '../auth/AuthContext';

const ALL_ROLES = ['PARTY_ADMIN', 'MAKER', 'CHECKER', 'APPROVER', 'RELEASER'] as const;

type PortalUser = {
  accountId: number;
  email: string;
  status?: string;
  roles: string[];
  temporaryPassword?: string;
};

export function PortalUsersPage() {
  const { session } = useAuth();
  const [users, setUsers] = useState<PortalUser[]>([]);
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');
  const [busy, setBusy] = useState(false);
  const [form, setForm] = useState({
    email: '',
    roles: ['MAKER'] as string[],
  });

  const canAdmin = session?.role === 'PLATFORM_ADMIN'
    || (session?.portalRoles || []).includes('PARTY_ADMIN');

  async function load() {
    if (!session?.token) return;
    try {
      const data = await api<PortalUser[]>('/api/portal/users', { token: session.token });
      setUsers(data);
      setError('');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load users');
    }
  }

  useEffect(() => {
    load();
  }, [session?.token]);

  if (!session) return <Navigate to="/login" replace />;
  if (session.role === 'PLATFORM_ADMIN') return <Navigate to="/admin" replace />;
  if (session.partyType !== 'MERCHANT' || session.partyStatus !== 'ACTIVE') {
    return <Navigate to="/dashboard" replace />;
  }

  function toggleRole(role: string) {
    setForm((f) => ({
      ...f,
      roles: f.roles.includes(role) ? f.roles.filter((r) => r !== role) : [...f.roles, role],
    }));
  }

  async function onCreate(e: FormEvent) {
    e.preventDefault();
    if (!session?.token || !canAdmin) return;
    setBusy(true);
    setOk('');
    setError('');
    try {
      const res = await api<PortalUser>('/api/portal/users', {
        method: 'POST',
        token: session.token,
        body: JSON.stringify(form),
      });
      setOk(`User created. Temp password: ${res.temporaryPassword || '(emailed)'}`);
      setForm({ email: '', roles: ['MAKER'] });
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Create failed');
    } finally {
      setBusy(false);
    }
  }

  async function saveRoles(accountId: number, roles: string[]) {
    if (!session?.token || !canAdmin) return;
    setBusy(true);
    try {
      await api(`/api/portal/users/${accountId}/roles`, {
        method: 'PUT',
        token: session.token,
        body: JSON.stringify({ roles }),
      });
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Update failed');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="page">
      <div className="container dash">
        <div className="panel panel--wide">
          <div className="badge">PORTAL ROLES</div>
          <h2 style={{ marginTop: 0 }}>Corporate users (Maker / Checker / Approver / Releaser)</h2>
          <p className="muted">
            One person can hold multiple roles. If Checker also has Approver, after Check the workflow
            skips Approver and goes to Releaser. Partnership / LLP partners each get their own login
            (same corporate, separate email).
          </p>
          <p className="muted">Your roles: {(session.portalRoles || []).join(', ') || '—'}</p>
          {error && <div className="alert alert-error">{error}</div>}
          {ok && <div className="alert alert-info">{ok}</div>}

          {canAdmin && (
            <form className="form-grid" onSubmit={onCreate} style={{ marginBottom: '1.5rem' }}>
              <div className="form-row">
                <label>Email</label>
                <input required type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
              </div>
              <div className="form-row">
                <label>Roles</label>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.75rem' }}>
                  {ALL_ROLES.map((r) => (
                    <label key={r} style={{ display: 'flex', gap: '0.35rem', alignItems: 'center' }}>
                      <input type="checkbox" checked={form.roles.includes(r)} onChange={() => toggleRole(r)} />
                      {r}
                    </label>
                  ))}
                </div>
              </div>
              <button className="btn btn-primary" disabled={busy || form.roles.length === 0} type="submit">
                Add user
              </button>
            </form>
          )}

          <div className="dash-kyc-list">
            {users.map((u) => (
              <div className="doc-row" key={u.accountId} style={{ flexWrap: 'wrap', gap: '0.75rem' }}>
                <div style={{ flex: '1 1 180px' }}>
                  <strong>{u.email}</strong>
                  <div className="muted">{u.status}</div>
                </div>
                {canAdmin ? (
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.5rem', alignItems: 'center' }}>
                    {ALL_ROLES.map((r) => (
                      <label key={r} style={{ fontSize: '0.85rem' }}>
                        <input
                          type="checkbox"
                          checked={u.roles.includes(r)}
                          onChange={() => {
                            const next = u.roles.includes(r)
                              ? u.roles.filter((x) => x !== r)
                              : [...u.roles, r];
                            if (next.length) saveRoles(u.accountId, next);
                          }}
                        />{' '}
                        {r}
                      </label>
                    ))}
                  </div>
                ) : (
                  <span className="muted">{u.roles.join(', ')}</span>
                )}
              </div>
            ))}
          </div>

          <div className="actions" style={{ marginTop: '1.25rem' }}>
            <Link className="btn btn-ghost" to="/dashboard">Dashboard</Link>
            <Link className="btn btn-ghost" to="/approvals">Approvals</Link>
          </div>
        </div>
      </div>
    </div>
  );
}
