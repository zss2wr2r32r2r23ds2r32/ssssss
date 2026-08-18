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

/** /hide - scrambles your display name and swaps skin to Steve. */
public final class HideModule extends Module implements CommandExecutor {

    private record Original(PlayerProfile profile, net.kyori.adventure.text.Component displayName,
                            net.kyori.adventure.text.Component listName) {
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
            send(player, "no-permission");
            return true;
        }
        if (isHidden(player)) unhide(player, false);
        else hide(player);
        return true;
    }

    private void hide(Player player) {
        String scrambled = config.getString("scrambled-name", "&kaaaaaaaaaa");
        hidden.put(player.getUniqueId(), new Original(player.getPlayerProfile(), player.displayName(), player.playerListName()));

        PlayerProfile fake = Bukkit.createProfile(player.getUniqueId(), "??????");
        fake.setProperty(new ProfileProperty("textures", steveTexture()));
        player.setPlayerProfile(fake);
        player.displayName(Text.c(scrambled));
        player.playerListName(Text.c(scrambled));
        send(player, "hidden");
    }

    private void unhide(Player player, boolean silent) {
        Original original = hidden.remove(player.getUniqueId());
        if (original == null) return;
        player.setPlayerProfile(original.profile());
        player.displayName(original.displayName());
        player.playerListName(original.listName());
        if (!silent) send(player, "unhidden");
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
