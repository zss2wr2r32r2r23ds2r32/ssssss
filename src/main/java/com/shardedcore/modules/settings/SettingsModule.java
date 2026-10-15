package com.shardedcore.modules.settings;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import com.shardedcore.util.ConfigSync;
import com.shardedcore.util.PlayerToggles;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class SettingsModule extends Module implements CommandExecutor, Listener {

    public static final String KEY_PUBLIC_CHAT = "public-chat";
    public static final String KEY_MSG = "msgtoggle";

    public SettingsModule(ShardedCore plugin) {
        super(plugin, "settings");
    }

    @Override
    public void enable() {
        File guiFile = syncJarResource("gui.yml");
        plugin.gui().loadMenu(guiFile, "settings");
        plugin.gui().registerMenuExtras("settings", this::placeholders);
        plugin.gui().registerAction("scoreboard_toggle", this::toggleScoreboardExternal);

        registerListener(this);
        registerCommand("settings", this);
        registerCommand("deathtoggle", this);
        registerCommand("jointoggle", this);
        registerCommand("mobtoggle", this);
        registerCommand("chattoggle", this);
        registerCommand("msgtoggle", this);
        registerCommand("eventsoundstoggle", this);
    }

    @Override
    public void disable() {
        cleanup();
    }

    public void openSettings(Player player) {
        if (!player.hasPermission("sharded.settings.use") && !player.hasPermission("shardedcore.settings.use")) {
            send(player, "no-permission");
            return;
        }
        plugin.gui().open(player, "settings", placeholders(player));
    }

    public Map<String, String> placeholders(Player player) {
        Map<String, String> map = new HashMap<>();
        map.put("status_scoreboard", formatStatus(PlayerToggles.scoreboardDisplay(player)));
        map.put("status_death_messages", formatStatus(PlayerToggles.deathMessages(player)));
        map.put("status_join_messages", formatStatus(PlayerToggles.joinMessages(player)));
        map.put("status_mob_toggle", formatStatus(PlayerToggles.mobSpawn(player)));
        map.put("status_public_chat", formatStatus(getBool(player, KEY_PUBLIC_CHAT, true)));
        map.put("status_private_messages", formatStatus(getBool(player, KEY_MSG, true)));
        map.put("status_event_sounds", formatStatus(PlayerToggles.eventSounds(player)));
        return map;
    }

    public String formatStatus(boolean enabled) {
        String key = enabled ? "status.enabled" : "status.disabled";
        return messages.getString(key, enabled ? "&#9FFF00&lENABLED" : "&#FF2727&lDISABLED");
    }

    public boolean getBool(Player player, String key, boolean defaultValue) {
        return plugin.stateStore().getBool(player.getUniqueId(), key, defaultValue);
    }

    public void setBool(Player player, String key, boolean value) {
        plugin.stateStore().setBool(player.getUniqueId(), key, value);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("settings") || cmd.equals("setting")) {
            openSettings(player);
            return true;
        }
        if (args.length > 0 && !args[0].equalsIgnoreCase("toggle")) {
            return true;
        }
        switch (cmd) {
            case "deathtoggle", "deathmessages", "dtoggle" -> toggleDeath(player);
            case "jointoggle", "joinmessages", "jtoggle" -> toggleJoin(player);
            case "mobtoggle", "mobspawn", "mtoggle" -> toggleMobSpawn(player);
            case "chattoggle", "publicchat", "togglechat", "ct" -> togglePublicChat(player);
            case "msgtoggle", "togglemsg", "pmtoggle" -> togglePrivateMessages(player);
            case "eventsoundstoggle", "eventsounds" -> toggleEventSounds(player);
        }
        return true;
    }

    public void toggleScoreboardExternal(Player player) {
        if (!check(player, "sharded.settings.scoreboard")) return;
        if (canUseTabScoreboard(player)) {
            player.performCommand("sb");
            PlayerToggles.flipScoreboardDisplay(player);
            return;
        }
        PlayerToggles.setScoreboard(player, !PlayerToggles.scoreboard(player));
        send(player, PlayerToggles.scoreboard(player) ? "scoreboard-on" : "scoreboard-off");
    }

    private boolean canUseTabScoreboard(Player player) {
        return player.hasPermission("tab.scoreboard.toggle")
                || player.hasPermission("tab.scoreboard.show")
                || player.hasPermission("tab.use");
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

    private void togglePublicChat(Player player) {
        if (!check(player, "sharded.chat.toggle")) return;
        boolean next = !getBool(player, KEY_PUBLIC_CHAT, true);
        setBool(player, KEY_PUBLIC_CHAT, next);
        send(player, next ? "chat-on" : "chat-off");
    }

    private void togglePrivateMessages(Player player) {
        if (!check(player, "sharded.msg.toggle")) return;
        boolean next = !getBool(player, KEY_MSG, true);
        setBool(player, KEY_MSG, next);
        send(player, next ? "msg-on" : "msg-off");
    }

    private void toggleEventSounds(Player player) {
        if (!check(player, "sharded.settings.eventsounds")) return;
        PlayerToggles.setEventSounds(player, !PlayerToggles.eventSounds(player));
        send(player, PlayerToggles.eventSounds(player) ? "eventsounds-on" : "eventsounds-off");
    }

    private boolean check(Player player, String permission) {
        if (player.hasPermission(permission) || player.hasPermission(permission.replace("sharded.", "shardedcore."))) {
            return true;
        }
        PlayerToggles.noPermissionActionBar(player, raw("no-permission-actionbar"));
        return false;
    }

    protected File syncJarResource(String fileName) {
        File target = new File(moduleFolder, fileName);
        ConfigSync.sync(plugin, target, "modules/settings/" + fileName);
        return target;
    }

    @EventHandler
    public void onMobSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity().getWorld().getEnvironment() == World.Environment.NORMAL)) return;
        if (event.getEntity().getCustomName() != null) return;
        if (event.getEntity().getNearbyEntities(32, 32, 32).stream()
                .filter(e -> e instanceof Player player && !PlayerToggles.mobSpawn(player))
                .findAny().isEmpty()) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (PlayerToggles.scoreboard(event.getPlayer())) {
            // TAB/scoreboard plugins handle visibility externally.
        }
    }
}
