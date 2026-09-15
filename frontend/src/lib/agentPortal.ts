/** Shared AgentApp portal response helpers for Balance / Statement pages. */

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
};

export function agentBalanceValue(resp: AgentAppPortalResponse | null): string {
  if (!resp?.data || typeof resp.data !== 'object' || Array.isArray(resp.data)) return '—';
  const bal = (resp.data as { balance?: number }).balance;
  return bal == null ? '—' : String(bal);
}

export function agentMiniStatementRows(data: unknown): AgentMiniStatementRow[] {
  if (Array.isArray(data)) return data as AgentMiniStatementRow[];
  if (data && typeof data === 'object') {
    const nested = data as { transactions?: unknown; list?: unknown; records?: unknown; data?: unknown };
    if (Array.isArray(nested.transactions)) return nested.transactions as AgentMiniStatementRow[];
    if (Array.isArray(nested.list)) return nested.list as AgentMiniStatementRow[];
    if (Array.isArray(nested.records)) return nested.records as AgentMiniStatementRow[];
    if (Array.isArray(nested.data)) return nested.data as AgentMiniStatementRow[];
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

export function formatMoney(n: number | null | undefined): string {
  if (n == null || Number.isNaN(Number(n))) return '—';
  return Number(n).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}
