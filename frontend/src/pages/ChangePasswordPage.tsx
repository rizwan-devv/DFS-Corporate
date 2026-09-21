import { type FormEvent, useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { api } from '../lib/api';
import { useAuth } from '../auth/AuthContext';

export function ChangePasswordPage() {
  const { session, setSession } = useAuth();
  const navigate = useNavigate();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  if (!session) return <Navigate to="/login" replace />;
  if (session.role === 'PLATFORM_ADMIN') return <Navigate to="/admin" replace />;
  if (!session.firstLogin) {
    return <Navigate to={session.partyStatus === 'ACTIVE' ? '/dashboard' : '/onboarding'} replace />;
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    if (newPassword.length < 8) {
      setError('New password must be at least 8 characters');
      return;
    }
    if (newPassword !== confirm) {
      setError('New password and confirmation do not match');
      return;
    }
    setLoading(true);
    try {
      await api('/api/auth/change-password', {
        method: 'POST',
        token: session!.token,
        body: JSON.stringify({ currentPassword, newPassword }),
      });
      setSession({ ...session!, firstLogin: false });
      const dest =
        session!.partyStatus === 'ACTIVE'
          ? '/dashboard'
          : session!.partyStatus === 'DRAFT' || session!.partyStatus === 'REJECTED'
            ? '/onboarding'
            : '/dashboard';
      navigate(dest);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Password change failed');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="page">
      <div className="container">
        <div className="panel panel--auth">
          <div className="badge">SECURITY</div>
          <h2 className="panel-title">Change password</h2>
          <p className="muted">
            You signed in with a temporary password from your application submit email.
            Set a new password to continue (document uploads, status tracking, etc.).
          </p>
          {error && <div className="alert alert-error">{error}</div>}
          <form className="form-grid" onSubmit={onSubmit}>
            <div className="form-row">
              <label>Current (temporary) password</label>
              <input
                required
                type="password"
                value={currentPassword}
                onChange={(e) => setCurrentPassword(e.target.value)}
                autoComplete="current-password"
              />
            </div>
            <div className="form-row">
              <label>New password</label>
              <input
                required
                type="password"
                minLength={8}
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                autoComplete="new-password"
              />
            </div>
            <div className="form-row">
              <label>Confirm new password</label>
              <input
                required
                type="password"
                minLength={8}
                value={confirm}
                onChange={(e) => setConfirm(e.target.value)}
                autoComplete="new-password"
              />
            </div>
            <button className="btn btn-primary" disabled={loading} type="submit">
              {loading ? 'Saving…' : 'Save password'}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}
