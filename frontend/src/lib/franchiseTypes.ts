/** Shared franchise invite / child types for portal pages. */

export type FranchiseInvite = {
  id: number;
  contactName: string;
  email: string;
  phone: string;
  businessName?: string;
  entityType?: string;
  status: string;
  inviteUrl: string;
  invitedAt?: string;
  expiresAt?: string;
  completedAt?: string;
  childTrackingId?: string;
  childStatus?: string;
  commissionRatePercent?: number;
  commissionType?: string;
  commissionNotes?: string;
};

export type FranchiseChild = {
  id: number;
  publicId: string;
  trackingId?: string;
  businessName?: string;
  fullName?: string;
  email?: string;
  phone?: string;
  status?: string;
  partyType?: string;
  entityType?: string;
  commissionRatePercent?: number;
  commissionStatus?: string;
  commissionType?: string;
  dfsAccountId?: string;
  levelCode?: string;
};

export type AgentAppPortalResponse = {
  responsecode?: string;
  messages?: string;
  data?: unknown;
  childPartyId?: number;
  childTrackingId?: string;
  mobileNumber?: string;
  accountLevelCode?: string;
};

export type AgentMiniStatementRow = {
  transDate?: string;
  transDocsDescr?: string;
  txnAmt?: number | null;
  feeAmt?: number | null;
  amountType?: string;
  closingBalance?: number | null;
  openingbalance?: number | null;
  transRefnum?: string;
  toAccountNo?: string;
  fromAccountNo?: string;
  channel?: string;
};

export function isPendingInvite(inv: FranchiseInvite): boolean {
  const s = (inv.status || '').toUpperCase();
  return s !== 'COMPLETED' && s !== 'CANCELLED' && s !== 'EXPIRED';
}

export function fmtDate(iso?: string) {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleDateString(undefined, {
      year: 'numeric', month: 'short', day: 'numeric',
    });
  } catch {
    return '—';
  }
}

export function fmtDateTime(iso?: string) {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleString(undefined, {
      year: 'numeric', month: 'short', day: 'numeric',
      hour: '2-digit', minute: '2-digit',
    });
  } catch {
    return '—';
  }
}

export function agentMiniStatementRows(data: unknown): AgentMiniStatementRow[] {
  if (Array.isArray(data)) return data as AgentMiniStatementRow[];
  if (data && typeof data === 'object') {
    const nested = data as { transactions?: unknown; list?: unknown; records?: unknown };
    if (Array.isArray(nested.transactions)) return nested.transactions as AgentMiniStatementRow[];
    if (Array.isArray(nested.list)) return nested.list as AgentMiniStatementRow[];
    if (Array.isArray(nested.records)) return nested.records as AgentMiniStatementRow[];
  }
  return [];
}

export function amountTypeLabel(t?: string) {
  if (!t) return '—';
  const u = t.toUpperCase();
  if (u === 'D' || u === 'DR') return 'Debit';
  if (u === 'C' || u === 'CR') return 'Credit';
  return t;
}

export function agentBalanceValue(resp: AgentAppPortalResponse | null): string {
  if (!resp?.data || typeof resp.data !== 'object' || Array.isArray(resp.data)) return '—';
  const bal = (resp.data as { balance?: number }).balance;
  return bal == null ? '—' : String(bal);
}
