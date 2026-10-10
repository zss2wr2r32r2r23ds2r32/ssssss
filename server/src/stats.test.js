import assert from 'node:assert/strict';
import test from 'node:test';
import { applyZeroStats, emptyStats } from './store.js';

test('launcher stats and leaderboard rows start at zero', () => {
  const parsed = {
    user: { stats: { elims: 4821, wins: 186, matches: 974, vbucks: 1350 } },
    rivals: [
      { id: 'vanta', discordName: 'Vanta', wins: 640, elims: 22110 },
      { id: 'kite', discordName: 'Kite', wins: 0, elims: 0 },
    ],
  };
  assert.equal(applyZeroStats(parsed), true);
  assert.deepEqual(parsed.user.stats, emptyStats());
  assert.deepEqual(
    parsed.rivals.map((rival) => [rival.discordName, rival.wins, rival.elims, rival.wins * 100 + rival.elims]),
    [
      ['Vanta', 0, 0, 0],
      ['Kite', 0, 0, 0],
    ],
  );
  assert.equal(applyZeroStats(parsed), false);
});
