/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_BANKING_DEMO?: string;
  readonly VITE_BANKING_ANALYST_ENABLED?: string;
  readonly VITE_API_BASE_URL?: string;
  readonly VITE_DEMO_MODE?: string;
}

declare const __PORTFOLIO_BUILD__: boolean;
declare const __BANKING_LIVE_MODE__: boolean;

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
interface ImportMetaEnv { readonly VITE_API_BASE_URL?: string }
interface ImportMeta { readonly env: ImportMetaEnv }
