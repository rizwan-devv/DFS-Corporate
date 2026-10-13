import { useTheme } from '../theme/ThemeContext';
import { brandForTheme, type BrandPack } from './brand';

export function useBrand(): BrandPack {
  const { theme } = useTheme();
  return brandForTheme(theme);
}
