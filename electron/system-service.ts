import { execFile } from "node:child_process";
import { promisify } from "node:util";
import si from "systeminformation";
import type { RuntimeCheck, SystemMetrics } from "../src/shared/types";

const execFileAsync = promisify(execFile);

export class SystemService {
  async metrics(): Promise<SystemMetrics> {
    const [load, memory, disks, network] = await Promise.all([
      si.currentLoad(),
      si.mem(),
      si.fsSize(),
      si.networkStats(),
    ]);
    const disk = disks.find((item) => item.mount === "C:") ?? disks.find((item) => item.mount === "/") ?? disks[0];
    return {
      cpu: load.currentLoad,
      memoryUsed: memory.active,
      memoryTotal: memory.total,
      diskUsed: disk?.used ?? 0,
      diskTotal: disk?.size ?? 0,
      networkRx: network.reduce((total, item) => total + Math.max(0, item.rx_sec ?? 0), 0),
      networkTx: network.reduce((total, item) => total + Math.max(0, item.tx_sec ?? 0), 0),
      timestamp: Date.now(),
    };
  }

  async runtimes(): Promise<RuntimeCheck[]> {
    return Promise.all([
      this.check("java", ["-version"], "https://adoptium.net/"),
      this.check("node", ["--version"], "https://nodejs.org/en/download"),
      this.check("python", ["--version"], "https://www.python.org/downloads/windows/"),
    ]);
  }

  private async check(
    runtime: RuntimeCheck["runtime"],
    args: string[],
    installUrl: string,
  ): Promise<RuntimeCheck> {
    try {
      const { stdout, stderr } = await execFileAsync(runtime, args, {
        windowsHide: true,
        timeout: 4_000,
      });
      return {
        runtime,
        installed: true,
        version: `${stdout}${stderr}`.trim().split(/\r?\n/)[0],
        installUrl,
      };
    } catch {
      return { runtime, installed: false, installUrl };
    }
  }
}
