import crypto from 'node:crypto';

export const QUEUE_BATCH = 3;
export const QUEUE_INTERVAL_MS = 2800;
export const QUEUE_STALE_MS = 4500;

function compareClients(a, b) {
  return a.joinedAt - b.joinedAt || a.seq - b.seq;
}

export function createQueue(options = {}) {
  const now = options.now || (() => Date.now());
  const batch = options.batch || QUEUE_BATCH;
  const interval = options.intervalMs || QUEUE_INTERVAL_MS;
  const staleMs = options.staleMs || QUEUE_STALE_MS;
  const clients = new Map();
  let nextWaveAt = null;
  let sequence = 0;

  function waiting() {
    return [...clients.values()].filter((client) => !client.admittedAt).sort(compareClients);
  }

  function prune(t) {
    for (const [id, client] of clients) {
      if (client.admittedAt) {
        if (t - client.admittedAt > 15000) clients.delete(id);
      } else if (t - client.seenAt > staleMs) {
        clients.delete(id);
      }
    }
  }

  function admit(t) {
    prune(t);
    let line = waiting();
    if (!line.length) {
      nextWaveAt = null;
      return;
    }
    if (nextWaveAt == null) nextWaveAt = line[0].joinedAt + interval;
    let guard = 0;
    while (t >= nextWaveAt && guard < 100) {
      line = waiting();
      if (!line.length) {
        nextWaveAt = null;
        return;
      }
      const wave = line.slice(0, batch);
      const count = line.length;
      for (let offset = 0; offset < wave.length; offset += 1) {
        wave[offset].admittedAt = t;
        wave[offset].spotAtAdmit = offset + 1;
        wave[offset].inLineAtAdmit = count;
      }
      nextWaveAt += interval;
      guard += 1;
    }
    if (!waiting().length) nextWaveAt = null;
  }

  function view(client, t) {
    admit(t);
    const fresh = clients.get(client.id);
    if (!fresh) return null;
    if (fresh.admittedAt) {
      return {
        id: fresh.id,
        spot: fresh.spotAtAdmit,
        inLine: fresh.inLineAtAdmit,
        estimateSeconds: 0,
        progress: 1,
        admitted: true,
        headline: "You're next",
      };
    }
    const line = waiting();
    const index = line.findIndex((row) => row.id === fresh.id);
    if (index < 0) return null;
    if (nextWaveAt == null) nextWaveAt = line[0].joinedAt + interval;
    const wavesNow = Math.floor(index / batch);
    const admitAt = nextWaveAt + wavesNow * interval;
    const waveFraction = Math.max(0, Math.min(1, 1 - (nextWaveAt - t) / interval));
    const progress = Math.max(0, Math.min(1, (fresh.wavesAtJoin - wavesNow + waveFraction) / (fresh.wavesAtJoin + 1)));
    const estimateSeconds = Math.max(0, Math.ceil((admitAt - t) / 1000));
    const spot = index + 1;
    return {
      id: fresh.id,
      spot,
      inLine: line.length,
      estimateSeconds,
      progress,
      admitted: false,
      headline: spot === 1 ? "You're next" : "You're in line",
    };
  }

  return {
    join() {
      const t = now();
      admit(t);
      const line = waiting();
      const id = crypto.randomBytes(16).toString('hex');
      const client = {
        id,
        joinedAt: t,
        seq: sequence,
        seenAt: t,
        admittedAt: null,
        spotAtAdmit: 1,
        inLineAtAdmit: 1,
        wavesAtJoin: Math.floor(line.length / batch),
      };
      sequence += 1;
      clients.set(id, client);
      if (nextWaveAt == null) nextWaveAt = t + interval;
      return view(client, t);
    },
    touch(id) {
      if (typeof id !== 'string' || !clients.has(id)) return null;
      const client = clients.get(id);
      client.seenAt = now();
      return view(client, now());
    },
    leave(id) {
      clients.delete(id);
      admit(now());
    },
  };
}

export const queue = createQueue();
