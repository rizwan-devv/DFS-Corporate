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
  if (row.status === 'APPROVED' || row.currentStep === 'DONE') return 'RELEASED';
  if (row.status === 'REJECTED') {
    const stop = row.actions?.some((a) => (a.comment || '').toUpperCase().includes('STOP'));
    return stop ? 'STOPPED' : 'REJECTED';
  }
  if (row.status === 'IN_PROGRESS') {
    if (row.currentStep === 'RELEASER') return 'PENDING_RELEASE';
    if (row.currentStep === 'APPROVER') return 'CHECKED';
    if (row.currentStep === 'CHECKER') return 'PENDING_CHECK';
    return row.currentStep || 'IN_PROGRESS';
  }
  return row.status || '—';
}

function isShortPath(row: ApprovalRow): boolean {
  const actions = row.actions || [];
  return (
    actions.some((a) => (a.comment || '').toLowerCase().includes('skipped checker/approver')) ||
    (actions.some((a) => a.step === 'MAKER' && a.decision === 'SUBMIT') &&
      !actions.some((a) => a.step === 'CHECKER' || a.step === 'APPROVER') &&
      (row.currentStep === 'RELEASER' || row.currentStep === 'DONE' || row.status === 'APPROVED'))
  );
}

/** Progress toward release: short path (≤5k) is submit+release; full path is check+approve+release. */
export function approvalCount(row: ApprovalRow): string {
  const needed = isShortPath(row) ? 2 : 3;
  const done = (row.actions || []).filter((a) => a.decision === 'APPROVE' || a.decision === 'SUBMIT').length;
  return `${Math.min(done, needed)}/${needed}`;
}

/** Human step label for the Steps column (e.g. "Awaiting release · 1/2"). */
export function progressLabel(row: ApprovalRow): string {
  const count = approvalCount(row);
  const st = displayStatus(row);
  if (st === 'RELEASED') return `Released · ${count}`;
  if (st === 'PENDING_RELEASE') return `Awaiting release · ${count}`;
  if (st === 'PENDING_CHECK') return `Awaiting checker · ${count}`;
  if (st === 'CHECKED') return `Awaiting approver · ${count}`;
  if (st === 'STOPPED') return `Stopped · ${count}`;
  if (st === 'REJECTED') return `Rejected · ${count}`;
  return count;
}

export function isPendingDisplayStatus(st: string): boolean {
  return st === 'PENDING_CHECK' || st === 'CHECKED' || st === 'PENDING_RELEASE';
}

export function shortRequestId(publicId: string): string {
  const clean = (publicId || '').replace(/-/g, '');
  return clean.length >= 8 ? clean.slice(-8).toUpperCase() : publicId || '—';
}

export function formatActionDecision(step: string, decision: string, comment?: string): string {
  const c = (comment || '').toUpperCase();
  if (decision === 'SUBMIT') return 'Submitted';
  if (decision === 'REJECT' || c.includes('STOP')) return c.includes('STOP') ? 'Stopped' : 'Rejected';
  if (decision === 'APPROVE' && step === 'RELEASER') return 'Released';
  if (decision === 'APPROVE' && step === 'CHECKER') return 'Checked';
  if (decision === 'APPROVE' && step === 'APPROVER') return 'Approved';
  if (decision === 'APPROVE') return 'Approved';
  return decision;
}

export function formatWhen(iso?: string): string {
  if (!iso) return '—';
  try {
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return iso;
    return d.toLocaleString(undefined, {
      year: 'numeric',
      month: 'short',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return iso;
  }
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
