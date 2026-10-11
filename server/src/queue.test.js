import assert from 'node:assert/strict';
import test from 'node:test';
import { createQueue } from './queue.js';

test('one connected launcher is spot 1 and the bar fills before they are let in', () => {
  let t = 1_000_000;
  const queue = createQueue({ now: () => t });
  const joined = queue.join();
  assert.equal(joined.spot, 1);
  assert.equal(joined.inLine, 1);
  assert.equal(joined.admitted, false);
  assert.equal(joined.headline, "You're next");
  assert.equal(joined.progress, 0);
  assert.equal(joined.estimateSeconds, 3);

  t += 1400;
  const mid = queue.touch(joined.id);
  assert.equal(mid.spot, 1);
  assert.equal(mid.inLine, 1);
  assert.equal(mid.admitted, false);
  assert.ok(mid.progress > 0.45 && mid.progress < 0.55);
  assert.equal(mid.estimateSeconds, 2);

  t += 1400;
  const done = queue.touch(joined.id);
  assert.equal(done.admitted, true);
  assert.equal(done.progress, 1);
  assert.equal(done.spot, 1);
  assert.equal(done.inLine, 1);
  assert.equal(done.estimateSeconds, 0);
  assert.equal(done.headline, "You're next");
});

test('only connected clients count, and a few are let in at a time', () => {
  let t = 5_000_000;
  const queue = createQueue({ now: () => t });
  const first = queue.join();
  const second = queue.join();
  const third = queue.join();
  const fourth = queue.join();
  assert.deepEqual(
    [first.spot, second.spot, third.spot, fourth.spot],
    [1, 2, 3, 4],
  );
  assert.equal(fourth.inLine, 4);
  assert.equal(fourth.headline, "You're in line");
  assert.equal(fourth.estimateSeconds, 6);
  assert.ok(fourth.progress < first.progress + 0.001);

  t += 1400;
  const behind = queue.touch(fourth.id);
  assert.ok(behind.progress > 0.2 && behind.progress < 0.3);
  assert.equal(behind.admitted, false);

  t = 5_000_000 + 2800;
  assert.equal(queue.touch(first.id).admitted, true);
  assert.equal(queue.touch(second.id).admitted, true);
  assert.equal(queue.touch(third.id).admitted, true);
  const nowFirst = queue.touch(fourth.id);
  assert.equal(nowFirst.admitted, false);
  assert.equal(nowFirst.spot, 1);
  assert.equal(nowFirst.inLine, 1);
  assert.equal(nowFirst.headline, "You're next");
  assert.ok(nowFirst.progress > behind.progress);
  assert.ok(nowFirst.progress < 1);

  t += 2800;
  const entered = queue.touch(fourth.id);
  assert.equal(entered.admitted, true);
  assert.equal(entered.progress, 1);
});

test('moving up the line fills the bar', () => {
  let t = 8_000_000;
  const queue = createQueue({ now: () => t });
  const ahead = queue.join();
  queue.join();
  queue.join();
  const last = queue.join();
  t += 1400;
  const before = queue.touch(last.id);
  queue.leave(ahead.id);
  const after = queue.touch(last.id);
  assert.equal(after.spot, 3);
  assert.equal(after.inLine, 3);
  assert.equal(after.headline, "You're in line");
  assert.ok(after.progress > before.progress + 0.2);
  assert.ok(after.estimateSeconds < before.estimateSeconds);
});

test('a client who stops checking in leaves the line', () => {
  let t = 2_000_000;
  const queue = createQueue({ now: () => t });
  const quiet = queue.join();
  const active = queue.join();
  t += 4501;
  const status = queue.touch(active.id);
  assert.equal(status.spot, 1);
  assert.equal(status.inLine, 1);
  assert.equal(queue.touch(quiet.id), null);
});
