export type TransferProduct = 'FT' | 'IBFT' | 'UBP' | 'RAAST';
export type BeneficiaryRail = 'FT' | 'IBFT' | 'FT_IBFT' | 'RAAST';

export type MockTransfer = {
  id: number;
  publicId: string;
  productType: TransferProduct;
  mode: 'SINGLE' | 'BULK';
  status: string;
  mockTxnRef: string;
  accountNumber?: string;
  ipin?: string;
  bankName?: string;
  amount?: number;
  cnic?: string;
  mobile?: string;
  beneficiaryName?: string;
  notes?: string;
  bulkFileName?: string;
  bulkRowCount?: number;
  bulkSummary?: string;
  raastQrPayload?: string;
  raastQrDataUrl?: string;
  ubpCategory?: string;
  ubpCompany?: string;
  consumerNumber?: string;
  billingMonth?: string;
  billDueDate?: string;
  createdAt?: string;
};

export type Beneficiary = {
  id: number;
  publicId: string;
  aliasName: string;
  fullName: string;
  accountNumber?: string;
  bankName?: string;
  raastId?: string;
  mobile?: string;
  cnic?: string;
  railScope: BeneficiaryRail;
  active: boolean;
  notes?: string;
  createdAt?: string;
  updatedAt?: string;
};

export type UbpBiller = { code: string; name: string };
export type UbpCategory = {
  code: string;
  label: string;
  consumerLabel: string;
  companies: UbpBiller[];
};
export type UbpBill = {
  customerName?: string;
  billingMonth?: string;
  dueDate?: string;
  dueAmount?: number;
  companyName?: string;
  consumerNumber?: string;
  categoryLabel?: string;
  message?: string;
};

export const TRANSFER_PRODUCTS: {
  id: TransferProduct;
  path: string;
  title: string;
  short: string;
  blurb: string;
  bulk: boolean;
}[] = [
  {
    id: 'FT',
    path: '/transfers/ft',
    title: 'Fund Transfer',
    short: 'FT',
    blurb: 'Move funds to accounts within the same network (mock).',
    bulk: true,
  },
  {
    id: 'IBFT',
    path: '/transfers/ibft',
    title: 'Interbank Transfer',
    short: 'IBFT',
    blurb: 'Send to other banks in Pakistan (mock).',
    bulk: true,
  },
  {
    id: 'UBP',
    path: '/transfers/ubp',
    title: 'Utility Bill Payment',
    short: 'UBP',
    blurb: 'Electricity, gas, water, internet, mobile & tickets (mock).',
    bulk: true,
  },
  {
    id: 'RAAST',
    path: '/transfers/raast',
    title: 'Raast',
    short: 'Raast',
    blurb: 'Receive via live Raast QR from your corporate wallet.',
    bulk: false,
  },
];

export function railLabel(rail: BeneficiaryRail): string {
  switch (rail) {
    case 'FT':
      return 'FT only';
    case 'IBFT':
      return 'IBFT only';
    case 'FT_IBFT':
      return 'FT & IBFT';
    case 'RAAST':
      return 'Raast';
    default:
      return rail;
  }
}
