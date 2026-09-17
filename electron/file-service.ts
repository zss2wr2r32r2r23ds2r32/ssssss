import { promises as fs } from "node:fs";
import path from "node:path";
import type { FileEntry } from "../src/shared/types";
import type { StorageService } from "./storage";

const TEXT_EXTENSIONS = new Set([
  ".txt",
  ".log",
  ".yml",
  ".yaml",
  ".json",
  ".properties",
  ".js",
  ".cjs",
  ".mjs",
  ".ts",
  ".tsx",
  ".jsx",
  ".py",
  ".md",
  ".xml",
  ".toml",
  ".ini",
  ".env",
  ".sh",
  ".bat",
  ".ps1",
  ".html",
  ".css",
]);

export class FileService {
  constructor(private readonly storage: StorageService) {}

  async list(appId: string, relativePath = ""): Promise<FileEntry[]> {
    const target = this.resolvePath(appId, relativePath);
    const entries = await fs.readdir(target, { withFileTypes: true });
    const result = await Promise.all(
      entries
        .filter((entry) => !entry.name.startsWith(".process-manager"))
        .map(async (entry): Promise<FileEntry> => {
          const fullPath = path.join(target, entry.name);
          const stat = await fs.stat(fullPath);
          return {
            name: entry.name,
            path: path.relative(this.getRoot(appId), fullPath).replaceAll("\\", "/"),
            type: entry.isDirectory() ? "directory" : "file",
            size: stat.size,
            modifiedAt: stat.mtime.toISOString(),
          };
        }),
    );
    return result.sort((a, b) => {
      if (a.type !== b.type) return a.type === "directory" ? -1 : 1;
      return a.name.localeCompare(b.name);
    });
  }

  async readText(appId: string, relativePath: string) {
    const target = this.resolvePath(appId, relativePath);
    const stat = await fs.stat(target);
    if (!stat.isFile()) throw new Error("Choose a text file to edit.");
    if (stat.size > 2 * 1024 * 1024) throw new Error("The editor supports files up to 2 MB.");
    const extension = path.extname(target).toLowerCase();
    if (!TEXT_EXTENSIONS.has(extension) && path.basename(target) !== ".env") {
      throw new Error("This file type cannot be opened in the text editor.");
    }
    return fs.readFile(target, "utf8");
  }

  async writeText(appId: string, relativePath: string, content: string) {
    if (Buffer.byteLength(content, "utf8") > 2 * 1024 * 1024) {
      throw new Error("The editor supports files up to 2 MB.");
    }
    const target = this.resolvePath(appId, relativePath);
    await fs.writeFile(target, content, "utf8");
  }

  async createFolder(appId: string, relativeParent: string, name: string) {
    this.validateName(name);
    await fs.mkdir(this.resolvePath(appId, path.join(relativeParent, name)), { recursive: false });
  }

  async rename(appId: string, relativePath: string, name: string) {
    this.validateName(name);
    const source = this.resolvePath(appId, relativePath);
    const destination = path.join(path.dirname(source), name);
    await fs.rename(source, destination);
  }

  async delete(appId: string, relativePath: string) {
    if (!relativePath) throw new Error("The application root cannot be deleted.");
    await fs.rm(this.resolvePath(appId, relativePath), { recursive: true, force: false });
  }

  async search(appId: string, query: string) {
    const root = this.getRoot(appId);
    const lowered = query.trim().toLowerCase();
    if (!lowered) return [];
    const matches: FileEntry[] = [];
    const walk = async (directory: string) => {
      if (matches.length >= 100) return;
      const entries = await fs.readdir(directory, { withFileTypes: true });
      for (const entry of entries) {
        if (matches.length >= 100) break;
        const fullPath = path.join(directory, entry.name);
        if (entry.name.toLowerCase().includes(lowered)) {
          const stat = await fs.stat(fullPath);
          matches.push({
            name: entry.name,
            path: path.relative(root, fullPath).replaceAll("\\", "/"),
            type: entry.isDirectory() ? "directory" : "file",
            size: stat.size,
            modifiedAt: stat.mtime.toISOString(),
          });
        }
        if (entry.isDirectory() && !entry.isSymbolicLink()) await walk(fullPath);
      }
    };
    await walk(root);
    return matches;
  }

  async copyInto(appId: string, relativeDirectory: string, sourcePaths: string[]) {
    const destination = this.resolvePath(appId, relativeDirectory);
    await Promise.all(
      sourcePaths.map((source) =>
        fs.cp(source, path.join(destination, path.basename(source)), {
          recursive: true,
          errorOnExist: true,
        }),
      ),
    );
  }

  async copyOut(appId: string, relativePath: string, destination: string) {
    const source = this.resolvePath(appId, relativePath);
    const stat = await fs.stat(source);
    if (!stat.isFile()) throw new Error("Only files can be downloaded.");
    await fs.copyFile(source, destination);
  }

  private getRoot(appId: string) {
    const config = this.storage.getApp(appId);
    if (!config?.directory) throw new Error("Choose an application directory first.");
    return path.resolve(config.directory);
  }

  private resolvePath(appId: string, relativePath: string) {
    const root = this.getRoot(appId);
    const target = path.resolve(root, relativePath || ".");
    if (target !== root && !target.startsWith(`${root}${path.sep}`)) {
      throw new Error("That path is outside the application directory.");
    }
    return target;
  }

  private validateName(name: string) {
    if (!name.trim() || name === "." || name === ".." || /[<>:"/\\|?*]/.test(name)) {
      throw new Error("Enter a valid file or folder name.");
    }
  }
}
