/** Maker → Checker → Approver → Releaser helpers for FT/IBFT UI. */

export type PortalRoleName = 'PARTY_ADMIN' | 'MAKER' | 'CHECKER' | 'APPROVER' | 'RELEASER';

export type ApprovalRow = {
  publicId: string;
  requestType: string;
  title: string;
  referenceKey?: string;
  status: string;
  currentStep: string;
  createdAt?: string;
  payloadJson?: string;
  actions?: { step: string; decision: string; actorEmail: string; comment?: string; createdAt?: string }[];
};

export type PaymentPayload = {
  product?: string;
  amount?: string | number;
  accountNumber?: string;
  bankName?: string;
  bankImd?: string;
  beneficiaryName?: string;
  notes?: string;
  purposeOfPayment?: string;
  mobile?: string;
};

export function parsePayload(json?: string): PaymentPayload {
  if (!json) return {};
  try {
    return JSON.parse(json) as PaymentPayload;
  } catch {
    return {};
  }
}

export function displayStatus(row: ApprovalRow): string {
  if (row.status === 'APPROVED') return 'AUTHORIZED';
  if (row.status === 'REJECTED') {
    const stop = row.actions?.some((a) => (a.comment || '').toUpperCase().includes('STOP'));
    return stop ? 'STOPPED' : 'REJECTED';
  }
  if (row.status === 'IN_PROGRESS') {
    if (row.currentStep === 'RELEASER') return 'AUTHORIZED';
    if (row.currentStep === 'APPROVER') return 'CHECKED';
    if (row.currentStep === 'CHECKER') return 'PENDING_CHECK';
    return row.currentStep || 'IN_PROGRESS';
  }
  return row.status || '—';
}

export function approvalCount(row: ApprovalRow): string {
  const approved = (row.actions || []).filter((a) => a.decision === 'APPROVE' || a.decision === 'SUBMIT').length;
  const needed = 3; // checker + approver + releaser (approx)
  return `${Math.min(approved, needed)}/${needed}`;
}

export function roleFlags(roles: string[] | undefined) {
  const r = new Set(roles || []);
  const admin = r.has('PARTY_ADMIN');
  return {
    isAdmin: admin,
    canMake: admin || r.has('MAKER'),
    canCheck: admin || r.has('CHECKER'),
    canApprove: admin || r.has('APPROVER'),
    canRelease: admin || r.has('RELEASER'),
    roles: [...r],
  };
}

export function canActOn(row: ApprovalRow, roles: string[] | undefined): {
  details: boolean;
  approve: boolean;
  reject: boolean;
  release: boolean;
  stop: boolean;
} {
  const f = roleFlags(roles);
  const inProgress = row.status === 'IN_PROGRESS';
  const step = row.currentStep;
  return {
    details: true,
    approve: inProgress && ((step === 'CHECKER' && f.canCheck) || (step === 'APPROVER' && f.canApprove)),
    reject: inProgress && ((step === 'CHECKER' && f.canCheck) || (step === 'APPROVER' && f.canApprove)),
    release: inProgress && step === 'RELEASER' && f.canRelease,
    stop: inProgress && (f.canRelease || f.canApprove || f.isAdmin),
  };
}
