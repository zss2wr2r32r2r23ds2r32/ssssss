const cache = new Map();

export async function discordUserIsAdmin(discordId) {
  if (!discordId) return false;
  const cached = cache.get(discordId);
  if (cached && Date.now() - cached.at < 60_000) return cached.admin;
  let admin = false;
  try {
    const response = await fetch(
      `http://127.0.0.1:4391/admin?userId=${encodeURIComponent(discordId)}`,
      { signal: AbortSignal.timeout(2500) },
    );
    if (response.ok) {
      const data = await response.json();
      admin = Boolean(data.admin);
    }
  } catch {
    admin = false;
  }
  cache.set(discordId, { admin, at: Date.now() });
  return admin;
}
