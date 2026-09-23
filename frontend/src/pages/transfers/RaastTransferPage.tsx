import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import QRCode from 'qrcode';
import { PageHeader } from '../../components/PageHeader';
import { api } from '../../lib/api';
import { useAuth } from '../../auth/AuthContext';
import { fetchLiveStatus, type LiveStatus } from '../../lib/liveTransfers';

type RaastAccountDetails = {
  responsecode?: string;
  messages?: string;
  accountNo?: string;
  mobileNo?: string;
  nidNo?: string;
  iban?: string;
  qrCode?: string;
  accountTitle?: string;
  currentBalance?: number;
  accountStatusDescr?: string;
};

export function RaastTransferPage() {
  const { session } = useAuth();
  const [live, setLive] = useState<LiveStatus | null>(null);
  const [account, setAccount] = useState<RaastAccountDetails | null>(null);
  const [qrDataUrl, setQrDataUrl] = useState<string | null>(null);
  const [qrLoading, setQrLoading] = useState(false);
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');

  const liveOn = !!live?.liveEnabled || !!live?.appConfigured || !!live?.raastLive;

  const loadLiveQr = useCallback(async () => {
    if (!session?.token) return;
    setQrLoading(true);
    setError('');
    setOk('');
    try {
      const status = await fetchLiveStatus(session.token);
      setLive(status);
      if (!status.appConfigured && !status.liveEnabled && !status.raastLive) {
        setAccount(null);
        setQrDataUrl(null);
        return;
      }
      const details = await api<RaastAccountDetails>('/api/transfers/live/raast/account-details', {
        token: session.token,
      });
      setAccount(details);
      if (details.qrCode) {
        const url = await QRCode.toDataURL(details.qrCode, {
          width: 240,
          margin: 2,
          color: { dark: '#0f172a', light: '#ffffff' },
          errorCorrectionLevel: 'M',
        });
        setQrDataUrl(url);
        setOk('Live Raast QR loaded from DFS accountDetails');
      } else {
        setQrDataUrl(null);
        setError('DFS returned account details without qrCode');
      }
    } catch (e) {
      setAccount(null);
      setQrDataUrl(null);
      setError(e instanceof Error ? e.message : 'Failed to load Raast QR');
    } finally {
      setQrLoading(false);
    }
  }, [session?.token]);

  useEffect(() => {
    void loadLiveQr();
  }, [loadLiveQr]);

  if (!session) {
    return (
      <div className="portal-page">
        <PageHeader eyebrow="Raast" title="Login required" subtitle="Sign in to continue." />
        <Link className="btn btn-primary" to="/login">Login</Link>
      </div>
    );
  }

  return (
    <div className="portal-page raast-page">
      <PageHeader
        eyebrow="Transfers"
        title="Raast"
        subtitle="Receive payments via your live Raast QR from DFS accountDetails"
        actions={
          <div className="actions" style={{ marginTop: 0 }}>
            <button
              type="button"
              className="btn btn-ghost btn-sm"
              disabled={qrLoading}
              onClick={() => void loadLiveQr()}
            >
              {qrLoading ? 'Loading…' : 'Refresh'}
            </button>
          </div>
        }
      />

      {error && <div className="alert alert-error">{error}</div>}
      {ok && <div className="alert alert-ok">{ok}</div>}

      <div className="panel panel--wide raast-receive-panel">
        <div className="raast-receive-head">
          <div>
            <h3 className="form-section-title" style={{ marginTop: 0 }}>My Raast QR</h3>
            <p className="muted" style={{ margin: 0 }}>
              Others can scan this to pay your corporate wallet. Source: DFS <code>accountDetails</code>.
            </p>
          </div>
          <button type="button" className="btn btn-primary btn-sm" disabled={qrLoading} onClick={() => void loadLiveQr()}>
            {qrLoading ? 'Fetching…' : 'Reload QR'}
          </button>
        </div>

        {!liveOn && !qrLoading && (
          <div className="alert alert-info">
            Enable live portal key (<code>DFS_PORTAL_API_ENABLED</code> + <code>CORPORATE_PORTAL_API_KEY</code>) to load QR.
          </div>
        )}

        {qrDataUrl && account && (
          <div className="raast-qr-card">
            <div className="raast-qr-frame">
              <img src={qrDataUrl} alt="Raast QR code" width={240} height={240} />
            </div>
            <div className="raast-qr-meta">
              <p className="raast-qr-title">{account.accountTitle || 'Corporate account'}</p>
              {account.accountStatusDescr && (
                <span className={`status ${account.accountStatusDescr === 'ACTIVE' ? 'status-ACTIVE' : 'status-PENDING'}`}>
                  {account.accountStatusDescr}
                </span>
              )}
              <dl className="raast-qr-dl">
                {account.iban && (
                  <>
                    <dt>IBAN</dt>
                    <dd className="txn-mono">{account.iban}</dd>
                  </>
                )}
                {(account.mobileNo || account.accountNo) && (
                  <>
                    <dt>Wallet</dt>
                    <dd className="txn-mono">{account.mobileNo || account.accountNo}</dd>
                  </>
                )}
                {account.currentBalance != null && (
                  <>
                    <dt>Balance</dt>
                    <dd>PKR {Number(account.currentBalance).toLocaleString()}</dd>
                  </>
                )}
              </dl>
              <p className="muted raast-qr-hint">Scan with a Raast-enabled banking app</p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
