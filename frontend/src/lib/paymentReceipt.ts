import type { DfsTxnResponse } from './liveTransfers';
import type { MockTransfer, TransferProduct } from './transferTypes';

export type ReceiptProduct = TransferProduct | string;

export type PaymentReceiptModel = {
  product: ReceiptProduct;
  productLabel: string;
  success: boolean;
  statusLabel: string;
  amountDisplay: string;
  currency: string;
  paidAt: string;
  payerAccountNo?: string;
  portalTxnRef?: string;
  counterpartyTitle?: string;
  counterpartySubtitle?: string;
  rows: { label: string; value: string }[];
  networkRows: { label: string; value: string }[];
  message?: string;
  responseCode?: string;
};

function productLabel(p: string): string {
  switch ((p || '').toUpperCase()) {
    case 'FT': return 'Fund Transfer';
    case 'IBFT': return 'Interbank Transfer';
    case 'UBP': return 'Utility Bill Payment';
    case 'RAAST': return 'Raast Payment';
    default: return p || 'Payment';
  }
}

function asRecord(v: unknown): Record<string, unknown> | null {
  if (v && typeof v === 'object' && !Array.isArray(v)) return v as Record<string, unknown>;
  return null;
}

function str(v: unknown): string | undefined {
  if (v == null) return undefined;
  const s = String(v).trim();
  return s ? s : undefined;
}

/** DFS often returns amount as 000000001000 (paisa-style / padded). */
export function formatAmountPkr(raw: string | number | undefined | null): string {
  if (raw == null || raw === '') return '—';
  if (typeof raw === 'number' && Number.isFinite(raw)) {
    return raw.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }
  const s = String(raw).trim();
  if (/^\d+$/.test(s) && s.length >= 10) {
    const n = Number(s) / 100;
    if (Number.isFinite(n)) {
      return n.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }
  }
  const n = Number(s);
  if (Number.isFinite(n)) {
    return n.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }
  return s;
}

function pick(data: Record<string, unknown> | null, ...keys: string[]): string | undefined {
  if (!data) return undefined;
  for (const k of keys) {
    const v = str(data[k]);
    if (v) return v;
  }
  return undefined;
}

export function buildReceiptFromLive(
  r: DfsTxnResponse,
  opts?: {
    product?: string;
    amount?: string | number;
    beneficiaryName?: string;
    bankName?: string;
    bankImd?: string;
    accountNumber?: string;
    utilityCompanyCode?: string;
    utilityCompanyName?: string;
    consumerNo?: string;
    portalTxnRef?: string;
    paidAt?: string;
  },
): PaymentReceiptModel {
  const data = asRecord(r.data) || asRecord(r.raw);
  const product = (opts?.product || r.product || pick(data, 'product') || 'PAYMENT').toUpperCase();
  const success = r.responsecode === '000';
  const amountRaw =
    opts?.amount
    ?? pick(data, 'amount', 'billAmount', 'transactionAmount')
    ?? undefined;

  const payer = opts?.accountNumber
    ? undefined
    : (r.fromAccountNo || pick(data, 'fromAccountNo', 'fromAccount'));

  const stan = pick(data, 'stan', 'STAN');
  const rrn = pick(data, 'rrn', 'RRN');
  const switchCode = pick(data, 'switchResponseCode', 'switchCode');
  const coreCode = pick(data, 'coreResponseCode', 'coreCode') || r.responsecode;
  const authId = pick(data, 'authIdResponse', 'authId');
  const desc = pick(data, 'responseDescription') || r.messages;

  const rows: { label: string; value: string }[] = [];
  const from = r.fromAccountNo || pick(data, 'fromAccountNo');
  if (from) rows.push({ label: 'From', value: from });

  if (product === 'UBP') {
    const company =
      opts?.utilityCompanyName
      || pick(data, 'utilityCompanyName')
      || opts?.utilityCompanyCode
      || pick(data, 'utilityCompanyCode');
    const code = opts?.utilityCompanyCode || pick(data, 'utilityCompanyCode');
    const consumer = opts?.consumerNo || pick(data, 'consumerNo', 'consumerNumber');
    if (company) rows.push({ label: 'Biller', value: company });
    if (code && code !== company) rows.push({ label: 'Biller code', value: code });
    if (consumer) rows.push({ label: 'Consumer', value: consumer });
  } else {
    const to =
      opts?.accountNumber
      || pick(data, 'beneficiaryAccountNo', 'accountNo', 'toAccountNo', 'accountNumber');
    const name = opts?.beneficiaryName || pick(data, 'beneficiaryName', 'accountTitle');
    const bank = opts?.bankName || pick(data, 'bankName', 'beneficiaryBankName');
    const imd = opts?.bankImd || pick(data, 'beneficiaryBankImd', 'bankImd');
    if (name) rows.push({ label: 'Beneficiary', value: name });
    if (to) rows.push({ label: product === 'IBFT' ? 'Account / IBAN' : 'To wallet', value: to });
    if (bank) rows.push({ label: 'Bank', value: bank });
    if (imd) rows.push({ label: 'Bank IMD', value: imd });
  }

  const networkRows: { label: string; value: string }[] = [];
  if (stan) networkRows.push({ label: 'STAN', value: stan });
  if (rrn) networkRows.push({ label: 'RRN', value: rrn });
  if (switchCode) networkRows.push({ label: 'Switch', value: switchCode });
  if (coreCode) networkRows.push({ label: 'Core', value: coreCode });
  if (authId) networkRows.push({ label: 'Auth ID', value: authId });

  const counterpartyTitle =
    product === 'UBP'
      ? (opts?.utilityCompanyName || pick(data, 'utilityCompanyName') || opts?.utilityCompanyCode || pick(data, 'utilityCompanyCode'))
      : (opts?.beneficiaryName || pick(data, 'beneficiaryName'));
  const counterpartySubtitle =
    product === 'UBP'
      ? (opts?.consumerNo || pick(data, 'consumerNo'))
      : (opts?.accountNumber || pick(data, 'beneficiaryAccountNo', 'accountNo'));

  return {
    product,
    productLabel: productLabel(product),
    success,
    statusLabel: success ? 'SUCCESS' : 'FAILED',
    amountDisplay: formatAmountPkr(amountRaw),
    currency: 'PKR',
    paidAt: opts?.paidAt || new Date().toISOString(),
    payerAccountNo: from || payer,
    portalTxnRef: opts?.portalTxnRef || (r as DfsTxnResponse & { portalTxnRef?: string }).portalTxnRef,
    counterpartyTitle,
    counterpartySubtitle,
    rows,
    networkRows,
    message: desc,
    responseCode: r.responsecode,
  };
}

export function buildReceiptFromHistory(t: MockTransfer, live?: DfsTxnResponse | null): PaymentReceiptModel {
  const success = t.status === 'LIVE_SUCCESS' || t.status === 'SUCCESS' || t.status === 'COMPLETED';
  const base = live
    ? buildReceiptFromLive(live, {
        product: t.productType,
        amount: t.amount,
        accountNumber: t.accountNumber,
        beneficiaryName: t.beneficiaryName,
        bankName: t.bankName,
        utilityCompanyCode: t.ubpCompany,
        consumerNo: t.consumerNumber,
        portalTxnRef: t.mockTxnRef,
        paidAt: t.createdAt,
      })
    : null;

  if (base) {
    return {
      ...base,
      portalTxnRef: t.mockTxnRef || base.portalTxnRef,
      paidAt: t.createdAt || base.paidAt,
      success,
      statusLabel: success ? 'SUCCESS' : (t.status || 'FAILED'),
    };
  }

  const rows: { label: string; value: string }[] = [];
  if (t.productType === 'UBP') {
    if (t.ubpCompany) rows.push({ label: 'Biller code', value: t.ubpCompany });
    if (t.consumerNumber) rows.push({ label: 'Consumer', value: t.consumerNumber });
  } else {
    if (t.beneficiaryName) rows.push({ label: 'Beneficiary', value: t.beneficiaryName });
    if (t.accountNumber) rows.push({ label: t.productType === 'IBFT' ? 'Account / IBAN' : 'To wallet', value: t.accountNumber });
    if (t.bankName) rows.push({ label: 'Bank', value: t.bankName });
  }
  if (t.notes) rows.push({ label: 'Notes', value: t.notes });

  return {
    product: t.productType,
    productLabel: productLabel(t.productType),
    success,
    statusLabel: success ? 'SUCCESS' : (t.status || 'FAILED'),
    amountDisplay: formatAmountPkr(t.amount),
    currency: 'PKR',
    paidAt: t.createdAt || new Date().toISOString(),
    portalTxnRef: t.mockTxnRef,
    counterpartyTitle: t.beneficiaryName || t.ubpCompany,
    counterpartySubtitle: t.accountNumber || t.consumerNumber,
    rows,
    networkRows: [],
    message: t.notes,
    responseCode: success ? '000' : undefined,
  };
}

export function formatReceiptWhen(iso?: string): string {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleString(undefined, {
      year: 'numeric',
      month: 'short',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
  } catch {
    return iso;
  }
}
