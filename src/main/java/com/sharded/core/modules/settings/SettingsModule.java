package com.sharded.core.modules.settings;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.chat.ChatToggleModule;
import com.sharded.core.modules.privatemessages.PrivateMessagesModule;
import com.sharded.core.util.ConfigSync;
import com.sharded.core.util.PlayerToggles;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** Personal settings GUI (/settings) — toggles persist in SQLite across sessions. */
public final class SettingsModule extends Module implements CommandExecutor {

    public SettingsModule(ShardedCore plugin) {
        super(plugin, "settings");
    }

    @Override
    protected void onEnable() {
        File guiFile = new File(moduleFolder(), "gui.yml");
        ConfigSync.sync(plugin, guiFile, "modules/settings/gui.yml");
        plugin.gui().loadMenu(guiFile, "settings");
        plugin.gui().registerMenuExtras("settings", this::placeholders);

        registerCommand("settings", this);
        registerCommand("sb", this);
        registerCommand("deathtoggle", this);
        registerCommand("jointoggle", this);
        registerCommand("mobtoggle", this);
    }

    public void openSettings(Player player) {
        if (!player.hasPermission("sharded.settings.use")) {
            send(player, "no-permission");
            return;
        }
        plugin.gui().open(player, "settings", placeholders(player));
        send(player, "opened");
    }

    public Map<String, String> placeholders(Player player) {
        Map<String, String> map = new HashMap<>();
        map.put("status_scoreboard", formatStatus(PlayerToggles.scoreboard(player)));
        map.put("status_death_messages", formatStatus(PlayerToggles.deathMessages(player)));
        map.put("status_join_messages", formatStatus(PlayerToggles.joinMessages(player)));
        map.put("status_mob_toggle", formatStatus(PlayerToggles.mobSpawn(player)));
        map.put("status_public_chat", formatStatus(isChatEnabled(player)));
        map.put("status_private_messages", formatStatus(isMsgEnabled(player)));
        return map;
    }

    public String formatStatus(boolean enabled) {
        String key = enabled ? "status.enabled" : "status.disabled";
        return config.getString(key, enabled ? "&#9FFF00&lENABLED" : "&#FF2727&lDISABLED");
    }

    private boolean isChatEnabled(Player player) {
        ChatToggleModule chat = plugin.modules().get(ChatToggleModule.class);
        return chat == null || !chat.isEnabled() || chat.isChatEnabled(player);
    }

    private boolean isMsgEnabled(Player player) {
        PrivateMessagesModule pms = plugin.modules().get(PrivateMessagesModule.class);
        return pms == null || !pms.isEnabled() || pms.isMsgEnabled(player);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        String cmd = command.getName().toLowerCase();
        if (cmd.equals("settings") || cmd.equals("setting")) {
            openSettings(player);
            return true;
        }
        if (args.length > 0 && !args[0].equalsIgnoreCase("toggle")) {
            return true;
        }
        switch (cmd) {
            case "sb" -> toggleScoreboard(player);
            case "deathtoggle", "deathmessages", "dtoggle" -> toggleDeath(player);
            case "jointoggle", "joinmessages", "joinleave", "jtoggle" -> toggleJoin(player);
            case "mobtoggle", "mobspawn", "mtoggle" -> toggleMobSpawn(player);
        }
        return true;
    }

    private void toggleScoreboard(Player player) {
        if (!check(player, "sharded.settings.scoreboard")) return;
        PlayerToggles.setScoreboard(player, !PlayerToggles.scoreboard(player));
        send(player, PlayerToggles.scoreboard(player) ? "scoreboard-on" : "scoreboard-off");
    }

    private void toggleDeath(Player player) {
        if (!check(player, "sharded.settings.deathmessages")) return;
        PlayerToggles.setDeathMessages(player, !PlayerToggles.deathMessages(player));
        send(player, PlayerToggles.deathMessages(player) ? "death-on" : "death-off");
    }

    private void toggleJoin(Player player) {
        if (!check(player, "sharded.settings.joinmessages")) return;
        PlayerToggles.setJoinMessages(player, !PlayerToggles.joinMessages(player));
        send(player, PlayerToggles.joinMessages(player) ? "join-on" : "join-off");
    }

    private void toggleMobSpawn(Player player) {
        if (!check(player, "sharded.settings.mobspawn")) return;
        PlayerToggles.setMobSpawn(player, !PlayerToggles.mobSpawn(player));
        send(player, PlayerToggles.mobSpawn(player) ? "mobspawn-on" : "mobspawn-off");
    }

    private boolean check(Player player, String permission) {
        if (player.hasPermission(permission)) return true;
        PlayerToggles.noPermissionActionBar(player, raw("no-permission-actionbar"));
        return false;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerToggles.setScoreboard(player, PlayerToggles.scoreboard(player));
    }

    @EventHandler
    public void onMobSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
        World world = event.getEntity().getWorld();
        if (world.getPlayers().isEmpty()) return;
        var loc = event.getLocation();
        for (Player player : world.getPlayers()) {
            if (PlayerToggles.mobSpawn(player)) continue;
            if (player.getLocation().distanceSquared(loc) <= 256) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
