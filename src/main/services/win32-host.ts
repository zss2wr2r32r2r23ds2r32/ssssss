import { spawn, type ChildProcessWithoutNullStreams } from 'node:child_process'
import { isWindows } from './windows-api'
import { win32Resource } from './win32-resources'

const log = {
  warn: (message: string, extra?: Record<string, unknown>) => {
    console.warn(`[win32] ${message}`, extra ?? '')
  },
}

type Pending = { resolve: (v: string) => void; reject: (e: Error) => void }

class Win32Host {
  private child: ChildProcessWithoutNullStreams | null = null
  private queue: Pending[] = []
  private buffer = ''
  private starting: Promise<void> | null = null
  private failCount = 0
  private cooldownUntil = 0
  private disabled = false

  available(): boolean {
    if (!isWindows || this.disabled) return false
    if (Date.now() < this.cooldownUntil) return false
    return Boolean(win32Resource('avix-win32-host.ps1'))
  }

  async ensure(): Promise<boolean> {
    if (!this.available()) return false
    if (this.child && !this.child.killed) return true
    if (this.starting) {
      await this.starting
      return Boolean(this.child)
    }
    this.starting = this.spawnHost()
    try {
      await this.starting
    } finally {
      this.starting = null
    }
    return Boolean(this.child)
  }

  private markFailure(): void {
    this.failCount += 1
    this.cooldownUntil = Date.now() + Math.min(120_000, 30_000 * this.failCount)
    if (this.failCount >= 2) this.disabled = true
    this.child = null
  }

  private spawnHost(): Promise<void> {
    return new Promise((resolve) => {
      const scriptPath = win32Resource('avix-win32-host.ps1')
      if (!scriptPath) {
        this.markFailure()
        resolve()
        return
      }
      try {
        const child = spawn(
          'powershell.exe',
          ['-NoProfile', '-STA', '-ExecutionPolicy', 'Bypass', '-File', scriptPath],
          { windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] },
        )
        this.child = child
        child.stdout.setEncoding('utf8')
        child.stderr.setEncoding('utf8')
        child.stdout.on('data', (chunk: string) => {
          this.buffer += chunk
          let idx = this.buffer.indexOf('\n')
          while (idx >= 0) {
            const line = this.buffer.slice(0, idx).replace(/\r$/, '')
            this.buffer = this.buffer.slice(idx + 1)
            if (line) this.settle(line)
            idx = this.buffer.indexOf('\n')
          }
        })
        child.stderr.on('data', (chunk: string) => {
          const text = String(chunk).trim()
          if (text) log.warn('host stderr', { text: text.slice(0, 240) })
        })
        child.on('exit', () => {
          this.child = null
          const leftover = this.queue.splice(0)
          for (const p of leftover) p.reject(new Error('win32 host exited'))
          if (leftover.length) this.markFailure()
        })
        const timer = setTimeout(() => {
          if (!this.child) this.markFailure()
          resolve()
        }, 8000)
        this.queue.push({
          resolve: () => {
            clearTimeout(timer)
            this.failCount = 0
            resolve()
          },
          reject: () => {
            clearTimeout(timer)
            this.markFailure()
            resolve()
          },
        })
        child.stdin.write('PING\n')
      } catch (error) {
        log.warn('win32 host spawn failed', { error: String(error) })
        this.markFailure()
        resolve()
      }
    })
  }

  private settle(line: string): void {
    const pending = this.queue.shift()
    if (!pending) return
    if (line.startsWith('ERR ')) pending.reject(new Error(line.slice(4)))
    else pending.resolve(line.startsWith('OK ') ? line.slice(3) : line)
  }

  async request(line: string, timeoutMs = 4000): Promise<string> {
    if (!(await this.ensure()) || !this.child) throw new Error('win32 host unavailable')
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        const i = this.queue.indexOf(entry)
        if (i >= 0) this.queue.splice(i, 1)
        reject(new Error('win32 host timeout'))
      }, timeoutMs)
      const entry: Pending = {
        resolve: (v) => {
          clearTimeout(timer)
          resolve(v)
        },
        reject: (e) => {
          clearTimeout(timer)
          reject(e)
        },
      }
      this.queue.push(entry)
      this.child!.stdin.write(`${line}\n`)
    })
  }

  dispose(): void {
    try {
      this.child?.stdin.write('QUIT\n')
    } catch {
      /* ignore */
    }
    this.child?.kill()
    this.child = null
  }
}

const host = new Win32Host()

export function isWin32HostAvailable(): boolean {
  return host.available()
}

export async function hostSendKey(vk: number, down: boolean): Promise<void> {
  await host.request(`KEY|${vk}|${down ? 1 : 0}`, 1500)
}

export async function hostMouse(flags: number, data = 0): Promise<void> {
  await host.request(`MOUSE|${flags}|${data}`, 1500)
}

export async function hostWheel(delta: number): Promise<void> {
  await hostMouse(0x0800, delta)
}

export async function hostClick(button: 'left' | 'right' | 'middle' = 'left'): Promise<void> {
  const down = button === 'right' ? 0x0008 : button === 'middle' ? 0x0020 : 0x0002
  const up = button === 'right' ? 0x0010 : button === 'middle' ? 0x0040 : 0x0004
  await hostMouse(down)
  await hostMouse(up)
}

export async function hostFocus(): Promise<{ pid: number; hwnd: string; className: string; processName: string }> {
  const raw = await host.request('FOCUS', 1500)
  const [pid, hwnd, className, processName] = raw.split('|')
  return {
    pid: Number(pid) || 0,
    hwnd: hwnd || '0',
    className: className || '',
    processName: processName || '',
  }
}

export async function hostFortniteRect(): Promise<{ left: number; top: number; right: number; bottom: number } | null> {
  const raw = await host.request('FNRECT', 2500)
  if (!raw || !raw.includes(',')) return null
  const [left, top, right, bottom] = raw.split(',').map(Number)
  if (![left, top, right, bottom].every((n) => Number.isFinite(n))) return null
  return { left, top, right, bottom }
}

export async function hostHideEpic(): Promise<void> {
  try {
    if (!host.available()) return
    await host.request('HIDEEPIC', 2500)
  } catch {
    /* optional */
  }
}

export function disposeWin32Host(): void {
  host.dispose()
}
