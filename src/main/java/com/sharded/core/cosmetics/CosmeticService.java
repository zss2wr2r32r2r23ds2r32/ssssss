package com.sharded.core.cosmetics;

import com.sharded.core.ShardedCore;
import com.sharded.core.util.ColorUtil;
import com.sharded.core.util.GradientUtil;
import com.sharded.core.util.RainbowUtil;
import com.sharded.core.util.TagDisplayUtil;
import com.sharded.core.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.util.UUID;

/** Applies tags, name colours, and chat colours to tab, nametag, and chat. */
public final class CosmeticService implements Listener {

    private final ShardedCore plugin;
    private CosmeticDatabase database;

    public CosmeticService(ShardedCore plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        try {
            database = new CosmeticDatabase(plugin, new File(plugin.getDataFolder(), "cosmetics"));
        } catch (Exception e) {
            plugin.getLogger().severe("Could not open cosmetics database — tag/name/chat display disabled: " + e.getMessage());
            e.printStackTrace();
            database = null;
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void disable() {
        if (database != null) database.close();
        database = null;
    }

    public CosmeticDatabase database() {
        return database;
    }

    public void setTag(Player player, String tagId, String tagDisplay) {
        if (database == null) return;
        CosmeticDatabase.PlayerCosmetics cur = database.get(player.getUniqueId());
        database.save(player.getUniqueId(), cur.withTag(tagId, tagDisplay));
        applyDisplay(player);
    }

    public void clearTag(Player player) {
        if (database == null) return;
        database.save(player.getUniqueId(), database.get(player.getUniqueId()).withoutTag());
        applyDisplay(player);
    }

    public void setNameColor(Player player, String colorSpec) {
        if (database == null) return;
        database.save(player.getUniqueId(), database.get(player.getUniqueId()).withNameColor(normalizeColorSpec(colorSpec)));
        applyDisplay(player);
    }

    public void clearNameColor(Player player) {
        if (database == null) return;
        database.save(player.getUniqueId(), database.get(player.getUniqueId()).withoutNameColor());
        applyDisplay(player);
    }

    public void setChatColor(Player player, String colorSpec) {
        if (database == null) return;
        database.save(player.getUniqueId(), database.get(player.getUniqueId()).withChatColor(normalizeColorSpec(colorSpec)));
    }

    public void clearChatColor(Player player) {
        if (database == null) return;
        database.save(player.getUniqueId(), database.get(player.getUniqueId()).withoutChatColor());
    }

    public String tagDisplay(UUID uuid) {
        if (database == null) return "";
        CosmeticDatabase.PlayerCosmetics c = database.get(uuid);
        return c.tagDisplay() == null ? "" : TagDisplayUtil.tabTag(c.tagDisplay());
    }

    public String formattedName(Player player) {
        if (database == null) return player.getName();
        return colorizeName(player.getName(), database.get(player.getUniqueId()).nameColor());
    }

    public String chatColorPrefix(UUID uuid) {
        if (database == null) return "";
        String spec = database.get(uuid).chatColor();
        if (spec == null || spec.isBlank()) return "";
        if (GradientUtil.isGradient(spec)) return "";
        return ColorUtil.normalize(spec);
    }

    public void applyDisplay(Player player) {
        if (database == null) return;
        CosmeticDatabase.PlayerCosmetics c = database.get(player.getUniqueId());
        String tag = c.tagDisplay() == null ? "" : TagDisplayUtil.tabTag(c.tagDisplay());
        String name = colorizeName(player.getName(), c.nameColor());
        String withTag = tag.isBlank() ? name : tag + " " + name;
        // Tab list name is name-only so TAB/other plugins can place %shardedcore_tag% once.
        player.playerListName(Text.c(name));
        player.displayName(Text.c(name));
        if (tag.isBlank()) {
            player.customName(null);
            player.setCustomNameVisible(false);
        } else {
            player.customName(Text.c(withTag));
            player.setCustomNameVisible(true);
        }
    }

    public static String colorizeName(String name, String spec) {
        if (spec == null || spec.isBlank()) return name;
        spec = ColorUtil.normalize(spec.trim());
        if (isRainbow(spec)) return RainbowUtil.apply(name);
        if (GradientUtil.isGradient(spec)) {
            String[] hex = GradientUtil.splitGradient(spec);
            if (hex != null) return GradientUtil.apply(name, hex[0], hex[1]);
        }
        if (spec.startsWith("&#") || spec.startsWith("&")) return spec + name;
        if (spec.startsWith("#") && spec.length() == 7) return "&#" + spec.substring(1) + name;
        return spec + name;
    }

    public static String normalizeColorSpec(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        String s = raw.trim();
        if (isRainbow(s)) return "rainbow";
        if (GradientUtil.isGradient(s)) {
            String[] hex = GradientUtil.splitGradient(s);
            return hex == null ? s : hex[0] + " " + hex[1];
        }
        if (s.startsWith("#") && s.length() == 7) return "&#" + s.substring(1);
        return ColorUtil.normalize(s);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyDisplay(event.getPlayer()), 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        player.setCustomNameVisible(false);
        player.customName(null);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (database == null) return;
        String spec = database.get(event.getPlayer().getUniqueId()).chatColor();
        if (spec == null || spec.isBlank()) return;
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (isRainbow(spec)) {
            event.message(Text.c(RainbowUtil.apply(message)));
            return;
        }
        if (GradientUtil.isGradient(spec)) {
            String[] hex = GradientUtil.splitGradient(spec);
            if (hex != null) {
                event.message(Text.c(GradientUtil.apply(message, hex[0], hex[1])));
                return;
            }
        }
        event.message(Text.c(ColorUtil.normalize(spec) + message));
    }

    private static boolean isRainbow(String spec) {
        return spec != null && spec.equalsIgnoreCase("rainbow");
    }
}
