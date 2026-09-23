import { useEffect, useState, type FormEvent } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { useSession } from '../auth/SessionContext';
import {
  me,
  saveProfile,
  stubBiometric,
  stubVideo,
  submitKyc,
  uploadDoc,
  type AppKycSession,
} from '../lib/api';

const STEP_LABELS = ['ID / OCR', 'Capture', 'Video / Bio', 'Submit'];

export function FlowPage() {
  const { session, setSession, clear } = useSession();
  const navigate = useNavigate();
  const [step, setStep] = useState(1);
  const [animKey, setAnimKey] = useState(0);
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');
  const [loading, setLoading] = useState(false);
  const [cnicNumber, setCnicNumber] = useState('');
  const [cnicFullName, setCnicFullName] = useState('');
  const [dateOfBirth, setDateOfBirth] = useState('');

  useEffect(() => {
    if (!session?.sessionToken) return;
    setCnicNumber(session.cnicNumber || '');
    setCnicFullName(session.cnicFullName || session.fullName || '');
    setDateOfBirth(session.dateOfBirth || '');
  }, [session]);

  useEffect(() => {
    if (!session?.sessionToken) return;
    me(session.sessionToken)
      .then(setSession)
      .catch(() => clear());
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  if (!session?.sessionToken) return <Navigate to="/" replace />;
  if (session.status === 'KYC_COMPLETED') return <Navigate to="/done" replace />;

  function goStep(n: number) {
    setStep(n);
    setAnimKey((k) => k + 1);
  }

  async function refresh(next: AppKycSession) {
    setSession(next);
  }

  async function saveOcr(e: FormEvent) {
    e.preventDefault();
    setError(''); setOk(''); setLoading(true);
    try {
      const next = await saveProfile(session!.sessionToken, {
        cnicNumber,
        cnicFullName,
        dateOfBirth: dateOfBirth || null,
      });
      await refresh(next);
      setOk('CNIC details saved');
      goStep(2);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Save failed');
    } finally {
      setLoading(false);
    }
  }

  function simulateOcr() {
    setCnicFullName(session?.fullName || 'Partner Name');
    setCnicNumber('35202-1234567-1');
    setDateOfBirth('1990-01-15');
    setOk('OCR stub filled fields — edit if needed');
  }

  async function onFile(kind: string, file: File | undefined) {
    if (!file) return;
    setError(''); setOk(''); setLoading(true);
    try {
      const next = await uploadDoc(session!.sessionToken, kind, file);
      await refresh(next);
      setOk(`${kind} uploaded`);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Upload failed');
    } finally {
      setLoading(false);
    }
  }

  async function doVideo() {
    setError(''); setOk(''); setLoading(true);
    try {
      await refresh(await stubVideo(session!.sessionToken));
      setOk('Video KYC stub completed');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed');
    } finally {
      setLoading(false);
    }
  }

  async function doBio() {
    setError(''); setOk(''); setLoading(true);
    try {
      await refresh(await stubBiometric(session!.sessionToken));
      setOk('NADRA biometric stub recorded (ref only — no finger images stored)');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed');
    } finally {
      setLoading(false);
    }
  }

  async function doSubmit() {
    setError(''); setOk(''); setLoading(true);
    try {
      const next = await submitKyc(session!.sessionToken);
      setSession(next);
      navigate('/done', { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Submit failed');
    } finally {
      setLoading(false);
    }
  }

  const docs = session.requiredDocuments || [];
  const progressPct = (step / STEP_LABELS.length) * 100;

  return (
    <div className="shell">
      <div className="brand-row animate-in">
        <div className="brand-mark" aria-hidden><span /></div>
        <span className="brand-text">DFS CORPORATE</span>
      </div>

      <header className="top animate-in animate-in-delay-1">
        <div>
          <span className="badge">MOBILE KYC</span>
          <h2>{session.fullName}</h2>
          <p className="muted" style={{ margin: 0 }}>
            {session.businessName || 'Entity'} · {session.phone}
          </p>
          <span className="status-pill" style={{ marginTop: '0.5rem' }}>{session.status}</span>
        </div>
        <button
          type="button"
          className="btn btn-ghost"
          onClick={() => { clear(); navigate('/'); }}
        >
          Sign out
        </button>
      </header>

      <div className="progress-track animate-in animate-in-delay-2" aria-hidden>
        <div className="progress-fill" style={{ width: `${progressPct}%` }} />
      </div>

      <div className="steps animate-in animate-in-delay-2">
        {STEP_LABELS.map((label, i) => {
          const n = i + 1;
          const cls = n === step ? 'active' : n < step ? 'done' : '';
          return (
            <button
              key={label}
              type="button"
              className={`chip ${cls}`}
              onClick={() => goStep(n)}
            >
              {n}. {label}
            </button>
          );
        })}
      </div>

      {error && <div className="alert error">{error}</div>}
      {ok && <div className="alert ok">{ok}</div>}

      {step === 1 && (
        <form key={`s-${animKey}`} className="card step-panel" onSubmit={saveOcr}>
          <h3>CNIC details</h3>
          <p className="hint" style={{ marginTop: 0 }}>
            OCR will run on-device later; for MVP use stub or type manually.
          </p>
          <button type="button" className="btn btn-ghost" onClick={simulateOcr}>
            Simulate OCR auto-fill
          </button>
          <label>
            Full name (as on CNIC)
            <input required value={cnicFullName} onChange={(e) => setCnicFullName(e.target.value)} />
          </label>
          <label>
            CNIC number
            <input
              required
              value={cnicNumber}
              onChange={(e) => setCnicNumber(e.target.value)}
              placeholder="XXXXX-XXXXXXX-X"
            />
          </label>
          <label>
            Date of birth
            <input required type="date" value={dateOfBirth} onChange={(e) => setDateOfBirth(e.target.value)} />
          </label>
          <button className="btn btn-primary" type="submit" disabled={loading}>
            Save & continue
          </button>
        </form>
      )}

      {step === 2 && (
        <div key={`s-${animKey}`} className="card step-panel">
          <h3>ID capture & selfie</h3>
          {docs.map((d) => (
            <div className="doc" key={d.kind}>
              <div>
                <strong>{d.label}</strong>
                <span className="muted">{d.uploaded ? 'Uploaded' : 'Required'}</span>
              </div>
              <input
                type="file"
                accept="image/*,.pdf"
                capture={d.kind === 'SELFIE' ? 'user' : 'environment'}
                onChange={(e) => void onFile(d.kind, e.target.files?.[0])}
              />
            </div>
          ))}
          <button className="btn btn-primary" type="button" onClick={() => goStep(3)}>
            Continue
          </button>
        </div>
      )}

      {step === 3 && (
        <div key={`s-${animKey}`} className="card step-panel">
          <h3>Video KYC &amp; NADRA biometric</h3>
          <p className="hint" style={{ marginTop: 0 }}>
            Fingerprints are verified via NADRA — DFS does not store finger images. Stubs until NADRA is wired.
          </p>
          <div className="doc">
            <div>
              <strong>Video KYC</strong>
              <span className="muted">{session.videoKycRef || 'Not done'}</span>
            </div>
            <button className="btn btn-ghost" type="button" disabled={loading} onClick={() => void doVideo()}>
              Run video stub
            </button>
          </div>
          <div className="doc">
            <div>
              <strong>NADRA biometric</strong>
              <span className="muted">{session.biometricRef || 'Not done'}</span>
            </div>
            <button className="btn btn-ghost" type="button" disabled={loading} onClick={() => void doBio()}>
              Run NADRA stub
            </button>
          </div>
          <button className="btn btn-primary" type="button" onClick={() => goStep(4)}>
            Continue
          </button>
        </div>
      )}

      {step === 4 && (
        <div key={`s-${animKey}`} className="card step-panel">
          <h3>Review & submit</h3>
          <ul className="summary">
            <li>Name: {session.cnicFullName || '—'}</li>
            <li>CNIC: {session.cnicNumber || '—'}</li>
            <li>DOB: {session.dateOfBirth || '—'}</li>
            <li>Docs: {docs.filter((d) => d.uploaded).length}/{docs.length}</li>
            <li>Video: {session.videoKycRef ? 'Done' : 'Missing'}</li>
            <li>NADRA: {session.biometricRef ? 'Done' : 'Pending'}</li>
          </ul>
          <button
            className="btn btn-primary"
            type="button"
            disabled={loading || !session.canSubmit}
            onClick={() => void doSubmit()}
          >
            {loading ? 'Submitting…' : 'Submit KYC'}
          </button>
          {!session.canSubmit && (
            <p className="hint">Complete all steps above before submit is enabled.</p>
          )}
        </div>
      )}
    </div>
  );
}
