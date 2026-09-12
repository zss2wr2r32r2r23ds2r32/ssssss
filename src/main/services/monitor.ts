import os from 'node:os'
import { screen } from 'electron'
import type { MonitorSnapshot } from '../../shared/types'
import { getLaunchStatus, getTrackedPid, getUptimeSec } from './fortnite'
import { detectGpu } from './resolution'

let lastCpu = os.cpus().map((cpu) => cpu.times)
let lastAppCpu = process.cpuUsage()
let lastAppAt = Date.now()

function cpuLoad(): number {
  const current = os.cpus()
  let idle = 0
  let total = 0
  current.forEach((cpu, index) => {
    const prev = lastCpu[index] ?? cpu.times
    const idleDelta = cpu.times.idle - prev.idle
    const totalDelta =
      cpu.times.user + cpu.times.nice + cpu.times.sys + cpu.times.irq + cpu.times.idle -
      (prev.user + prev.nice + prev.sys + prev.irq + prev.idle)
    idle += idleDelta
    total += totalDelta
  })
  lastCpu = current.map((cpu) => cpu.times)
  if (total <= 0) return 0
  return Math.max(0, Math.min(100, Math.round((1 - idle / total) * 100)))
}

export async function snapshot(): Promise<MonitorSnapshot> {
  const display = screen.getPrimaryDisplay()
  const total = os.totalmem()
  const free = os.freemem()
  const gpu = await detectGpu()
  const now = Date.now()
  const usage = process.cpuUsage(lastAppCpu)
  const elapsed = Math.max(1, now - lastAppAt)
  lastAppCpu = process.cpuUsage()
  lastAppAt = now
  const appCpu = Math.max(0, Math.min(100, Math.round(((usage.user + usage.system) / 1000 / elapsed) * 100)))
  return {
    cpuLoad: cpuLoad(),
    cpuTemp: null,
    gpuLoad: null,
    gpuTemp: null,
    gpuName: gpu.name,
    ramUsedMb: Math.round((total - free) / 1024 / 1024),
    ramTotalMb: Math.round(total / 1024 / 1024),
    appCpu,
    appRssMb: Math.round(process.memoryUsage().rss / 1024 / 1024),
    resolution: { width: display.size.width, height: display.size.height },
    fortnite: {
      status: getLaunchStatus(),
      pid: getTrackedPid(),
      uptimeSec: getUptimeSec()
    },
    timestamp: Date.now()
  }
}
