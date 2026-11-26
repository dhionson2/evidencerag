import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  const portfolioBuild = mode === 'portfolio' || mode === 'pages';
  return {
    base: mode === 'pages' ? '/evidencerag/' : '/',
    plugins: [react()],
    define: {
      __PORTFOLIO_BUILD__: JSON.stringify(portfolioBuild),
      __BANKING_LIVE_MODE__: JSON.stringify(mode === 'live'),
    },
    server: { port: 5173, proxy: { '/api': 'http://localhost:8080' } },
    test: { environment: 'jsdom', setupFiles: './src/test-setup.ts' },
  };
});
