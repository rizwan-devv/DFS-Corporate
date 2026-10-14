import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../lib/api';
import { useBrand } from '../lib/useBrand';

type AckInfo = {
  status: string;
  message?: string;
  brandName?: string;
  businessName?: string;
  trackingId?: string;
  emailMasked?: string;
  expiresAt?: string;
  acknowledgedAt?: string;
  canConfirm: boolean;
};

export function AcknowledgePage() {
  const { token = '' } = useParams();
  const brand = useBrand();
  const [info, setInfo] = useState<AckInfo | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [confirming, setConfirming] = useState(false);
  const [agreed, setAgreed] = useState(false);

  useEffect(() => {
    if (!token) {
      setLoading(false);
      setError('Missing acknowledgment token. Open the link from your email.');
      return;
    }
    let cancelled = false;
    setLoading(true);
    api<AckInfo>(`/api/public/acknowledgment/${encodeURIComponent(token)}`)
      .then((data) => {
        if (!cancelled) setInfo(data);
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Invalid acknowledgment link');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [token]);

  async function confirm() {
    if (!token || !agreed) return;
    setError('');
    setConfirming(true);
    try {
      const data = await api<AckInfo>(`/api/public/acknowledgment/${encodeURIComponent(token)}/confirm`, {
        method: 'POST',
      });
      setInfo(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not record acknowledgment');
    } finally {
      setConfirming(false);
    }
  }

  const title = info?.brandName || brand.name;
  const done = info?.status === 'ACKNOWLEDGED';

  return (
    <div className="page">
      <div className="container">
        <div className="panel panel--auth">
          <div className="badge">ACCOUNT HOLDER CONFIRMATION</div>
          <h1 className="panel-title">{title}</h1>
          <p className="panel-subtitle muted">
            Confirm that you authorised this corporate application before the bank completes approval.
          </p>

          {loading && <p className="muted">Loading acknowledgment details…</p>}
          {error && <div className="alert alert-error">{error}</div>}

          {!loading && info && (
            <>
              {done && <div className="alert alert-ok">{info.message}</div>}
              {!done && info.message && (
                <div className={`alert ${info.canConfirm ? 'alert-info' : 'alert-warn'}`}>
                  {info.message}
                </div>
              )}

              <dl className="ack-summary">
                <div>
                  <dt>Entity</dt>
                  <dd>{info.businessName || '—'}</dd>
                </div>
                <div>
                  <dt>Tracking ID</dt>
                  <dd>{info.trackingId || '—'}</dd>
                </div>
                <div>
                  <dt>Registered email</dt>
                  <dd>{info.emailMasked || '—'}</dd>
                </div>
                {info.expiresAt && info.canConfirm && (
                  <div>
                    <dt>Link expires</dt>
                    <dd>{new Date(info.expiresAt).toLocaleString()}</dd>
                  </div>
                )}
                {info.acknowledgedAt && (
                  <div>
                    <dt>Acknowledged</dt>
                    <dd>{new Date(info.acknowledgedAt).toLocaleString()}</dd>
                  </div>
                )}
              </dl>

              {info.canConfirm && (
                <div className="ack-actions">
                  <label className="ack-agree">
                    <input
                      type="checkbox"
                      checked={agreed}
                      onChange={(e) => setAgreed(e.target.checked)}
                    />
                    <span>
                      I am the registered account holder for this application. I confirm the details are
                      correct and I authorised this corporate account to be opened.
                    </span>
                  </label>
                  <button
                    className="btn btn-primary"
                    type="button"
                    disabled={!agreed || confirming}
                    onClick={() => void confirm()}
                  >
                    {confirming ? 'Recording…' : 'Confirm acknowledgment'}
                  </button>
                </div>
              )}

              {done && (
                <p className="muted">
                  You can close this page. The bank will email you again when the account is approved.
                </p>
              )}
            </>
          )}

          <p className="muted" style={{ marginTop: '1.5rem' }}>
            <Link to="/">Return to {brand.short}</Link>
          </p>
        </div>
      </div>
    </div>
  );
}
