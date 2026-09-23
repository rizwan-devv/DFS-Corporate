import type { UbpBillerLive } from './liveTransfers';

export type UbpUiCategory = {
  id: string;
  label: string;
  blurb: string;
  /** Match against biller name/code (case-insensitive) */
  keywords: string[];
};

/** Cash-Management–style categories; live DFS billers are grouped by keyword. */
export const UBP_UI_CATEGORIES: UbpUiCategory[] = [
  {
    id: 'UTILITY',
    label: 'Utility Bills',
    blurb: 'Electricity, gas & water',
    keywords: [
      'electric', 'lesco', 'kepco', 'kesc', 'hesco', 'gepco', 'fesco', 'iesco', 'mepco', 'pesco', 'qesco', 'sepco', 'tesco',
      'gas', 'sngpl', 'ssgc', 'water', 'wasa', 'kwsb', 'utility',
    ],
  },
  {
    id: 'TELCO',
    label: 'Mobile / Telco',
    blurb: 'Prepaid & postpaid mobile',
    keywords: ['jazz', 'telenor', 'ufone', 'zong', 'mobile', 'telco', 'prepaid', 'postpaid', 'warid'],
  },
  {
    id: 'INTERNET',
    label: 'Internet / Broadband',
    blurb: 'ISP, PTCL & cable TV',
    keywords: ['nayatel', 'ptcl', 'stormfiber', 'optic', 'internet', 'broadband', 'wifi', 'dsl', 'fiber', 'cable', 'dth', 'tv'],
  },
  {
    id: 'EDUCATION',
    label: 'Education',
    blurb: 'Schools, colleges & fees',
    keywords: ['school', 'college', 'university', 'education', 'fee', 'beacon', 'lums', 'nust'],
  },
  {
    id: 'GOVERNMENT',
    label: 'Government',
    blurb: 'Taxes, challans & fees',
    keywords: ['fbr', 'tax', 'excise', 'government', 'govt', 'challan', 'psid', 'nadra', 'passport', 'traffic'],
  },
  {
    id: 'INSURANCE',
    label: 'Insurance',
    blurb: 'Life & health premiums',
    keywords: ['insurance', 'takaful', 'efu', 'jubilee', 'adamjee', 'health', 'life'],
  },
  {
    id: 'OTHER',
    label: 'Other billers',
    blurb: 'Everything else from DFS',
    keywords: [],
  },
];

export function billerText(b: UbpBillerLive): string {
  return `${b.name || ''} ${b.code || ''}`.toLowerCase();
}

export function categoryForBiller(b: UbpBillerLive): string {
  const text = billerText(b);
  for (const cat of UBP_UI_CATEGORIES) {
    if (cat.id === 'OTHER') continue;
    if (cat.keywords.some((k) => text.includes(k))) return cat.id;
  }
  return 'OTHER';
}

export function billersInCategory(billers: UbpBillerLive[], categoryId: string): UbpBillerLive[] {
  return billers.filter((b) => categoryForBiller(b) === categoryId);
}

export function categoryCounts(billers: UbpBillerLive[]): Record<string, number> {
  const counts: Record<string, number> = {};
  for (const cat of UBP_UI_CATEGORIES) counts[cat.id] = 0;
  for (const b of billers) {
    const id = categoryForBiller(b);
    counts[id] = (counts[id] || 0) + 1;
  }
  return counts;
}

export type LiveUbpStep = 'category' | 'company' | 'billId' | 'fetch' | 'pay';

export const LIVE_UBP_STEPS: { id: LiveUbpStep; label: string }[] = [
  { id: 'category', label: 'Category' },
  { id: 'company', label: 'Company' },
  { id: 'billId', label: 'Bill ID' },
  { id: 'fetch', label: 'Fetch' },
  { id: 'pay', label: 'Pay' },
];
