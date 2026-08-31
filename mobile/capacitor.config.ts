import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.dfscorporate.kyc',
  appName: 'DFS Corporate KYC',
  webDir: 'dist',
  server: {
    androidScheme: 'http',
  },
};

export default config;
