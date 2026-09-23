import { type FormEvent, useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import QRCode from 'qrcode';
import { PageHeader } from '../../components/PageHeader';
import { BeneficiaryPicker } from '../../components/BeneficiaryPicker';
import { api } from '../../lib/api';
import { useAuth } from '../../auth/AuthContext';
import type { Beneficiary, MockTransfer } from '../../lib/transferTypes';
import { fetchLiveStatus, type LiveStatus } from '../../lib/liveTransfers';

const emptyForm = {
  accountNumber: '',
  amount: '',
  cnic: '',
  mobile: '',
  beneficiaryName: '',
  notes: '',
  raastId: '',
};

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
  const [form, setForm] = useState(emptyForm);
  const [selectedBenId, setSelectedBenId] = useState<string | null>(null);
  const [history, setHistory] = useState<MockTransfer[]>([]);
  const [last, setLast] = useState<MockTransfer | null>(null);
  const [live, setLive] = useState<LiveStatus | null>(null);
  const [account, setAccount] = useState<RaastAccountDetails | null>(null);
  const [qrDataUrl, setQrDataUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [qrLoading, setQrLoading] = useState(false);
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');

  const liveOn = !!live?.liveEnabled || !!live?.appConfigured || !!live?.raastLive;

  const load = useCallback(async () => {
    if (!session?.token) return;
    try {
      const list = await api<MockTransfer[]>('/api/transfers/mock?productType=RAAST', {
        token: session.token,
      });
      setHistory(list);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load history');
    }
  }, [session?.token]);

  const loadLiveQr = useCallback(async () => {
    if (!session?.token) return;
    setQrLoading(true);
    setError('');
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
    void load();
    void loadLiveQr();
  }, [load, loadLiveQr]);

  function applyBeneficiary(b: Beneficiary | null) {
    setSelectedBenId(b?.publicId ?? null);
    if (!b) return;
    setForm((f) => ({
      ...f,
      raastId: b.raastId || '',
      accountNumber: b.accountNumber || b.raastId || '',
      mobile: b.mobile || '',
      cnic: b.cnic || '',
      beneficiaryName: b.fullName || '',
    }));
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
      const accountNo = form.raastId.trim() || form.accountNumber.trim();
      if (!accountNo) throw new Error('Raast ID or IBAN / account is required');

      const res = await api<MockTransfer>('/api/transfers/mock/single', {
        method: 'POST',
        token: session.token,
        body: JSON.stringify({
          productType: 'RAAST',
          accountNumber: accountNo,
          amount,
          cnic: form.cnic || undefined,
          mobile: form.mobile || undefined,
          beneficiaryName: form.beneficiaryName || undefined,
          notes: form.notes || undefined,
        }),
      });
      setLast(res);
      setOk(`Mock Raast success — ${res.mockTxnRef}`);
      setForm(emptyForm);
      setSelectedBenId(null);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Raast payment failed');
    } finally {
      setLoading(false);
    }
  }

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
        subtitle={
          qrDataUrl
            ? 'Live receive QR from DFS accountDetails · outbound pay still mock'
            : 'Load live receive QR when portal key is enabled · outbound pay is mock'
        }
        actions={
          <div className="actions" style={{ marginTop: 0 }}>
            <Link className="btn btn-ghost btn-sm" to="/beneficiaries">Beneficiaries</Link>
            <button
              type="button"
              className="btn btn-ghost btn-sm"
              disabled={qrLoading}
              onClick={() => { void load(); void loadLiveQr(); }}
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

      <div className="panel panel--wide" style={{ marginTop: '1.25rem' }}>
        <form className="form-grid" onSubmit={submitSingle}>
          <h3 className="form-section-title">Raast — send (mock)</h3>
          <p className="muted" style={{ margin: 0 }}>Outbound Raast payment is still mock until DFS provides a pay API.</p>
          <BeneficiaryPicker
            product="RAAST"
            selectedPublicId={selectedBenId}
            onSelect={applyBeneficiary}
          />
          <div className="form-row">
            <label>Raast ID / IBAN</label>
            <input
              required
              value={form.raastId || form.accountNumber}
              onChange={(e) => setForm({ ...form, raastId: e.target.value, accountNumber: e.target.value })}
              placeholder="PK00… or Raast alias"
            />
          </div>
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
          <div className="form-row">
            <label>Beneficiary name</label>
            <input
              value={form.beneficiaryName}
              onChange={(e) => setForm({ ...form, beneficiaryName: e.target.value })}
            />
          </div>
          <div className="form-row">
            <label>CNIC</label>
            <input value={form.cnic} onChange={(e) => setForm({ ...form, cnic: e.target.value })} />
          </div>
          <div className="form-row">
            <label>Mobile</label>
            <input value={form.mobile} onChange={(e) => setForm({ ...form, mobile: e.target.value })} />
          </div>
          <div className="form-row">
            <label>Notes</label>
            <input value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} />
          </div>
          <div className="actions">
            <button className="btn btn-primary" type="submit" disabled={loading}>
              {loading ? 'Submitting…' : 'Submit mock Raast'}
            </button>
          </div>
        </form>
      </div>

      {last && (
        <div className="panel panel--wide" style={{ marginTop: '1.25rem' }}>
          <h3>Last mock result</h3>
          <p>
            <span className="status status-ACTIVE">{last.status}</span>{' '}
            <strong>{last.mockTxnRef}</strong>
          </p>
          {last.raastQrDataUrl && (
            <div className="transfer-qr">
              <img src={last.raastQrDataUrl} alt="Mock Raast QR" width={200} height={200} />
              <p className="muted">Mock Raast QR. Payload: {last.raastQrPayload}</p>
            </div>
          )}
        </div>
      )}

      <div className="panel panel--wide" style={{ marginTop: '1.25rem' }}>
        <h3>Raast history</h3>
        {history.length === 0 && <p className="muted">No Raast payments yet.</p>}
        {history.map((t) => (
          <div className="doc-row" key={t.id}>
            <div>
              <strong>{t.mockTxnRef}</strong>
              <div className="muted">
                {t.amount != null ? `PKR ${t.amount}` : '—'}
                {t.accountNumber ? ` · ${t.accountNumber}` : ''}
                {t.beneficiaryName ? ` · ${t.beneficiaryName}` : ''}
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
