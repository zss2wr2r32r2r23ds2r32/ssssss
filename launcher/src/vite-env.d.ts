/// <reference types="vite/client" />

interface NexaBridge {
  minimize: () => void;
  maximize: () => void;
  close: () => void;
  apiBase?: string;
  filePath?: (file: File) => string;
  openExternal?: (url: string) => Promise<void>;
}

interface Window {
  nexa?: NexaBridge;
}
