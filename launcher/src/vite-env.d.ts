/// <reference types="vite/client" />

interface NexaBridge {
  minimize: () => void;
  maximize: () => void;
  close: () => void;
}

interface Window {
  nexa?: NexaBridge;
}
