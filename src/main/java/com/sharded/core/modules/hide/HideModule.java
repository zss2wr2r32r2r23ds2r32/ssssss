package com.sharded.core.modules.hide;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * /hide — other players see Steve skin + scrambled nametag above head.
 * Tab list name stays normal.
 */
public final class HideModule extends Module implements CommandExecutor {

    private record Original(PlayerProfile profile, net.kyori.adventure.text.Component displayName,
                            net.kyori.adventure.text.Component listName, net.kyori.adventure.text.Component customName,
                            boolean customNameVisible) {
    }

    private final Map<UUID, Original> hidden = new HashMap<>();

    public HideModule(ShardedCore plugin) {
        super(plugin, "hide");
    }

    @Override
    protected void onEnable() {
        registerCommand("hide", this);
    }

    @Override
    protected void onDisable() {
        for (UUID uuid : Map.copyOf(hidden).keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) unhide(player, true);
        }
        hidden.clear();
    }

    public boolean isHidden(Player player) {
        return hidden.containsKey(player.getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.hide.use")) {
            send(sender, "no-permission");
            return true;
        }
        if (isHidden(player)) unhide(player, false);
        else hide(player);
        return true;
    }

    private void hide(Player player) {
        String scrambled = scrambleName();
        hidden.put(player.getUniqueId(), new Original(
                player.getPlayerProfile(), player.displayName(), player.playerListName(),
                player.customName(), player.isCustomNameVisible()));

        PlayerProfile steve = Bukkit.createProfile(player.getUniqueId(), stripColors(scrambled));
        steve.setProperty(new ProfileProperty("textures", steveTexture()));
        player.setPlayerProfile(steve);

        // Nametag above head (what others see floating)
        player.customName(Text.c(scrambled));
        player.setCustomNameVisible(true);
        // Tab stays normal — do NOT change playerListName
        send(player, "hidden");
    }

    private void unhide(Player player, boolean silent) {
        Original original = hidden.remove(player.getUniqueId());
        if (original == null) return;
        player.setPlayerProfile(original.profile());
        player.displayName(original.displayName());
        player.playerListName(original.listName());
        player.customName(original.customName());
        player.setCustomNameVisible(original.customNameVisible());
        if (!silent) send(player, "unhidden");
    }

    private String scrambleName() {
        String template = config.getString("scrambled-name", "&kaaaaaaaaaa");
        if (config.getBoolean("random-scramble", true)) {
            int len = config.getInt("scramble-length", 12);
            StringBuilder sb = new StringBuilder("&k");
            for (int i = 0; i < len; i++) {
                sb.append((char) ('a' + ThreadLocalRandom.current().nextInt(26)));
            }
            return sb.toString();
        }
        return template;
    }

    private String stripColors(String input) {
        return input.replaceAll("&[0-9a-fk-orA-FK-OR]", "").replaceAll("(?i)&#[0-9a-fA-F]{6}", "");
    }

    private String steveTexture() {
        String url = config.getString("skin-url",
                "http://textures.minecraft.net/texture/1a4af718455d4aab528e7a61f86fa25e6a369d1768dcb13f7df319a713eb810b");
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hidden.remove(event.getPlayer().getUniqueId());
    }
}
