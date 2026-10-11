export function visibleName(discordName: string, displayName: string) {
  const discord = discordName.trim();
  const display = displayName.trim();
  if (display && discord && display.toLowerCase() !== discord.toLowerCase()) return display;
  return discord || display;
}
