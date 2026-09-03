package com.shardedcore.eventcore.modules;

import com.shardedcore.eventcore.ShardedEventCore;
import com.shardedcore.eventcore.event.EventMode;
import com.shardedcore.eventcore.event.GamePhase;
import com.shardedcore.eventcore.event.Setting;
import com.shardedcore.eventcore.module.EventModule;
import com.shardedcore.eventcore.util.Feedback;
import com.shardedcore.eventcore.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates the run: {@code /start}, {@code /end}, joins, revives and the
 * winner announcement.
 *
 * <p>Everything else in the plugin reacts to the phase this module publishes,
 * which is why {@code /start} needs no knowledge of how PvP or the border are
 * implemented — it flips the phase and lets the other modules read it.</p>
 */
public final class GameModule extends EventModule {

    private static final String BUNGEE_CHANNEL = "BungeeCord";

    private boolean bungeeRegistered;

    public GameModule(ShardedEventCore plugin) {
        super(plugin, "game", "Event lifecycle: /start, /end, winner detection and revives.");
    }

    @Override
    protected void onModuleEnable() {
        if (!bungeeRegistered && config().raw().getBoolean("end.send-to-lobby", true)) {
            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
            bungeeRegistered = true;
        }
    }

    // ------------------------------------------------------------------ start

    /**
     * Runs the pre-event countdown and then unlocks the world.
     *
     * @return false when nothing is selected or the event is already live
     */
    public boolean start() {
        FileConfiguration config = config().raw();
        if (!plugin.state().hasSelection() && config.getBoolean("start.require-selection", true)) {
            return false;
        }
        if (plugin.state().running()) {
            return false;
        }

        if (config.getBoolean("start.whitelist-on", true)) {
            SpawnModule spawnModule = plugin.modules().byType(SpawnModule.class);
            if (spawnModule != null && spawnModule.isEnabled()) {
                spawnModule.setWhitelist(true);
            }
        }

        int seconds = resolveStartCountdown(config);
        CountdownModule countdown = plugin.modules().byType(CountdownModule.class);
        if (seconds > 0 && countdown != null && countdown.isEnabled() && countdown.start(seconds)) {
            return true;
        }
        unlock();
        return true;
    }

    private int resolveStartCountdown(FileConfiguration config) {
        if (config.getBoolean("start.use-selected-countdown", true)) {
            EventMode mode = plugin.state().selected();
            if (mode != null) {
                return plugin.state().selectedCountdown(mode, config.getInt("start.countdown-seconds", 10));
            }
        }
        return config.getInt("start.countdown-seconds", 10);
    }

    /**
     * Opens the event up: PvP, damage and building all come back on and the
     * participant list is snapshotted for winner detection.
     */
    public void unlock() {
        FileConfiguration config = config().raw();
        EventMode mode = plugin.state().selected();

        if (mode != null && config.getBoolean("start.force-enable-pvp", true)) {
            plugin.state().setToggle(mode, Setting.PVP, true);
        }

        plugin.state().phase(GamePhase.RUNNING);
        plugin.state().seedParticipants();

        ProtectionModule protection = plugin.modules().byType(ProtectionModule.class);
        if (protection != null && protection.isEnabled()) {
            protection.applyWorldRules();
        }

        if (config.getBoolean("start.apply-border", true)) {
            WorldBorderModule border = plugin.modules().byType(WorldBorderModule.class);
            if (border != null && border.isEnabled()) {
                border.applyEventStartBorder();
            }
        }

        if (config.getBoolean("start.reset-block-tracking", true)) {
            ClearBlocksModule clear = plugin.modules().byType(ClearBlocksModule.class);
            if (clear != null && clear.isEnabled()) {
                clear.resetTracking();
            }
        }

        DeathModule death = plugin.modules().byType(DeathModule.class);
        if (death != null && death.isEnabled() && config.getBoolean("start.clear-head-stashes", true)) {
            death.clearStashes();
        }

        if (config.getBoolean("start.heal", true)) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getGameMode() != GameMode.SPECTATOR) {
                    heal(player);
                }
            }
        }

        plugin.messages().broadcast("game.started",
                "%players%", Integer.toString(plugin.state().aliveCount()));
        plugin.guis().refreshAll();
    }

    private void heal(Player player) {
        org.bukkit.attribute.AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(max == null ? 20.0D : max.getValue());
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        player.setFireTicks(0);
        player.setRemainingAir(player.getMaximumAir());
    }

    // -------------------------------------------------------------------- end

    /** Ends the run, re-enables the whitelist and sends everyone to the lobby. */
    public void end() {
        FileConfiguration config = config().raw();
        plugin.state().phase(GamePhase.ENDED);

        CountdownModule countdown = plugin.modules().byType(CountdownModule.class);
        if (countdown != null && countdown.isEnabled()) {
            countdown.stop(true);
        }

        if (config.getBoolean("end.whitelist-on", true)) {
            SpawnModule spawnModule = plugin.modules().byType(SpawnModule.class);
            if (spawnModule != null && spawnModule.isEnabled()) {
                spawnModule.setWhitelist(true);
            }
        }

        plugin.messages().broadcast("game.ended");

        long delay = Math.max(0L, config.getLong("end.delay-seconds", 0L)) * 20L;
        if (config.getBoolean("end.send-to-lobby", true)) {
            if (delay <= 0L) {
                sendEveryoneToLobby();
            } else {
                Bukkit.getScheduler().runTaskLater(plugin, this::sendEveryoneToLobby, delay);
            }
        }

        if (config.getBoolean("end.reset-state", true)) {
            Bukkit.getScheduler().runTaskLater(plugin, this::resetForNextRun, Math.max(delay + 20L, 20L));
        }
    }

    private void resetForNextRun() {
        plugin.state().phase(GamePhase.LOBBY);
        plugin.state().resetParticipants();
        DeathModule death = plugin.modules().byType(DeathModule.class);
        if (death != null && death.isEnabled()) {
            death.clearStashes();
        }
        ProtectionModule protection = plugin.modules().byType(ProtectionModule.class);
        if (protection != null && protection.isEnabled()) {
            protection.applyWorldRules();
        }
        plugin.guis().refreshAll();
    }

    public void sendEveryoneToLobby() {
        for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            sendToLobby(player);
        }
    }

    /**
     * Moves a player to the lobby. {@code BUNGEE} writes the proxy's Connect
     * message directly; {@code COMMAND} runs the configured command as the player
     * for setups that expose {@code /server} through a bridge plugin.
     */
    public void sendToLobby(Player player) {
        FileConfiguration config = config().raw();
        String method = config.getString("end.method", "BUNGEE").toUpperCase(Locale.ROOT);
        String server = config.getString("end.server", "lobby");

        if ("COMMAND".equals(method)) {
            String command = config.getString("end.command", "server " + server);
            player.performCommand(Text.fill(command, "%server%", server));
            return;
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream(32);
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeUTF("Connect");
            out.writeUTF(server);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not build the proxy Connect message: " + exception.getMessage());
            return;
        }
        player.sendPluginMessage(plugin, BUNGEE_CHANNEL, buffer.toByteArray());
    }

    // ----------------------------------------------------------------- revive

    /** Brings every dead player back into the fight. */
    public int reviveAll() {
        FileConfiguration config = config().raw();
        List<UUID> dead = new ArrayList<>(plugin.state().dead());
        int revived = 0;
        for (UUID uuid : dead) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                plugin.state().forget(uuid);
                continue;
            }
            revive(player, config);
            revived++;
        }
        plugin.guis().refreshAll();
        return revived;
    }

    public boolean revive(Player player) {
        if (!plugin.state().isDead(player.getUniqueId())) {
            return false;
        }
        revive(player, config().raw());
        return true;
    }

    private void revive(Player player, FileConfiguration config) {
        plugin.state().markAlive(player.getUniqueId());
        player.setGameMode(GameMode.valueOf(
                config.getString("revive.gamemode", "SURVIVAL").toUpperCase(Locale.ROOT)));
        heal(player);

        if (config.getBoolean("revive.teleport-to-spawn", true)) {
            SpawnModule spawnModule = plugin.modules().byType(SpawnModule.class);
            if (spawnModule != null && spawnModule.isEnabled()) {
                spawnModule.teleport(player, spawnModule.resolveActiveSpawn());
            }
        }
        if (config.getBoolean("revive.give-kit", true)) {
            giveSelectedKit(player);
        }
        plugin.messages().send(player, "game.revived");
    }

    // -------------------------------------------------------------------- kit

    /** Gives the kit selected for the active mode, if any. */
    public boolean giveSelectedKit(Player player) {
        EventMode mode = plugin.state().selected();
        if (mode == null) {
            return false;
        }
        String kit = plugin.state().selectedKit(mode);
        if (kit == null || kit.isBlank()) {
            return false;
        }
        KitModule kits = plugin.modules().byType(KitModule.class);
        return kits != null && kits.isEnabled() && kits.give(kit, player);
    }

    // ----------------------------------------------------------------- winner

    /** Called by {@link DeathModule} once a death has been recorded. */
    public void onPlayerDied(Player victim) {
        checkForWinner();
    }

    public void checkForWinner() {
        FileConfiguration config = config().raw();
        if (!config.getBoolean("winner.enabled", true) || plugin.state().phase() != GamePhase.RUNNING) {
            return;
        }
        int minimum = config.getInt("winner.minimum-players", 2);
        if (plugin.state().dead().size() + plugin.state().aliveCount() < minimum) {
            return;
        }
        if (plugin.state().aliveCount() != 1) {
            return;
        }
        UUID winner = plugin.state().alive().iterator().next();
        Player player = Bukkit.getPlayer(winner);
        announceWinner(player == null ? "Unknown" : player.getName());
    }

    public void announceWinner(String name) {
        FileConfiguration config = config().raw();
        plugin.state().phase(GamePhase.ENDED);

        ConfigurationSection winner = config.getConfigurationSection("winner");
        Map<String, String> placeholders = Map.of("%player%", name, "%winner%", name);

        Title.Times times = Feedback.times(
                winner == null ? null : winner.getConfigurationSection("times"), 10, 80, 20);
        Feedback.broadcastTitle(
                winner == null ? "&#AD4EFF&lEVENT WINNER" : winner.getString("title", "&#AD4EFF&lEVENT WINNER"),
                winner == null ? "&f%player% Has won the Event!"
                        : winner.getString("subtitle", "&f%player% Has won the Event!"),
                times, placeholders);
        Feedback.play(Bukkit.getServer(),
                Feedback.sound(winner == null ? null : winner.getConfigurationSection("sound")));

        List<String> chat = winner == null ? List.of() : winner.getStringList("chat");
        for (String line : chat) {
            Bukkit.getServer().sendMessage(Text.parse(line, placeholders));
        }
        plugin.guis().refreshAll();
    }

    // ----------------------------------------------------------------- events

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        FileConfiguration config = config().raw();

        if (plugin.state().running()) {
            if (config.getBoolean("join.spectate-if-running", true)
                    && !plugin.state().isAlive(player.getUniqueId())) {
                player.setGameMode(GameMode.SPECTATOR);
                plugin.state().markDead(player.getUniqueId());
                return;
            }
            return;
        }

        // Lobby phase: put the player in a clean, fully configured starting state.
        String gamemode = config.getString("join.gamemode", "SURVIVAL");
        if (!gamemode.isBlank() && !"NONE".equalsIgnoreCase(gamemode)) {
            try {
                player.setGameMode(GameMode.valueOf(gamemode.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("join.gamemode '" + gamemode + "' is not a valid game mode.");
            }
        }
        plugin.state().markAlive(player.getUniqueId());
        if (config.getBoolean("join.heal", true)) {
            heal(player);
        }
        if (config.getBoolean("join.give-kit", true)) {
            giveSelectedKit(player);
        }

        ProtectionModule protection = plugin.modules().byType(ProtectionModule.class);
        if (protection != null && protection.isEnabled()) {
            protection.applyWorldRules();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (!plugin.state().running()) {
            plugin.state().forget(uuid);
            return;
        }
        if (config().raw().getBoolean("treat-quit-as-death", true)) {
            plugin.state().markDead(uuid);
            Bukkit.getScheduler().runTask(plugin, this::checkForWinner);
        }
    }

    /** Muted chat for spectators, so eliminated players cannot coach the survivors. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!config().raw().getBoolean("mute-dead-players", false)) {
            return;
        }
        if (plugin.state().running() && plugin.state().isDead(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            plugin.messages().send(event.getPlayer(), "game.dead-chat-blocked");
        }
    }

    public Location lobbySpawn() {
        SpawnModule spawnModule = plugin.modules().byType(SpawnModule.class);
        return spawnModule == null ? null : spawnModule.resolveActiveSpawn();
    }
}
