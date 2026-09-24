import { type FormEvent, useCallback, useEffect, useState } from 'react';
import { Link, Navigate, useLocation } from 'react-router-dom';
import { api } from '../lib/api';
import { useAuth } from '../auth/AuthContext';

type Option = { code: string; label: string };
type RequiredDoc = { documentCode: string; documentLabel: string; mandatory: boolean; uploaded: boolean };
type Assoc = {
  id: number;
  roleType: string;
  fullName: string;
  authorizedToOperate?: boolean;
  ownershipPercent?: number;
  phone?: string;
  email?: string;
};
type AppUser = {
  id: number;
  phone: string;
  email?: string;
  fullName: string;
  status: string;
  appInviteUrl?: string;
  bankVisitRequired?: boolean;
  failureReason?: string;
  signatureUploaded?: boolean;
};
type PartyDoc = {
  id: number;
  documentCode: string;
  originalName: string;
  status: string;
  reviewNote?: string;
};

type Party = {
  status: string;
  partyType: string;
  trackingId?: string;
  canSubmit: boolean;
  requiredDocuments: RequiredDoc[];
  documents?: PartyDoc[];
  associatedPersons?: Assoc[];
  partnerAppUsers?: AppUser[];
  partnerKycTotal?: number;
  partnerKycCompleted?: number;
  rejectionReason?: string;
  discrepancyNote?: string;
  decisionDueAt?: string;
  draftExpiresAt?: string;
  businessName?: string;
  entityType?: string;
  partnershipUnregistered?: boolean;
  applicantIsPartner?: boolean;
  fullName?: string;
  incorporationNumber?: string;
  incorporationDate?: string;
  incorporationCountry?: string;
  incorporationAuthority?: string;
  ntnNumber?: string;
  taxCountry?: string;
  fatcaCrsDeclared?: boolean;
  fatcaCrsDetails?: string;
  registeredAddress?: string;
  mailingAddress?: string;
  placeOfBusiness?: string;
  addressDifferenceReason?: string;
  city?: string;
  country?: string;
  phone?: string;
  natureOfBusiness?: string;
  businessLicenseDetails?: string;
  purposeOfAccount?: string;
  intendedRelationship?: string;
  termsAccepted?: boolean;
  eddRequired?: boolean;
  eddNotes?: string;
  videoKycRef?: string;
  sanctionsStatus?: string;
  identityVerificationStatus?: string;
  onboardingStep?: number;
};

function needsPartnerRoster(entityType?: string) {
  return entityType === 'PARTNERSHIP' || entityType === 'LLP';
}

function isOwnerEntity(entityType?: string) {
  return !needsPartnerRoster(entityType);
}

export function OnboardingPage() {
  const { session } = useAuth();
  const location = useLocation();
  const [party, setParty] = useState<Party | null>(null);
  const [entityTypes, setEntityTypes] = useState<Option[]>([]);
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');
  const [loading, setLoading] = useState(false);
  const [step, setStep] = useState(1);
  const [entity, setEntity] = useState({
    fullName: '', businessName: '', entityType: 'SOLE_PROPRIETORSHIP',
    partnershipUnregistered: false, applicantIsPartner: true,
    incorporationNumber: '', incorporationDate: '', incorporationCountry: 'Pakistan',
    incorporationAuthority: 'SECP', ntnNumber: '', taxCountry: 'Pakistan',
    fatcaCrsDeclared: false, fatcaCrsDetails: '',
    registeredAddress: '', mailingAddress: '', placeOfBusiness: '',
    addressDifferenceReason: '', city: '', country: 'Pakistan', phone: '',
    natureOfBusiness: '', businessLicenseDetails: '', purposeOfAccount: '',
    intendedRelationship: '', termsAccepted: false, onboardingStep: 2,
    geoLocation: '', eddRequired: false, eddNotes: '', videoKycRef: '',
  });
  const [roster, setRoster] = useState({
    fullName: '', email: '', phone: '',
  });

  const load = useCallback(async () => {
    if (!session?.token) return;
    const data = await api<Party>('/api/onboarding/me', { token: session.token });
    setParty(data);
    setStep(data.onboardingStep || 1);
    setEntity((e) => ({
      ...e,
      fullName: data.fullName || e.fullName,
      businessName: data.businessName || '',
      entityType: data.entityType || 'SOLE_PROPRIETORSHIP',
      partnershipUnregistered: !!data.partnershipUnregistered,
      applicantIsPartner: !!data.applicantIsPartner,
      incorporationNumber: data.incorporationNumber || '',
      incorporationDate: data.incorporationDate || '',
      incorporationCountry: data.incorporationCountry || 'Pakistan',
      incorporationAuthority: data.incorporationAuthority || 'SECP',
      ntnNumber: data.ntnNumber || '',
      taxCountry: data.taxCountry || 'Pakistan',
      fatcaCrsDeclared: !!data.fatcaCrsDeclared,
      fatcaCrsDetails: data.fatcaCrsDetails || '',
      registeredAddress: data.registeredAddress || '',
      mailingAddress: data.mailingAddress || '',
      placeOfBusiness: data.placeOfBusiness || '',
      addressDifferenceReason: data.addressDifferenceReason || '',
      city: data.city || '',
      country: data.country || 'Pakistan',
      phone: data.phone || '',
      natureOfBusiness: data.natureOfBusiness || '',
      businessLicenseDetails: data.businessLicenseDetails || '',
      purposeOfAccount: data.purposeOfAccount || '',
      intendedRelationship: data.intendedRelationship || '',
      termsAccepted: !!data.termsAccepted,
      eddRequired: !!data.eddRequired,
      eddNotes: data.eddNotes || '',
      videoKycRef: data.videoKycRef || '',
    }));
  }, [session?.token]);

  useEffect(() => {
    api<Option[]>('/api/entity-types').then(setEntityTypes).catch(() => undefined);
    if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (pos) => setEntity((e) => ({ ...e, geoLocation: `${pos.coords.latitude},${pos.coords.longitude}` })),
        () => undefined,
        { timeout: 3000 },
      );
    }
    load().catch((err) => setError(err instanceof Error ? err.message : 'Failed to load'));
  }, [load]);

  useEffect(() => {
    if (location.hash === '#documents') {
      setStep(3);
      const t = window.setTimeout(() => {
        document.getElementById('documents')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }, 80);
      return () => window.clearTimeout(t);
    }
  }, [location.hash, party?.status, party?.documents]);

  if (!session) return <Navigate to="/login" replace />;
  if (session.role === 'PLATFORM_ADMIN') return <Navigate to="/admin" replace />;
  // Approved merchants use dashboard; don't restart onboarding
  if (session.partyStatus === 'ACTIVE' || party?.status === 'ACTIVE') {
    return <Navigate to="/dashboard" replace />;
  }

  async function saveEntity(e: FormEvent) {
    e.preventDefault();
    setError(''); setOk(''); setLoading(true);
    try {
      const ownerMode = isOwnerEntity(entity.entityType);
      const payload = {
        ...entity,
        applicantIsPartner: ownerMode ? true : entity.applicantIsPartner,
        onboardingStep: ownerMode ? 3 : 2,
      };
      const data = await api<Party>('/api/onboarding/profile', {
        method: 'PUT', token: session!.token,
        body: JSON.stringify(payload),
      });
      setParty(data);
      setEntity((prev) => ({ ...prev, applicantIsPartner: !!data.applicantIsPartner }));
      setOk('Entity details saved');
      setStep(ownerMode ? 3 : 2);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Save failed');
    } finally { setLoading(false); }
  }

  async function addRosterPartner(e: FormEvent) {
    e.preventDefault();
    setError(''); setOk(''); setLoading(true);
    try {
      const data = await api<Party>('/api/onboarding/associated-persons', {
        method: 'POST', token: session!.token,
        body: JSON.stringify({
          roleType: 'PARTNER',
          fullName: roster.fullName,
          email: roster.email,
          phone: roster.phone,
          authorizedToOperate: true,
        }),
      });
      setParty(data);
      setOk('Partner added — they will complete KYC in the mobile app after submit');
      setRoster({ fullName: '', email: '', phone: '' });
      setStep(3);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed');
    } finally { setLoading(false); }
  }

  async function upload(code: string, file: File) {
    setError(''); setOk('');
    const fd = new FormData();
    fd.append('documentCode', code);
    fd.append('file', file);
    try {
      const data = await api<Party>('/api/onboarding/documents', {
        method: 'POST', token: session!.token, body: fd,
      });
      setParty(data); setOk(`${code} uploaded`);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Upload failed');
    }
  }

  async function submit() {
    setError(''); setOk(''); setLoading(true);
    try {
      const data = await api<Party>('/api/onboarding/submit', { method: 'POST', token: session!.token });
      setParty(data);
      setOk(`Submitted. Tracking ${data.trackingId}. Each partner email gets its own corporate portal login (temporary password — change on first login). Partners also get mobile app KYC emails. Status: ${data.status}`);
      setStep(5);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Submit failed');
    } finally { setLoading(false); }
  }

  const editable = party?.status === 'DRAFT' || party?.status === 'REJECTED';
  const rejectedDocs = (party?.documents || []).filter((d) => d.status === 'REJECTED');
  const canReupload = party?.status === 'INCOMPLETE'
    || party?.status === 'SUBMITTED'
    || party?.status === 'PENDING_APPROVAL'
    || rejectedDocs.length > 0;
  const mandatoryDocs = (party?.requiredDocuments || []).filter((d) => d.mandatory);
  const mandatoryUploaded = mandatoryDocs.filter((d) => d.uploaded).length;
  const missingMandatory = mandatoryDocs.filter((d) => !d.uploaded);
  const partnerMode = needsPartnerRoster(entity.entityType);
  const stepLabels = partnerMode
    ? ['Entity', 'Partners', 'Documents', 'Review']
    : ['Entity', 'Documents', 'Review'];

  return (
    <div className="page">
      <div className="container">
        <div className="steps">
          {stepLabels.map((label, i) => {
            const stepNum = partnerMode ? i + 1 : (i === 0 ? 1 : i + 2);
            const active = partnerMode ? step >= i + 1 : (i === 0 ? step >= 1 : step >= i + 2);
            return (
              <span key={label} className={`step-pill ${active ? 'active' : ''}`}>
                {stepNum}. {label}
              </span>
            );
          })}
        </div>

        <div className="panel panel--wide">
          <div className="panel-header">
            <div>
              <div className="badge">SBP CONSOLIDATED · ENTITY KYC</div>
              <h2 style={{ margin: '0 0 0.35rem' }}>Entity account / wallet opening</h2>
              <p className="muted" style={{ margin: 0 }}>
                Tracking ID: <strong>{party?.trackingId || '—'}</strong>
                {party?.draftExpiresAt && <> · Draft until {new Date(party.draftExpiresAt).toLocaleDateString()}</>}
              </p>
            </div>
            {party && <span className={`status status-${party.status}`}>{party.status}</span>}
          </div>

          {error && <div className="alert alert-error alert-spaced">{error}</div>}
          {ok && <div className="alert alert-ok alert-spaced">{ok}</div>}
          {party?.discrepancyNote && (
            <div className="alert alert-info alert-spaced">Discrepancy: {party.discrepancyNote}</div>
          )}
          {party?.rejectionReason && (
            <div className="alert alert-error alert-spaced">Rejected: {party.rejectionReason}</div>
          )}
          {editable && party?.status === 'DRAFT' && missingMandatory.length > 0 && (
            <div className="alert alert-warn alert-spaced">
              <strong>Upload as you go</strong>
              {' — '}
              {mandatoryUploaded} of {mandatoryDocs.length} required documents on file.
              Add what you have now; submit stays locked until the rest are uploaded.
            </div>
          )}

          {editable && step === 1 && (
            <form className="form-grid" style={{ marginTop: '1.25rem' }} onSubmit={saveEntity}>
              <h3 className="form-section-title">§E Entity information</h3>
              <div className="form-row"><label>Entity / legal name</label>
                <input required value={entity.businessName} onChange={(e) => setEntity({ ...entity, businessName: e.target.value })} /></div>
              <div className="form-row"><label>Contact / onboarder name</label>
                <input required value={entity.fullName} onChange={(e) => setEntity({ ...entity, fullName: e.target.value })} /></div>
              <div className="form-row"><label>Entity type (Annex-C 1–4)</label>
                <select value={entity.entityType} onChange={(e) => {
                  const next = e.target.value;
                  setEntity({
                    ...entity,
                    entityType: next,
                    applicantIsPartner: isOwnerEntity(next) ? true : entity.applicantIsPartner,
                  });
                }}>
                  {(entityTypes.length ? entityTypes : [
                    { code: 'SOLE_PROPRIETORSHIP', label: 'Sole Proprietorship' },
                  ]).map((t) => (
                    <option key={t.code} value={t.code}>{t.label}</option>
                  ))}
                </select></div>
              {isOwnerEntity(entity.entityType) ? (
                <p className="muted">
                  Sole prop / small business: <strong>you are the owner</strong>. After submit you get the mobile KYC
                  invite (phone below). No separate authorized person.
                </p>
              ) : (
                <label className="form-check">
                  <input type="checkbox" checked={entity.applicantIsPartner}
                    onChange={(e) => setEntity({ ...entity, applicantIsPartner: e.target.checked })} />
                  I am also a partner of this entity (I will also receive a mobile KYC app invite)
                </label>
              )}
              {entity.entityType === 'PARTNERSHIP' && (
                <label className="form-check">
                  <input type="checkbox" checked={entity.partnershipUnregistered}
                    onChange={(e) => setEntity({ ...entity, partnershipUnregistered: e.target.checked })} />
                  Unregistered partnership (no Registrar of Firms certificate)
                </label>
              )}
              <div className="form-row"><label>Incorporation / registration number</label>
                <input value={entity.incorporationNumber} onChange={(e) => setEntity({ ...entity, incorporationNumber: e.target.value })} /></div>
              <div className="form-row"><label>Date of incorporation</label>
                <input type="date" value={entity.incorporationDate} onChange={(e) => setEntity({ ...entity, incorporationDate: e.target.value })} /></div>
              <div className="form-row"><label>Country of incorporation</label>
                <input value={entity.incorporationCountry} onChange={(e) => setEntity({ ...entity, incorporationCountry: e.target.value })} /></div>
              <div className="form-row"><label>Incorporating authority</label>
                <input value={entity.incorporationAuthority} onChange={(e) => setEntity({ ...entity, incorporationAuthority: e.target.value })} /></div>
              <div className="form-row"><label>NTN / tax number</label>
                <input value={entity.ntnNumber} onChange={(e) => setEntity({ ...entity, ntnNumber: e.target.value })} /></div>
              <div className="form-row"><label>Registered address</label>
                <input required value={entity.registeredAddress} onChange={(e) => setEntity({ ...entity, registeredAddress: e.target.value })} /></div>
              <div className="form-row"><label>Mailing address</label>
                <input value={entity.mailingAddress} onChange={(e) => setEntity({ ...entity, mailingAddress: e.target.value })} /></div>
              <div className="form-row"><label>Place of business</label>
                <input value={entity.placeOfBusiness} onChange={(e) => setEntity({ ...entity, placeOfBusiness: e.target.value })} /></div>
              <div className="form-row"><label>Reason if addresses differ</label>
                <input value={entity.addressDifferenceReason} onChange={(e) => setEntity({ ...entity, addressDifferenceReason: e.target.value })} /></div>
              <div className="form-row"><label>City</label>
                <input value={entity.city} onChange={(e) => setEntity({ ...entity, city: e.target.value })} /></div>
              <div className="form-row"><label>Phone (mobile app user ID for owner / partner)</label>
                <input required={isOwnerEntity(entity.entityType) || entity.applicantIsPartner}
                  value={entity.phone} onChange={(e) => setEntity({ ...entity, phone: e.target.value })} /></div>
              <div className="form-row"><label>Nature of business</label>
                <textarea required rows={2} value={entity.natureOfBusiness} onChange={(e) => setEntity({ ...entity, natureOfBusiness: e.target.value })} /></div>
              <div className="form-row"><label>Purpose of account/wallet</label>
                <input required value={entity.purposeOfAccount} onChange={(e) => setEntity({ ...entity, purposeOfAccount: e.target.value })} /></div>
              <div className="form-row"><label>Intended nature of relationship</label>
                <input value={entity.intendedRelationship} onChange={(e) => setEntity({ ...entity, intendedRelationship: e.target.value })} /></div>
              <label className="form-check">
                <input type="checkbox" checked={entity.termsAccepted} onChange={(e) => setEntity({ ...entity, termsAccepted: e.target.checked })} />
                I accept terms &amp; conditions for this entity account/wallet.
              </label>
              <button className="btn btn-primary" disabled={loading} type="submit">Save & continue</button>
            </form>
          )}

          {editable && partnerMode && step >= 2 && step < 5 && (
            <div className="section-block">
              <h3>Partner roster</h3>
              <p className="muted">
                Add each partner (name, phone = app user ID, unique email). After submit, <strong>each partner</strong> gets
                their own corporate portal login and completes KYC in the mobile app — no separate authorized-person form.
              </p>
              {(party?.associatedPersons || []).map((p) => (
                <div className="doc-row" key={p.id}>
                  <div>
                    <strong>{p.fullName}</strong>
                    <div className="muted">PARTNER · {p.phone || 'no phone'} · {p.email || 'no email'}</div>
                  </div>
                </div>
              ))}
              <form className="form-grid" onSubmit={addRosterPartner}>
                <div className="form-row"><label>Partner full name</label>
                  <input required value={roster.fullName} onChange={(e) => setRoster({ ...roster, fullName: e.target.value })} /></div>
                <div className="form-row"><label>Phone (app user ID)</label>
                  <input required value={roster.phone} onChange={(e) => setRoster({ ...roster, phone: e.target.value })} /></div>
                <div className="form-row"><label>Email</label>
                  <input required type="email" value={roster.email} onChange={(e) => setRoster({ ...roster, email: e.target.value })} /></div>
                <div className="actions">
                  <button className="btn btn-primary" disabled={loading} type="submit">Add partner</button>
                  <button className="btn btn-ghost" type="button" onClick={() => setStep(3)}>Continue to documents</button>
                </div>
              </form>
            </div>
          )}

          {editable && !partnerMode && step === 2 && (
            <div className="section-block">
              <p className="muted">No authorized-person step for this entity. Continue to documents.</p>
              <button className="btn btn-primary" type="button" onClick={() => setStep(3)}>Continue to documents</button>
            </div>
          )}

          {editable && step >= 3 && step < 5 && (
            <div className="section-block" id="documents">
              <h3>Documents {partnerMode ? '(firm + partner CNIC/agreement)' : '(Annex-C)'}</h3>
              <p className="muted">
                Upload whatever you have now and come back later. Submit is only enabled when every required file is attached.
                {mandatoryDocs.length > 0 && (
                  <>
                    {' '}Required: <strong>{mandatoryUploaded}/{mandatoryDocs.length}</strong> uploaded.
                  </>
                )}
              </p>
              {(party?.requiredDocuments || []).map((doc) => (
                <div className="doc-row" key={doc.documentCode}>
                  <div>
                    <strong>{doc.documentLabel}</strong>
                    <div className="muted">
                      {doc.documentCode}
                      {doc.mandatory ? ' · required' : ' · optional'}
                      {doc.uploaded ? ' · uploaded' : doc.mandatory ? ' · still needed' : ' · not yet'}
                    </div>
                  </div>
                  <input type="file" onChange={(e) => { const f = e.target.files?.[0]; if (f) void upload(doc.documentCode, f); }} />
                </div>
              ))}
              <div className="actions">
                <button className="btn btn-ghost" type="button" onClick={() => setStep(4)}>
                  Continue to review
                </button>
              </div>
            </div>
          )}

          {editable && step >= 4 && step < 5 && (
            <div className="section-block form-grid">
              <h3>Review &amp; submit</h3>
              <p className="muted">
                You can review now even if some files are still missing. Submit stays disabled until every required document is uploaded.
              </p>
              <div className="actions">
                <button className="btn btn-ghost" type="button" onClick={() => setStep(3)}>Back to documents</button>
                <button className="btn btn-primary" type="button" disabled={loading || !party?.canSubmit} onClick={() => void submit()}>
                  Submit application
                </button>
              </div>
              {!party?.canSubmit && (
                <p className="muted">Submit stays disabled until entity, roster, and required docs are complete.</p>
              )}
            </div>
          )}

          {(party?.status === 'SUBMITTED' || party?.status === 'PENDING_APPROVAL' || party?.status === 'INCOMPLETE') && (
            <div className="alert alert-info" style={{ marginTop: '1.5rem' }}>
              Status <strong>{party.status}</strong>
              {party.status === 'INCOMPLETE' && (
                <> — one or more documents need re-upload (your full application was <strong>not</strong> rejected).</>
              )}
              {party.status === 'SUBMITTED' && <> — waiting for partners to finish <strong>mobile app KYC</strong>.</>}
              {party.status === 'PENDING_APPROVAL' && <> — ready for backoffice final review (5 working-day TAT).</>}
              <br />Tracking <strong>{party.trackingId}</strong>
              {party.decisionDueAt && <> · Decision due by {new Date(party.decisionDueAt).toLocaleDateString()}</>}
              {(party.partnerAppUsers?.length || 0) > 0 && (
                <>
                  <br />App KYC: {party.partnerKycCompleted}/{party.partnerKycTotal} complete
                  <ul style={{ margin: '0.5rem 0 0', paddingLeft: '1.2rem' }}>
                    {party.partnerAppUsers!.map((u) => (
                      <li key={u.id}>
                        {u.fullName} · {u.phone} · {u.status}
                        {u.bankVisitRequired ? ' · bank visit required' : ''}
                        {u.failureReason ? ` · ${u.failureReason}` : ''}
                        {u.signatureUploaded ? ' · signature on file' : ' · signature missing'}
                        {!u.signatureUploaded && !u.bankVisitRequired && (
                          <>
                            {' · '}
                            <Link to={`/signature/${u.id}`}>Upload signature</Link>
                          </>
                        )}
                      </li>
                    ))}
                  </ul>
                </>
              )}
            </div>
          )}

          {canReupload && rejectedDocs.length > 0 && (
            <div className="section-block" id="documents" style={{ marginTop: '1.25rem' }}>
              <h3>Re-upload rejected documents</h3>
              <p className="muted">Only these files need to be replaced. Other approved documents stay as-is.</p>
              {rejectedDocs.map((doc) => (
                <div className="doc-row" key={doc.id}>
                  <div>
                    <strong>{doc.documentCode}</strong>
                    <div className="muted">
                      {doc.originalName} · REJECTED
                      {doc.reviewNote ? ` · ${doc.reviewNote}` : ''}
                    </div>
                  </div>
                  <input
                    type="file"
                    onChange={(e) => {
                      const f = e.target.files?.[0];
                      if (f) void upload(doc.documentCode, f);
                    }}
                  />
                </div>
              ))}
            </div>
          )}

          {party?.status === 'ACTIVE' && (
            <div className="alert alert-ok" style={{ marginTop: '1.5rem' }}>Approved. Login with emailed credentials.</div>
          )}
        </div>
      </div>
    </div>
  );
}
