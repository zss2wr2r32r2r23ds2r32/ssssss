/// <reference types="vite/client" />

export type AiPayload = {
  baseUrl: string;
  apiKey: string;
  model: string;
  instruction: string;
  yaml: string;
  accent: string;
  secondary: string;
};

declare global {
  interface Window {
    avix?: {
      isElectron: true;
      getStore: () => Promise<unknown>;
      setStore: (data: unknown) => Promise<boolean>;
      copyText: (text: string) => Promise<boolean>;
      completeAi: (payload: AiPayload) => Promise<string>;
    };
  }
}

export {};
