import { useCallback, useEffect, useState } from 'react';
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom';
import { PageHeader } from '../components/PageHeader';
import { api } from '../lib/api';
import { useAuth } from '../auth/AuthContext';

type AppUser = {
  id: number;
  fullName: string;
  phone: string;
  status: string;
  signatureUploaded?: boolean;
  bankVisitRequired?: boolean;
};

type Party = {
  partnerAppUsers?: AppUser[];
};

export function SignaturePage() {
  const { session } = useAuth();
  const { appUserId } = useParams();
  const navigate = useNavigate();
  const id = Number(appUserId);

  const [partner, setPartner] = useState<AppUser | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');

  const load = useCallback(async () => {
    if (!session?.token || !Number.isFinite(id)) return;
    setLoading(true);
    setError('');
    try {
      const party = await api<Party>('/api/onboarding/me', { token: session.token });
      const found = (party.partnerAppUsers || []).find((u) => u.id === id) ?? null;
      setPartner(found);
      if (!found) setError('Partner not found on this application.');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load');
    } finally {
      setLoading(false);
    }
  }, [session?.token, id]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!file) {
      setPreviewUrl(null);
      return;
    }
    const url = URL.createObjectURL(file);
    setPreviewUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  if (!session) return <Navigate to="/login" replace />;
  if (session.role === 'PLATFORM_ADMIN') return <Navigate to="/admin" replace />;

  async function upload() {
    if (!session?.token || !file || !partner) return;
    setError('');
    setOk('');
    setUploading(true);
    try {
      const fd = new FormData();
      fd.append('signature', file);
      const updated = await api<AppUser>(`/api/onboarding/partner-app-users/${partner.id}/signature`, {
        method: 'POST',
        token: session.token,
        body: fd,
      });
      setPartner(updated);
      setOk('Signature saved. A printable 4-up PDF / PNG / JPEG was generated for backoffice.');
      setFile(null);
      setTimeout(() => navigate('/dashboard'), 1200);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Upload failed');
    } finally {
      setUploading(false);
    }
  }

  const blocked = !!partner?.bankVisitRequired;

  return (
    <div className="page">
      <PageHeader
        eyebrow="Partner KYC"
        title="Partner signature"
        subtitle="Upload a photo of the handwritten signature. The portal stores one image; the backend places it in four slots and builds printable PDF, PNG, and JPEG sheets."
      />

      {error && <div className="alert alert-error">{error}</div>}
      {ok && <div className="alert alert-ok">{ok}</div>}

      {loading ? (
        <p className="muted">Loading…</p>
      ) : !partner ? (
        <div className="glass-panel">
          <p className="muted">No partner matched this link.</p>
          <Link className="btn btn-ghost" to="/dashboard">Back to dashboard</Link>
        </div>
      ) : (
        <section className="glass-panel animate-in" style={{ maxWidth: 560 }}>
          <div className="dash-kyc-head" style={{ marginBottom: '1rem' }}>
            <div>
              <h2 className="panel-title" style={{ margin: 0 }}>{partner.fullName}</h2>
              <p className="muted" style={{ margin: '0.25rem 0 0' }}>
                {partner.phone} · {partner.status}
                {partner.signatureUploaded ? ' · signature on file' : ' · signature missing'}
              </p>
            </div>
          </div>

          {blocked ? (
            <div className="alert alert-info">
              Bank / office visit required — signature upload is closed for this partner.
            </div>
          ) : (
            <>
              <label className="field" style={{ display: 'block', marginBottom: '1rem' }}>
                <span className="muted" style={{ display: 'block', marginBottom: '0.35rem' }}>
                  Signature image (JPEG or PNG)
                </span>
                <input
                  type="file"
                  accept="image/jpeg,image/png,image/webp,image/*"
                  capture="environment"
                  onChange={(e) => setFile(e.target.files?.[0] ?? null)}
                />
              </label>

              {previewUrl && (
                <div className="sig-preview" style={{ marginBottom: '1rem' }}>
                  <img
                    src={previewUrl}
                    alt="Signature preview"
                    style={{
                      maxWidth: '100%',
                      maxHeight: 220,
                      objectFit: 'contain',
                      background: 'var(--surface-2, #f4f4f5)',
                      borderRadius: 8,
                      display: 'block',
                    }}
                  />
                </div>
              )}

              <div className="actions">
                <button
                  className="btn btn-primary"
                  type="button"
                  disabled={!file || uploading}
                  onClick={() => void upload()}
                >
                  {uploading ? 'Uploading…' : partner.signatureUploaded ? 'Replace signature' : 'Save signature'}
                </button>
                <Link className="btn btn-ghost" to="/dashboard">Cancel</Link>
              </div>
            </>
          )}
        </section>
      )}
    </div>
  );
}
