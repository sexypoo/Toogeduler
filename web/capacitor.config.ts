import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'app.toogeduler.mobile',
  appName: 'Toogeduler',
  // A placeholder directory is required by Capacitor even though the shell
  // loads the verified production site below.
  webDir: 'public',
  server: {
    url: process.env.CAPACITOR_SERVER_URL || 'https://app.toogeduler.com',
    androidScheme: 'https'
  }
};

export default config;
