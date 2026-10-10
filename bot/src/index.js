import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import dotenv from 'dotenv';
import { Client, GatewayIntentBits, REST, Routes, SlashCommandBuilder } from 'discord.js';

dotenv.config({ path: path.join(path.dirname(fileURLToPath(import.meta.url)), '..', '.env') });

const token = process.env.DISCORD_TOKEN?.trim();
const adminRoleId = process.env.ADMIN_ROLE_ID?.trim() || '';
const apiBase = (process.env.LAUNCHER_API_URL || 'http://127.0.0.1:4177').replace(/\/$/, '');

if (!token) {
  console.log(
    '[nexa-bot] DISCORD_TOKEN is missing. The bot is idle and will not connect. Add a token to bot/.env when you want slash commands. The launcher keeps running without it.',
  );
  process.exit(0);
}

const commands = [
  new SlashCommandBuilder().setName('login').setDescription('Finish Nexa sign-in as this Discord user'),
  new SlashCommandBuilder().setName('stats').setDescription('Show the linked Nexa profile stats'),
  new SlashCommandBuilder().setName('shop').setDescription('Show Nexa shop items and the next 01:00 UK refresh'),
  new SlashCommandBuilder().setName('link').setDescription('Show the Discord user currently signed in to Nexa'),
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
    console.log(`[nexa-bot] Registered /login /stats /shop /link (${guildId ? `guild ${guildId}` : 'global'}).`);
    if (!guildId) console.log('[nexa-bot] Global commands can take up to an hour to show up.');
  } catch (error) {
    console.error('[nexa-bot] Command registration failed:', error.message);
  }
});

client.on('interactionCreate', async (interaction) => {
  if (!interaction.isChatInputCommand()) return;
  try {
    if (interaction.commandName === 'login') {
      const response = await fetch(`${apiBase}/auth/discord/bot`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          id: interaction.user.id,
          username: interaction.user.username,
          avatar: interaction.user.displayAvatarURL({ size: 128, extension: 'png' }),
        }),
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) {
        await interaction.reply({
          content: data.error || 'Click Continue with Discord in Nexa first, then run /login again.',
          ephemeral: true,
        });
        return;
      }
      await interaction.reply({
        content: `Nexa has **${interaction.user.username}**. Return to the browser and press Continue on Ready to Play.`,
        ephemeral: true,
      });
      return;
    }
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

async function userHasAdminRole(userId) {
  if (!adminRoleId || !client.isReady()) return false;
  if (!/^\d{15,22}$/.test(String(userId || ''))) return false;
  for (const guild of client.guilds.cache.values()) {
    try {
      const member = await client.rest.get(Routes.guildMember(guild.id, userId));
      const roles = Array.isArray(member?.roles) ? member.roles.map(String) : [];
      if (roles.includes(adminRoleId)) return true;
    } catch {
      /* this user is not in the guild, or the role lookup failed */
    }
  }
  return false;
}

const adminLookup = http.createServer(async (req, res) => {
  const url = new URL(req.url || '/', 'http://127.0.0.1:4391');
  let admin = false;
  if (req.method === 'GET' && url.pathname === '/admin' && adminRoleId) {
    try {
      admin = await userHasAdminRole(url.searchParams.get('userId') || '');
    } catch {
      admin = false;
    }
  }
  res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
  res.end(JSON.stringify({ admin }));
});
adminLookup.listen(4391, '127.0.0.1', () => {
  console.log('[nexa-bot] admin role lookup on http://127.0.0.1:4391/admin');
  if (!adminRoleId) console.log('[nexa-bot] ADMIN_ROLE_ID is unset. Shop and news controls stay hidden.');
});

client.login(token).catch((error) => {
  console.error('[nexa-bot] Login failed:', error.message);
  process.exit(1);
});
