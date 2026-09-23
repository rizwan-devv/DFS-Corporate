import { api } from './api';

export type LiveStatus = {
  liveEnabled: boolean;
  txnConfigured: boolean;
  appConfigured: boolean;
  fromAccountNo?: string;
  hasNid: boolean;
  hasDfsAppUserId: boolean;
  levelCode?: string;
  note?: string;
  products: string[];
  raastLive: boolean;
  raastQrAvailable?: boolean;
};

export type DfsTxnResponse = {
  responsecode?: string;
  messages?: string;
  data?: unknown;
  raw?: unknown;
  fromAccountNo?: string;
  product?: string;
  /** Set by backend after live persist */
  portalTxnRef?: string;
};

export type IbftBank = {
  bankImd?: string;
  bankName?: string;
  [key: string]: unknown;
};

export type UbpBillerLive = {
  name?: string;
  code?: string;
  id?: number;
  [key: string]: unknown;
};

export function isLiveOk(r: DfsTxnResponse | null | undefined): boolean {
  return r?.responsecode === '000';
}

export async function fetchLiveStatus(token: string): Promise<LiveStatus> {
  return api<LiveStatus>('/api/transfers/live/status', { token });
}

export function banksFromResponse(r: DfsTxnResponse): IbftBank[] {
  const d = r.data;
  if (Array.isArray(d)) return d as IbftBank[];
  if (d && typeof d === 'object' && Array.isArray((d as { banks?: unknown }).banks)) {
    return (d as { banks: IbftBank[] }).banks;
  }
  if (Array.isArray(r.raw)) return r.raw as IbftBank[];
  return [];
}

export function billersFromResponse(r: DfsTxnResponse): UbpBillerLive[] {
  const d = r.data;
  if (Array.isArray(d)) return d as UbpBillerLive[];
  if (Array.isArray(r.raw)) return r.raw as UbpBillerLive[];
  return [];
}
