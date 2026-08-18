package com.sharded.core.modules.hide;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * /hide — other players see a Steve disguise + scrambled nametag.
 * Tab list name and skin stay normal (profile is never changed).
 */
public final class HideModule extends Module implements CommandExecutor {

    private record HideState(
            UUID armorStandId,
            UUID nameTagId,
            BukkitTask followTask,
            Team team,
            String scrambled) {
    }

    private final Map<UUID, HideState> hidden = new HashMap<>();

    public HideModule(ShardedCore plugin) {
        super(plugin, "hide");
    }

    @Override
    protected void onEnable() {
        registerCommand("hide", this);
    }

    @Override
    protected void onDisable() {
        for (UUID uuid : new HashMap<>(hidden).keySet()) {
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
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = teamId(player.getUniqueId());
        Team team = board.getTeam(teamName);
        if (team == null) team = board.registerNewTeam(teamName);
        team.addEntry(player.getName());
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);

        Location loc = player.getLocation();
        ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class, as -> {
            as.setGravity(false);
            as.setBasePlate(false);
            as.setVisible(true);
            as.setSmall(false);
            as.setMarker(false);
            as.setInvulnerable(true);
            as.setCollidable(false);
            as.setCanPickupItems(false);
            as.setCustomNameVisible(false);
            as.setPersistent(false);
            as.getEquipment().setHelmet(steveHead());
            as.getEquipment().setChestplate(new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS));
            as.getEquipment().setLeggings(new ItemStack(Material.PURPLE_STAINED_GLASS));
        });

        TextDisplay tag = loc.getWorld().spawn(loc.clone().add(0, 2.0, 0), TextDisplay.class, td -> {
            td.text(Text.c(scrambled));
            td.setBillboard(Display.Billboard.CENTER);
            td.setSeeThrough(true);
            td.setShadowed(true);
            td.setPersistent(false);
        });

        HideState state = new HideState(stand.getUniqueId(), tag.getUniqueId(), null, team, scrambled);
        hidden.put(player.getUniqueId(), state);

        applyVisibility(player, stand, tag);
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> follow(player, stand, tag), 1L, 1L);
        hidden.put(player.getUniqueId(), new HideState(state.armorStandId(), state.nameTagId(), task, team, scrambled));

        send(player, "hidden");
    }

    private void follow(Player player, ArmorStand stand, TextDisplay tag) {
        if (!player.isOnline() || !stand.isValid()) return;
        Location loc = player.getLocation();
        stand.teleport(loc);
        tag.teleport(loc.clone().add(0, 2.0, 0));
    }

    private void applyVisibility(Player hiddenPlayer, ArmorStand stand, TextDisplay tag) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(hiddenPlayer)) {
                viewer.hideEntity(plugin, stand);
                viewer.hideEntity(plugin, tag);
            } else {
                viewer.hideEntity(plugin, hiddenPlayer);
                viewer.showEntity(plugin, stand);
                viewer.showEntity(plugin, tag);
            }
        }
    }

    private void unhide(Player player, boolean silent) {
        HideState state = hidden.remove(player.getUniqueId());
        if (state == null) return;
        if (state.followTask() != null) state.followTask().cancel();
        if (state.team() != null) {
            state.team().removeEntry(player.getName());
            if (state.team().getEntries().isEmpty()) state.team().unregister();
        }
        Entity stand = Bukkit.getEntity(state.armorStandId());
        if (stand != null) stand.remove();
        Entity tag = Bukkit.getEntity(state.nameTagId());
        if (tag != null) tag.remove();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            viewer.showEntity(plugin, player);
        }
        if (!silent) send(player, "unhidden");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joiner = event.getPlayer();
        for (Map.Entry<UUID, HideState> entry : hidden.entrySet()) {
            Player hiddenPlayer = Bukkit.getPlayer(entry.getKey());
            if (hiddenPlayer == null || !hiddenPlayer.isOnline()) continue;
            Entity stand = Bukkit.getEntity(entry.getValue().armorStandId());
            Entity tag = Bukkit.getEntity(entry.getValue().nameTagId());
            if (joiner.equals(hiddenPlayer)) {
                if (stand != null) joiner.hideEntity(plugin, stand);
                if (tag != null) joiner.hideEntity(plugin, tag);
            } else {
                joiner.hideEntity(plugin, hiddenPlayer);
                if (stand != null) joiner.showEntity(plugin, stand);
                if (tag != null) joiner.showEntity(plugin, tag);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        HideState state = hidden.remove(event.getPlayer().getUniqueId());
        if (state == null) return;
        if (state.followTask() != null) state.followTask().cancel();
        if (state.team() != null) {
            state.team().removeEntry(event.getPlayer().getName());
            if (state.team().getEntries().isEmpty()) state.team().unregister();
        }
        Entity stand = Bukkit.getEntity(state.armorStandId());
        if (stand != null) stand.remove();
        Entity tag = Bukkit.getEntity(state.nameTagId());
        if (tag != null) tag.remove();
    }

    private ItemStack steveHead() {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), "Steve");
        profile.setProperty(new ProfileProperty("textures", steveTexture()));
        meta.setPlayerProfile(profile);
        head.setItemMeta(meta);
        return head;
    }

    private String scrambleName() {
        if (config.getBoolean("random-scramble", true)) {
            int len = config.getInt("scramble-length", 12);
            StringBuilder sb = new StringBuilder("&k");
            for (int i = 0; i < len; i++) {
                sb.append((char) ('a' + ThreadLocalRandom.current().nextInt(26)));
            }
            return sb.toString();
        }
        return config.getString("scrambled-name", "&kaaaaaaaaaa");
    }

    private String steveTexture() {
        String url = config.getString("skin-url",
                "http://textures.minecraft.net/texture/1a4af718455d4aab528e7a61f86fa25e6a369d1768dcb13f7df319a713eb810b");
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private String teamId(UUID uuid) {
        return "sh_hide_" + uuid.toString().replace("-", "").substring(0, 12);
    }
}
