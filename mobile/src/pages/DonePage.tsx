import { Navigate, useNavigate } from 'react-router-dom';
import { useSession } from '../auth/SessionContext';

export function DonePage() {
  const { session, clear } = useSession();
  const navigate = useNavigate();

  if (!session) return <Navigate to="/" replace />;

  return (
    <div className="shell">
      <div className="brand-row animate-in">
        <div className="brand-mark" aria-hidden><span /></div>
        <span className="brand-text">PAYFAST CORPORATE</span>
      </div>

      <div className="card success-card animate-in animate-in-delay-1">
        <div className="success-check" aria-hidden>✓</div>
        <span className="badge">COMPLETE</span>
        <h1>KYC submitted</h1>
        <p>
          Thanks, <strong>{session.fullName}</strong>. Your mobile KYC for{' '}
          <strong>{session.businessName || 'the entity'}</strong> was submitted.
        </p>
        <p className="muted">
          Status: {session.status}
          {session.trackingId ? ` · Tracking ${session.trackingId}` : ''}
        </p>
        <p className="hint">
          Backoffice can approve once all partners finish KYC. You can close this app.
        </p>
        <button
          type="button"
          className="btn btn-ghost"
          onClick={() => {
            clear();
            navigate('/', { replace: true });
          }}
        >
          Sign out
        </button>
      </div>
    </div>
  );
}
