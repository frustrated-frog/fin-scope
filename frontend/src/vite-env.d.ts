/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_WEAVRIA_AGENT_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
