import { app } from "electron";
import crypto from "node:crypto";
import { promises as fs } from "node:fs";
import path from "node:path";

interface EncryptedSecret {
  salt: string;
  iv: string;
  tag: string;
  ciphertext: string;
}

export class SecretVault {
  private readonly vaultPath = path.join(app.getPath("userData"), "secrets.vault");
  private secrets: Record<string, EncryptedSecret> = {};
  private writeQueue: Promise<void> = Promise.resolve();

  async initialize() {
    try {
      const raw = await fs.readFile(this.vaultPath, "utf8");
      this.secrets = JSON.parse(raw) as Record<string, EncryptedSecret>;
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code !== "ENOENT") throw error;
    }
  }

  async set(appId: string, token: string, password: string) {
    if (!token.trim()) throw new Error("Token cannot be empty.");
    if (password.length < 8) throw new Error("Master password must be at least 8 characters.");

    const salt = crypto.randomBytes(16);
    const iv = crypto.randomBytes(12);
    const key = crypto.scryptSync(password, salt, 32);
    const cipher = crypto.createCipheriv("aes-256-gcm", key, iv);
    const ciphertext = Buffer.concat([cipher.update(token, "utf8"), cipher.final()]);
    const tag = cipher.getAuthTag();

    this.secrets[appId] = {
      salt: salt.toString("base64"),
      iv: iv.toString("base64"),
      tag: tag.toString("base64"),
      ciphertext: ciphertext.toString("base64"),
    };
    await this.persist();
  }

  decrypt(appId: string, password: string): string {
    const encrypted = this.secrets[appId];
    if (!encrypted) throw new Error("The encrypted token is missing. Save the bot token again.");
    try {
      const key = crypto.scryptSync(password, Buffer.from(encrypted.salt, "base64"), 32);
      const decipher = crypto.createDecipheriv(
        "aes-256-gcm",
        key,
        Buffer.from(encrypted.iv, "base64"),
      );
      decipher.setAuthTag(Buffer.from(encrypted.tag, "base64"));
      return Buffer.concat([
        decipher.update(Buffer.from(encrypted.ciphertext, "base64")),
        decipher.final(),
      ]).toString("utf8");
    } catch {
      throw new Error("The master password is incorrect.");
    }
  }

  async remove(appId: string) {
    delete this.secrets[appId];
    await this.persist();
  }

  async retain(appIds: string[]) {
    const allowed = new Set(appIds);
    this.secrets = Object.fromEntries(
      Object.entries(this.secrets).filter(([appId]) => allowed.has(appId)),
    );
    await this.persist();
  }

  async clear() {
    this.secrets = {};
    await this.persist();
  }

  private async persist() {
    const serialized = JSON.stringify(this.secrets);
    const write = this.writeQueue.then(async () => {
      const temporaryPath = `${this.vaultPath}.${process.pid}.tmp`;
      await fs.writeFile(temporaryPath, serialized, { encoding: "utf8", mode: 0o600 });
      await fs.rename(temporaryPath, this.vaultPath);
    });
    this.writeQueue = write.catch(() => undefined);
    await write;
  }
}
