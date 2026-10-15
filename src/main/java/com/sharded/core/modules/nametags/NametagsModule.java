package com.sharded.core.modules.nametags;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.PlaceholderUtil;
import com.sharded.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Floating nametags above players using Paper display entities. */
public final class NametagsModule extends Module {

    private final Map<UUID, List<UUID>> displays = new ConcurrentHashMap<>();
    private NamespacedKey tagKey;
    private BukkitTask tickTask;

    public NametagsModule(ShardedCore plugin) {
        super(plugin, "nametags");
    }

    @Override
    protected void onEnable() {
        tagKey = new NamespacedKey(plugin, "nametag-display");
        long interval = Math.max(1L, config.getLong("update-interval-ticks", 5L));
        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
        for (Player player : Bukkit.getOnlinePlayers()) {
            spawnTags(player);
        }
    }

    @Override
    protected void onDisable() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        for (UUID uuid : List.copyOf(displays.keySet())) {
            removeTags(uuid);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> spawnTags(event.getPlayer()), 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeTags(event.getPlayer().getUniqueId());
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!displays.containsKey(player.getUniqueId())) {
                spawnTags(player);
                continue;
            }
            updateTags(player);
        }
    }

    private void spawnTags(Player player) {
        removeTags(player.getUniqueId());
        List<String> lines = resolvedLines(player);
        if (lines.isEmpty()) return;

        double height = config.getDouble("height", 2.35D);
        double lineGap = config.getDouble("line-gap", 0.28D);
        float scale = (float) config.getDouble("scale", 1.0D);
        List<UUID> ids = new ArrayList<>(lines.size());

        for (int i = 0; i < lines.size(); i++) {
            Location at = player.getLocation().clone().add(0, height - i * lineGap, 0);
            TextDisplay display = player.getWorld().spawn(at, TextDisplay.class, entity -> {
                entity.setBillboard(Display.Billboard.CENTER);
                entity.setPersistent(false);
                entity.setShadowed(config.getBoolean("shadow", true));
                entity.setSeeThrough(config.getBoolean("see-through", false));
                entity.setDefaultBackground(config.getBoolean("background", false));
                entity.getPersistentDataContainer().set(tagKey, PersistentDataType.STRING, player.getUniqueId().toString());
                Transformation transformation = entity.getTransformation();
                entity.setTransformation(new Transformation(
                        transformation.getTranslation(),
                        transformation.getLeftRotation(),
                        new Vector3f(scale, scale, scale),
                        transformation.getRightRotation()));
            });
            display.text(Text.c(lines.get(i)));
            ids.add(display.getUniqueId());
            applyVisibility(player, display);
        }
        displays.put(player.getUniqueId(), ids);
    }

    private void updateTags(Player player) {
        List<UUID> ids = displays.get(player.getUniqueId());
        if (ids == null || ids.isEmpty()) return;

        List<String> lines = resolvedLines(player);
        double height = config.getDouble("height", 2.35D);
        double lineGap = config.getDouble("line-gap", 0.28D);

        if (lines.size() != ids.size()) {
            spawnTags(player);
            return;
        }

        for (int i = 0; i < ids.size(); i++) {
            var entity = Bukkit.getEntity(ids.get(i));
            if (!(entity instanceof TextDisplay display)) {
                spawnTags(player);
                return;
            }
            Location at = player.getLocation().clone().add(0, height - i * lineGap, 0);
            display.teleport(at);
            display.text(Text.c(lines.get(i)));
            applyVisibility(player, display);
        }
    }

    private void applyVisibility(Player owner, TextDisplay display) {
        if (config.getBoolean("hide-own", false)) {
            owner.hideEntity(plugin, display);
        } else {
            owner.showEntity(plugin, display);
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(owner)) continue;
            viewer.showEntity(plugin, display);
        }
    }

    private List<String> resolvedLines(Player player) {
        List<String> templates = config.getStringList("lines");
        if (templates.isEmpty()) {
            templates = List.of("%luckperms_prefix%%player_name%", "&7%shardedcore_team%");
        }
        List<String> out = new ArrayList<>(templates.size());
        for (String line : templates) {
            if (line == null || line.isBlank()) continue;
            out.add(PlaceholderUtil.apply(player, line));
        }
        return out;
    }

    private void removeTags(UUID playerId) {
        List<UUID> ids = displays.remove(playerId);
        if (ids == null) return;
        for (UUID id : ids) {
            var entity = Bukkit.getEntity(id);
            if (entity != null) entity.remove();
        }
    }
}
