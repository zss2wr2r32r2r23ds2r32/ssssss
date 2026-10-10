import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';
import { applyUpdate } from './update.js';

function releases(version) {
  return [
    {
      tag_name: `nexa-${version}`,
      assets: [{ name: 'Nexa.exe', browser_download_url: 'https://example.test/Nexa.exe' }],
    },
  ];
}

function fetchImpl(payload) {
  return async (url) => {
    if (String(url).includes('/releases')) {
      return new Response(JSON.stringify(payload), { status: 200, headers: { 'Content-Type': 'application/json' } });
    }
    return new Response(Buffer.from('MZ-updated'), { status: 200 });
  };
}

test('the latest version does not spawn a helper', async () => {
  let spawned = false;
  const status = await applyUpdate(fetchImpl(releases('0.1.5')), {
    platform: 'win32',
    exePath: 'C:\\\\Nexa\\\\Nexa.exe',
    spawn() {
      spawned = true;
      return { unref() {} };
    },
    exit() {
      throw new Error('exit');
    },
  });
  assert.equal(status.updateAvailable, false);
  assert.equal(spawned, false);
});

test('a newer Windows release downloads Nexa.exe and replaces it without a console', async () => {
  const calls = [];
  const killed = [];
  let exited = null;
  const status = await applyUpdate(fetchImpl(releases('0.1.6')), {
    platform: 'win32',
    exePath: 'C:\\\\Games\\\\Nexa.exe',
    parentPid: 4242,
    spawn(exe, args, options) {
      calls.push({ exe, args, options });
      return { unref() {} };
    },
    kill(pid) {
      killed.push(pid);
    },
    exit(code) {
      exited = code;
    },
  });
  assert.equal(status.updateAvailable, true);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].options.shell, false);
  assert.equal(calls[0].options.windowsHide, true);
  assert.equal(calls[0].options.detached, true);
  assert.equal(calls[0].options.stdio, 'ignore');
  assert.equal(calls[0].options.env.NEXA_UPDATE_DEST, 'C:\\\\Games\\\\Nexa.exe');
  assert.equal(calls[0].options.env.NEXA_UPDATE_PID, '4242');
  const helper = fs.readFileSync(calls[0].args[0], 'utf8');
  assert.match(helper, /copyFileSync/);
  assert.match(helper, /windowsHide: true/);
  assert.match(helper, /shell: false/);
  assert.doesNotMatch(helper, /cmd\.exe|\\.bat|shell:\s*true|\bstart\b/i);
  await new Promise((resolve) => setTimeout(resolve, 500));
  assert.deepEqual(killed, [4242]);
  assert.equal(exited, 0);
});

test('linux does not spawn when a newer release exists', async () => {
  let spawned = false;
  const status = await applyUpdate(fetchImpl(releases('0.9.0')), {
    platform: 'linux',
    exePath: '/opt/Nexa.exe',
    spawn() {
      spawned = true;
      return { unref() {} };
    },
  });
  assert.equal(status.updateAvailable, true);
  assert.equal(spawned, false);
  assert.match(status.message, /Nexa\.exe/);
});
