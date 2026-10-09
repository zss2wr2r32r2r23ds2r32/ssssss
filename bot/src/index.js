import path from 'node:path';
import { fileURLToPath } from 'node:url';
import dotenv from 'dotenv';
import { Client, GatewayIntentBits, REST, Routes, SlashCommandBuilder } from 'discord.js';

dotenv.config({ path: path.join(path.dirname(fileURLToPath(import.meta.url)), '..', '.env') });

const token = process.env.DISCORD_TOKEN?.trim();
const apiBase = (process.env.LAUNCHER_API_URL || 'http://127.0.0.1:4177').replace(/\/$/, '');

if (!token) {
  console.log(
    '[nexa-bot] DISCORD_TOKEN is missing. The bot is idle and will not connect. Add a token to bot/.env when you want slash commands. The launcher keeps running without it.',
  );
  process.exit(0);
}

const commands = [
  new SlashCommandBuilder().setName('stats').setDescription('Show the linked Nexa profile stats'),
  new SlashCommandBuilder().setName('shop').setDescription('Show Nexa shop items and the next 01:00 UK refresh'),
  new SlashCommandBuilder().setName('link').setDescription('Link this Discord user to the local Nexa admin profile'),
].map((command) => command.toJSON());

async function api(pathname) {
  const response = await fetch(`${apiBase}${pathname}`);
  if (!response.ok) throw new Error(`${pathname} returned ${response.status}`);
  return response.json();
}

function formatDuration(seconds) {
  const s = Math.max(0, seconds);
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  return [h, m, sec].map((n) => String(n).padStart(2, '0')).join(':');
}

const client = new Client({ intents: [GatewayIntentBits.Guilds] });

client.once('ready', async () => {
  console.log(`[nexa-bot] Logged in as ${client.user.tag}`);
  const appId = process.env.DISCORD_CLIENT_ID?.trim() || client.application?.id;
  if (!appId) {
    console.log('[nexa-bot] No DISCORD_CLIENT_ID. Commands were not registered.');
    return;
  }
  const rest = new REST({ version: '10' }).setToken(token);
  const guildId = process.env.DISCORD_GUILD_ID?.trim();
  const route = guildId
    ? Routes.applicationGuildCommands(appId, guildId)
    : Routes.applicationCommands(appId);
  try {
    await rest.put(route, { body: commands });
    console.log(`[nexa-bot] Registered /stats /shop /link (${guildId ? `guild ${guildId}` : 'global'}).`);
    if (!guildId) console.log('[nexa-bot] Global commands can take up to an hour to show up.');
  } catch (error) {
    console.error('[nexa-bot] Command registration failed:', error.message);
  }
});

client.on('interactionCreate', async (interaction) => {
  if (!interaction.isChatInputCommand()) return;
  try {
    if (interaction.commandName === 'stats') {
      const stats = await api('/stats');
      const me = await api('/me');
      await interaction.reply(
        `**${me.displayName}** · elims ${stats.elims.toLocaleString('en-US')} · wins ${stats.wins.toLocaleString('en-US')} · matches ${stats.matches.toLocaleString('en-US')} · vbucks ${stats.vbucks.toLocaleString('en-US')}`,
      );
      return;
    }
    if (interaction.commandName === 'shop') {
      const [refresh, shop] = await Promise.all([api('/shop/refresh'), api('/shop/items')]);
      const preview = shop.items
        .slice(0, 4)
        .map((item) => `${item.name} (${item.vbucks === 0 ? 'Free' : item.vbucks.toLocaleString('en-US')})`)
        .join(', ');
      await interaction.reply(
        `Shop refreshes at 01:00 Europe/London · ${formatDuration(refresh.seconds)} left.\n${preview}`,
      );
      return;
    }
    if (interaction.commandName === 'link') {
      const me = await api('/me');
      await interaction.reply(
        `Linked to **${me.displayName}** (${me.discordName}). Discord ID \`${me.discordId}\`.`,
      );
      return;
    }
  } catch (error) {
    const content = 'Nexa API is not reachable. Start it with npm run dev and try again.';
    if (interaction.deferred || interaction.replied) {
      await interaction.followUp({ content, ephemeral: true });
    } else {
      await interaction.reply({ content, ephemeral: true });
    }
    console.error('[nexa-bot]', interaction.commandName, error.message);
  }
});

client.login(token).catch((error) => {
  console.error('[nexa-bot] Login failed:', error.message);
  process.exit(1);
});
