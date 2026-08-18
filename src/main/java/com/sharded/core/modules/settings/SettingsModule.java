package com.sharded.core.modules.settings;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.chat.ChatToggleModule;
import com.sharded.core.modules.nightvision.NightVisionModule;
import com.sharded.core.modules.privatemessages.PrivateMessagesModule;
import com.sharded.core.util.PlayerToggles;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;

/** Player toggles: /sb, /deathtoggle, /jointoggle (+ GUI actions if opened elsewhere). */
public final class SettingsModule extends Module implements CommandExecutor {

    public SettingsModule(ShardedCore plugin) {
        super(plugin, "settings");
    }

    @Override
    protected void onEnable() {
        File guiFile = new File(moduleFolder(), "gui.yml");
        if (!guiFile.exists()) plugin.saveResource("modules/settings/gui.yml", false);
        plugin.gui().loadMenu(guiFile, "settings");

        plugin.gui().registerAction("toggle_chat", this::toggleChat);
        plugin.gui().registerAction("toggle_msg", this::toggleMsg);
        plugin.gui().registerAction("toggle_nightvision", this::toggleNv);
        plugin.gui().registerAction("toggle_scoreboard", this::toggleScoreboard);
        plugin.gui().registerAction("toggle_deathmessages", this::toggleDeath);
        plugin.gui().registerAction("toggle_joinmessages", this::toggleJoin);
        plugin.gui().registerAction("toggle_mobspawn", this::toggleMobSpawn);

        registerCommand("sb", this);
        registerCommand("deathtoggle", this);
        registerCommand("jointoggle", this);
    }

    private void toggleChat(Player player) {
        if (!check(player, "sharded.chat.toggle")) return;
        ChatToggleModule chat = plugin.modules().get(ChatToggleModule.class);
        if (chat != null && chat.isEnabled()) chat.setChatEnabled(player, !chat.isChatEnabled(player));
    }

    private void toggleMsg(Player player) {
        if (!check(player, "sharded.msg.toggle")) return;
        PrivateMessagesModule pms = plugin.modules().get(PrivateMessagesModule.class);
        if (pms != null && pms.isEnabled()) pms.setMsgEnabled(player, !pms.isMsgEnabled(player));
    }

    private void toggleNv(Player player) {
        if (!check(player, "sharded.nightvision.use")) return;
        NightVisionModule nv = plugin.modules().get(NightVisionModule.class);
        if (nv != null && nv.isEnabled()) nv.setNightVision(player, !nv.isNightVisionEnabled(player));
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

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        switch (command.getName().toLowerCase()) {
            case "sb" -> {
                if (!player.hasPermission("sharded.settings.scoreboard")) {
                    PlayerToggles.noPermissionActionBar(player, raw("no-permission-actionbar"));
                    return true;
                }
                PlayerToggles.setScoreboard(player, !PlayerToggles.scoreboard(player));
                send(player, PlayerToggles.scoreboard(player) ? "scoreboard-on" : "scoreboard-off");
            }
            case "deathtoggle" -> toggleDeath(player);
            case "jointoggle" -> toggleJoin(player);
        }
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!PlayerToggles.scoreboard(event.getPlayer())) {
            PlayerToggles.setScoreboard(event.getPlayer(), false);
        }
    }

    @EventHandler
    public void onMobSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
        for (Player player : event.getEntity().getWorld().getPlayers()) {
            if (PlayerToggles.mobSpawn(player)) continue;
            if (player.getLocation().distanceSquared(event.getLocation()) <= 256) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
