import { isDemoBrand, type ThemeMode } from '../theme/themeModes';

export type BrandPack = {
  name: string;
  short: string;
  backoffice: string;
  tagline: string;
  receiptFooter: string;
};

export const BRAND_PAYFAST: BrandPack = {
  name: 'PayFast Corporate',
  short: 'PAYFAST CORPORATE',
  backoffice: 'PAYFAST BACKOFFICE',
  tagline: 'Corporate banking portal',
  receiptFooter: 'PayFast Corporate · Keep this receipt for your records',
};

export const BRAND_TEST: BrandPack = {
  name: 'TEST Corporate',
  short: 'TEST CORPORATE',
  backoffice: 'TEST BACKOFFICE',
  tagline: 'Corporate banking portal',
  receiptFooter: 'TEST Corporate · Keep this receipt for your records',
};

/** Default / static PayFast pack (non-React call sites). Prefer `useBrand()` in UI. */
export const BRAND = BRAND_PAYFAST;

export function brandForTheme(theme: ThemeMode): BrandPack {
  return isDemoBrand(theme) ? BRAND_TEST : BRAND_PAYFAST;
}
