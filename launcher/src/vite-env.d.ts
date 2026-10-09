/// <reference types="vite/client" />

interface NexaBridge {
  minimize: () => void;
  maximize: () => void;
  close: () => void;
  apiBase?: string;
}

interface Window {
  nexa?: NexaBridge;
}
