import { type FormEvent, useCallback, useEffect, useState } from 'react';
import { Navigate } from 'react-router-dom';
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
};

type Party = {
  status: string;
  partyType: string;
  trackingId?: string;
  canSubmit: boolean;
  requiredDocuments: RequiredDoc[];
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
  riskRating?: string;
  eddRequired?: boolean;
  eddNotes?: string;
  videoKycRef?: string;
  sanctionsStatus?: string;
  identityVerificationStatus?: string;
  onboardingStep?: number;
};

const ROLES = ['AUTHORIZED_SIGNATORY', 'BENEFICIAL_OWNER', 'PARTNER', 'SENIOR_MANAGING_OFFICIAL', 'OTHER'];

function needsPartnerRoster(entityType?: string) {
  return entityType === 'PARTNERSHIP' || entityType === 'LLP';
}

export function OnboardingPage() {
  const { session } = useAuth();
  const [party, setParty] = useState<Party | null>(null);
  const [entityTypes, setEntityTypes] = useState<Option[]>([]);
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');
  const [loading, setLoading] = useState(false);
  const [step, setStep] = useState(1);
  const [entity, setEntity] = useState({
    fullName: '', businessName: '', entityType: 'SOLE_PROPRIETORSHIP',
    partnershipUnregistered: false, applicantIsPartner: false,
    incorporationNumber: '', incorporationDate: '', incorporationCountry: 'Pakistan',
    incorporationAuthority: 'SECP', ntnNumber: '', taxCountry: 'Pakistan',
    fatcaCrsDeclared: false, fatcaCrsDetails: '',
    registeredAddress: '', mailingAddress: '', placeOfBusiness: '',
    addressDifferenceReason: '', city: '', country: 'Pakistan', phone: '',
    natureOfBusiness: '', businessLicenseDetails: '', purposeOfAccount: '',
    intendedRelationship: '', termsAccepted: false, onboardingStep: 2,
    geoLocation: '', riskRating: 'MEDIUM', eddRequired: false, eddNotes: '', videoKycRef: '',
  });
  const [person, setPerson] = useState({
    roleType: 'AUTHORIZED_SIGNATORY', fullName: '', fatherOrSpouseName: '',
    dateOfBirth: '', motherMaidenName: '', placeOfBirth: '',
    idDocumentType: 'CNIC', idDocumentNumber: '', ownershipPercent: '',
    authorizedToOperate: true, phone: '', email: '',
  });
  const [roster, setRoster] = useState({
    fullName: '', email: '', phone: '', roleType: 'PARTNER', authorizedToOperate: true,
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
      riskRating: data.riskRating || 'MEDIUM',
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
      const data = await api<Party>('/api/onboarding/profile', {
        method: 'PUT', token: session!.token,
        body: JSON.stringify({ ...entity, onboardingStep: 2 }),
      });
      setParty(data); setOk('Entity details saved'); setStep(2);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Save failed');
    } finally { setLoading(false); }
  }

  async function addPerson(e: FormEvent) {
    e.preventDefault();
    setError(''); setOk(''); setLoading(true);
    try {
      const body = {
        ...person,
        ownershipPercent: person.ownershipPercent ? Number(person.ownershipPercent) : null,
        dateOfBirth: person.dateOfBirth || null,
      };
      const data = await api<Party>('/api/onboarding/associated-persons', {
        method: 'POST', token: session!.token, body: JSON.stringify(body),
      });
      setParty(data); setOk('Associated person added'); setStep(3);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed');
    } finally { setLoading(false); }
  }

  async function addRosterPartner(e: FormEvent) {
    e.preventDefault();
    setError(''); setOk(''); setLoading(true);
    try {
      const data = await api<Party>('/api/onboarding/associated-persons', {
        method: 'POST', token: session!.token,
        body: JSON.stringify({
          roleType: roster.roleType,
          fullName: roster.fullName,
          email: roster.email,
          phone: roster.phone,
          authorizedToOperate: roster.authorizedToOperate,
        }),
      });
      setParty(data);
      setOk('Partner added to roster — upload their CNIC & agreement in Documents');
      setRoster({ fullName: '', email: '', phone: '', roleType: 'PARTNER', authorizedToOperate: true });
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

  async function saveEdd() {
    setError(''); setOk(''); setLoading(true);
    try {
      const data = await api<Party>('/api/onboarding/profile', {
        method: 'PUT', token: session!.token,
        body: JSON.stringify({ ...entity, onboardingStep: 4, eddRequired: entity.riskRating === 'HIGH' || entity.eddRequired }),
      });
      setParty(data); setOk('EDD details saved');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Save failed');
    } finally { setLoading(false); }
  }

  async function submit() {
    setError(''); setOk(''); setLoading(true);
    try {
      const data = await api<Party>('/api/onboarding/submit', { method: 'POST', token: session!.token });
      setParty(data);
      setOk(`Submitted. Tracking ${data.trackingId}. Partners will get mobile app KYC emails (check backend logs if mail off). Status: ${data.status}`);
      setStep(5);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Submit failed');
    } finally { setLoading(false); }
  }

  const editable = party?.status === 'DRAFT' || party?.status === 'REJECTED';
  const partnerMode = needsPartnerRoster(entity.entityType);

  return (
    <div className="page">
      <div className="container">
        <div className="steps">
          {['Entity', 'Persons', 'Documents', 'EDD', 'Review'].map((label, i) => (
            <span key={label} className={`step-pill ${step >= i + 1 ? 'active' : ''}`}>{i + 1}. {label}</span>
          ))}
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

          {editable && step === 1 && (
            <form className="form-grid" style={{ marginTop: '1.25rem' }} onSubmit={saveEntity}>
              <h3 className="form-section-title">§E Entity information</h3>
              <div className="form-row"><label>Entity / legal name</label>
                <input required value={entity.businessName} onChange={(e) => setEntity({ ...entity, businessName: e.target.value })} /></div>
              <div className="form-row"><label>Contact / onboarder name</label>
                <input required value={entity.fullName} onChange={(e) => setEntity({ ...entity, fullName: e.target.value })} /></div>
              <div className="form-row"><label>Entity type (Annex-C 1–4)</label>
                <select value={entity.entityType} onChange={(e) => setEntity({ ...entity, entityType: e.target.value })}>
                  {(entityTypes.length ? entityTypes : [
                    { code: 'SOLE_PROPRIETORSHIP', label: 'Sole Proprietorship' },
                  ]).map((t) => (
                    <option key={t.code} value={t.code}>{t.label}</option>
                  ))}
                </select></div>
              <label className="form-check">
                <input type="checkbox" checked={entity.applicantIsPartner}
                  onChange={(e) => setEntity({ ...entity, applicantIsPartner: e.target.checked })} />
                I am also a partner / BO of this entity (I will receive the mobile KYC app link after submit)
              </label>
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
              <div className="form-row"><label>Phone (also used as app user ID if you are a partner)</label>
                <input value={entity.phone} onChange={(e) => setEntity({ ...entity, phone: e.target.value })} /></div>
              <div className="form-row"><label>Nature of business</label>
                <textarea required rows={2} value={entity.natureOfBusiness} onChange={(e) => setEntity({ ...entity, natureOfBusiness: e.target.value })} /></div>
              <div className="form-row"><label>Purpose of account/wallet</label>
                <input required value={entity.purposeOfAccount} onChange={(e) => setEntity({ ...entity, purposeOfAccount: e.target.value })} /></div>
              <div className="form-row"><label>Intended nature of relationship</label>
                <input value={entity.intendedRelationship} onChange={(e) => setEntity({ ...entity, intendedRelationship: e.target.value })} /></div>
              <div className="form-row"><label>Risk rating (CRP)</label>
                <select value={entity.riskRating} onChange={(e) => setEntity({ ...entity, riskRating: e.target.value, eddRequired: e.target.value === 'HIGH' })}>
                  <option value="LOW">LOW</option><option value="MEDIUM">MEDIUM</option><option value="HIGH">HIGH</option>
                </select></div>
              <label className="form-check">
                <input type="checkbox" checked={entity.termsAccepted} onChange={(e) => setEntity({ ...entity, termsAccepted: e.target.checked })} />
                I accept terms &amp; conditions for this entity account/wallet.
              </label>
              <button className="btn btn-primary" disabled={loading} type="submit">Save & continue</button>
            </form>
          )}

          {editable && step >= 2 && step < 5 && (
            <div className="section-block">
              {partnerMode ? (
                <>
                  <h3>Partner roster</h3>
                  <p className="muted">
                    Add each partner (name, phone = future app user ID, email). You upload their CNIC &amp; agreement on the portal.
                    After submit they get a <strong>mobile app</strong> KYC link — not a portal KYC page.
                  </p>
                  {(party?.associatedPersons || []).map((p) => (
                    <div className="doc-row" key={p.id}>
                      <div>
                        <strong>{p.fullName}</strong>
                        <div className="muted">{p.roleType} · {p.phone || 'no phone'} · {p.email || 'no email'}
                          {p.authorizedToOperate ? ' · operator' : ''}
                        </div>
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
                    <div className="form-row"><label>Role</label>
                      <select value={roster.roleType} onChange={(e) => setRoster({ ...roster, roleType: e.target.value })}>
                        <option value="PARTNER">PARTNER</option>
                        <option value="AUTHORIZED_SIGNATORY">AUTHORIZED_SIGNATORY</option>
                        <option value="BENEFICIAL_OWNER">BENEFICIAL_OWNER</option>
                      </select></div>
                    <label className="form-check">
                      <input type="checkbox" checked={roster.authorizedToOperate}
                        onChange={(e) => setRoster({ ...roster, authorizedToOperate: e.target.checked })} />
                      Authorized to open / operate account
                    </label>
                    <div className="actions">
                      <button className="btn btn-primary" disabled={loading} type="submit">Add partner</button>
                      <button className="btn btn-ghost" type="button" onClick={() => setStep(3)}>Continue to documents</button>
                    </div>
                  </form>
                </>
              ) : (
                <>
                  <h3>Associated persons (§E)</h3>
                  <p className="muted">Need ≥1 authorized operator; BOs ≥20% (or ≥10% if EDD/HIGH).</p>
                  {(party?.associatedPersons || []).map((p) => (
                    <div className="doc-row" key={p.id}>
                      <div><strong>{p.fullName}</strong>
                        <div className="muted">{p.roleType} {p.authorizedToOperate ? '· operator' : ''} {p.ownershipPercent != null ? `· ${p.ownershipPercent}%` : ''}</div>
                      </div>
                    </div>
                  ))}
                  <form className="form-grid" onSubmit={addPerson}>
                    <div className="form-row"><label>Role</label>
                      <select value={person.roleType} onChange={(e) => setPerson({ ...person, roleType: e.target.value })}>
                        {ROLES.map((r) => <option key={r} value={r}>{r}</option>)}
                      </select></div>
                    <div className="form-row"><label>Full name</label>
                      <input required value={person.fullName} onChange={(e) => setPerson({ ...person, fullName: e.target.value })} /></div>
                    <div className="form-row"><label>Father / spouse</label>
                      <input value={person.fatherOrSpouseName} onChange={(e) => setPerson({ ...person, fatherOrSpouseName: e.target.value })} /></div>
                    <div className="form-row"><label>Date of birth</label>
                      <input type="date" value={person.dateOfBirth} onChange={(e) => setPerson({ ...person, dateOfBirth: e.target.value })} /></div>
                    <div className="form-row"><label>Mother&apos;s maiden name (operators)</label>
                      <input value={person.motherMaidenName} onChange={(e) => setPerson({ ...person, motherMaidenName: e.target.value })} /></div>
                    <div className="form-row"><label>Place of birth (operators)</label>
                      <input value={person.placeOfBirth} onChange={(e) => setPerson({ ...person, placeOfBirth: e.target.value })} /></div>
                    <div className="form-row"><label>ID number</label>
                      <input value={person.idDocumentNumber} onChange={(e) => setPerson({ ...person, idDocumentNumber: e.target.value })} /></div>
                    <div className="form-row"><label>Ownership %</label>
                      <input value={person.ownershipPercent} onChange={(e) => setPerson({ ...person, ownershipPercent: e.target.value })} /></div>
                    <label className="form-check">
                      <input type="checkbox" checked={person.authorizedToOperate} onChange={(e) => setPerson({ ...person, authorizedToOperate: e.target.checked })} />
                      Authorized to open / operate account
                    </label>
                    <div className="actions">
                      <button className="btn btn-primary" disabled={loading} type="submit">Add person</button>
                      <button className="btn btn-ghost" type="button" onClick={() => setStep(3)}>Continue to documents</button>
                    </div>
                  </form>
                </>
              )}
            </div>
          )}

          {editable && step >= 3 && step < 5 && (
            <div className="section-block">
              <h3>Documents {partnerMode ? '(firm + partner CNIC/agreement)' : '(Annex-C)'}</h3>
              <p className="muted">
                {partnerMode
                  ? 'Upload firm deed/authority docs and each partner\'s CNIC front/back + agreement yourself.'
                  : 'Upload required entity documents.'}
              </p>
              {(party?.requiredDocuments || []).map((doc) => (
                <div className="doc-row" key={doc.documentCode}>
                  <div>
                    <strong>{doc.documentLabel}</strong>
                    <div className="muted">{doc.documentCode} {doc.mandatory ? '· required' : '· optional'} {doc.uploaded ? '· uploaded' : ''}</div>
                  </div>
                  <input type="file" onChange={(e) => { const f = e.target.files?.[0]; if (f) void upload(doc.documentCode, f); }} />
                </div>
              ))}
              <div className="actions">
                <button className="btn btn-ghost" type="button" onClick={() => setStep(4)}>EDD / continue</button>
              </div>
            </div>
          )}

          {editable && step >= 4 && step < 5 && (
            <div className="section-block form-grid">
              <h3>§G Enhanced Due Diligence</h3>
              <p className="muted">Required when risk is HIGH. Provide video KYC ref or EDD notes.</p>
              <div className="form-row"><label>Video KYC reference / URL</label>
                <input value={entity.videoKycRef} onChange={(e) => setEntity({ ...entity, videoKycRef: e.target.value })} /></div>
              <div className="form-row"><label>EDD notes</label>
                <textarea rows={2} value={entity.eddNotes} onChange={(e) => setEntity({ ...entity, eddNotes: e.target.value })} /></div>
              <div className="actions">
                <button className="btn btn-ghost" type="button" disabled={loading} onClick={() => void saveEdd()}>Save EDD</button>
                <button className="btn btn-primary" type="button" disabled={loading || !party?.canSubmit} onClick={() => void submit()}>
                  Submit application
                </button>
              </div>
              {!party?.canSubmit && (
                <p className="muted">Submit stays disabled until entity, roster, and required docs are complete.</p>
              )}
            </div>
          )}

          {(party?.status === 'SUBMITTED' || party?.status === 'PENDING_APPROVAL') && (
            <div className="alert alert-info" style={{ marginTop: '1.5rem' }}>
              Status <strong>{party.status}</strong>
              {party.status === 'SUBMITTED' && <> — waiting for partners to finish <strong>mobile app KYC</strong>.</>}
              {party.status === 'PENDING_APPROVAL' && <> — ready for backoffice final review (5 working-day TAT).</>}
              <br />Tracking <strong>{party.trackingId}</strong>
              {party.decisionDueAt && <> · Decision due by {new Date(party.decisionDueAt).toLocaleDateString()}</>}
              {(party.partnerAppUsers?.length || 0) > 0 && (
                <>
                  <br />App KYC: {party.partnerKycCompleted}/{party.partnerKycTotal} complete
                  <ul style={{ margin: '0.5rem 0 0', paddingLeft: '1.2rem' }}>
                    {party.partnerAppUsers!.map((u) => (
                      <li key={u.id}>{u.fullName} · {u.phone} · {u.status}</li>
                    ))}
                  </ul>
                </>
              )}
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
