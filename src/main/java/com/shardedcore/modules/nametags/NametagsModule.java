package com.shardedcore.modules.nametags;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import com.shardedcore.modules.economy.EconomyModule;
import com.shardedcore.util.ColorUtil;
import com.shardedcore.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NametagsModule extends Module implements CommandExecutor, Listener {

    private static final GsonComponentSerializer SERIALIZER = GsonComponentSerializer.gson();

    private final Map<UUID, Tag> tags = new ConcurrentHashMap<>();
    private NamespacedKey key;
    private BukkitTask task;
    private long ticks;
    private Color background;
    private Display.Brightness brightness;
    private float viewRange;
    private float scale;

    public NametagsModule(ShardedCore plugin) {
        super(plugin, "nametags");
    }

    @Override
    public void enable() {
        key = new NamespacedKey(plugin, "nametag");
        applyDisplay();
        purgeWorld();
        int refresh = Math.max(1, config.getInt("refresh", 1));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, refresh);
        registerCommand("nametags", this);
        registerListener(this);
        for (Player player : Bukkit.getOnlinePlayers()) spawn(player);
    }

    @Override
    public void disable() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (UUID uuid : new ArrayList<>(tags.keySet())) remove(uuid);
        cleanup();
    }

    @Override
    public void reload() {
        super.reload();
        applyDisplay();
        int refresh = Math.max(1, config.getInt("refresh", 1));
        if (task != null) task.cancel();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, refresh);
        for (Player player : Bukkit.getOnlinePlayers()) spawn(player);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            send(sender, "usage");
            send(sender, "info",
                    "lines", "2",
                    "offset", String.valueOf(config.getDouble("display.y-offset", 0.5)),
                    "gap", String.valueOf(config.getDouble("display.line-gap", 0.25)),
                    "refresh", String.valueOf(config.getInt("refresh", 10)));
            return true;
        }
        if (!sender.hasPermission("shardedcore.nametags.admin")) {
            sendRaw(sender, "&#FF0000&lERROR &7▷ &fYou do not have permission.");
            return true;
        }
        loadFiles();
        applyDisplay();
        int refresh = Math.max(1, config.getInt("refresh", 1));
        if (task != null) task.cancel();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, refresh);
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            spawn(player);
            count++;
        }
        send(sender, "reloaded", "count", String.valueOf(count));
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) spawn(event.getPlayer());
        }, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        spawn(event.getPlayer());
    }

    private void tick() {
        ticks += Math.max(1, config.getInt("refresh", 10));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!tags.containsKey(player.getUniqueId())) spawn(player);
            else update(player);
        }
    }

    private void spawn(Player player) {
        remove(player.getUniqueId());
        if (disabled(player.getWorld())) return;
        Tag tag = new Tag();
        tag.top = spawnLine(player, true);
        tag.bottom = spawnLine(player, false);
        tags.put(player.getUniqueId(), tag);
        update(player);
    }

    private TextDisplay spawnLine(Player player, boolean top) {
        TextDisplay display = player.getWorld().spawn(anchor(player, top), TextDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setShadowed(config.getBoolean("display.shadow", true));
            entity.setSeeThrough(config.getBoolean("display.see-through", false));
            entity.setLineWidth(config.getInt("display.line-width", 1000));
            entity.setTextOpacity((byte) config.getInt("display.text-opacity", 255));
            entity.setAlignment(TextDisplay.TextAlignment.CENTER);
            entity.setBackgroundColor(background);
            if (brightness != null) entity.setBrightness(brightness);
            entity.setViewRange(viewRange);
            entity.setInterpolationDuration(0);
            entity.setInterpolationDelay(0);
            int teleport = config.getBoolean("display.ride", true)
                    ? 0
                    : Math.max(0, config.getInt("display.teleport-duration", 1));
            entity.setTeleportDuration(teleport);
            entity.setTransformation(new Transformation(
                    offset(player, top), new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf()));
            entity.getPersistentDataContainer().set(key, PersistentDataType.STRING, player.getUniqueId().toString());
        });
        if (config.getBoolean("display.hide-own", false)) {
            player.hideEntity(plugin, display);
        } else {
            player.showEntity(plugin, display);
        }
        if (config.getBoolean("display.ride", true)) {
            player.addPassenger(display);
        }
        return display;
    }

    private void update(Player player) {
        Tag tag = tags.get(player.getUniqueId());
        if (tag == null) return;
        if (disabled(player.getWorld())) {
            remove(player.getUniqueId());
            return;
        }
        boolean hide = (config.getBoolean("display.hide-when-sneaking", true) && player.isSneaking())
                || (config.getBoolean("display.hide-when-invisible", true) && invisible(player));
        refreshLine(player, tag, true, hide);
        refreshLine(player, tag, false, hide);
    }

    private void refreshLine(Player player, Tag tag, boolean top, boolean hide) {
        TextDisplay display = top ? tag.top : tag.bottom;
        if (display == null || !display.isValid()) {
            spawn(player);
            return;
        }
        if (display.getWorld() != player.getWorld()) {
            spawn(player);
            return;
        }
        display.setInvisible(hide);
        if (config.getBoolean("display.ride", true)) {
            if (!player.getPassengers().contains(display)) player.addPassenger(display);
            display.setTransformation(new Transformation(
                    offset(player, top), new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf()));
        } else {
            display.teleport(anchor(player, top));
        }
        String path = top ? "lines.top" : "lines.bottom";
        if (!config.getBoolean(path + ".enabled", true)) {
            if (!"".equals(top ? tag.topSerial : tag.bottomSerial)) {
                display.text(Component.empty());
                if (top) tag.topSerial = "";
                else tag.bottomSerial = "";
            }
            return;
        }
        int lineRefresh = config.getInt(path + ".refresh", 0);
        int global = Math.max(1, config.getInt("refresh", 10));
        if (lineRefresh > 0 && ticks % lineRefresh != 0 && ticks > global) return;
        String raw = frame(path);
        Component component = ColorUtil.parse(placeholders(player, raw));
        String serial = SERIALIZER.serialize(component);
        if (top) {
            if (serial.equals(tag.topSerial)) return;
            tag.topSerial = serial;
        } else {
            if (serial.equals(tag.bottomSerial)) return;
            tag.bottomSerial = serial;
        }
        display.text(component);
    }

    private String frame(String path) {
        List<String> frames = config.getStringList(path + ".frames");
        if (frames.isEmpty()) return "";
        int interval = config.getInt(path + ".interval", 0);
        if (interval <= 0 || frames.size() == 1) return frames.get(0);
        int refresh = Math.max(1, config.getInt("refresh", 10));
        int rounded = ((interval + refresh - 1) / refresh) * refresh;
        if (rounded <= 0) return frames.get(0);
        int index = (int) ((ticks / rounded) % frames.size());
        return frames.get(index);
    }

    private Vector3f offset(Player player, boolean top) {
        double extra = config.getDouble("display.y-offset", 0.5);
        double gap = config.getDouble("display.line-gap", 0.25);
        float y = (float) (player.getHeight() + extra + (top ? gap : 0));
        return new Vector3f(0f, y, 0f);
    }

    private org.bukkit.Location anchor(Player player, boolean top) {
        Vector3f off = offset(player, top);
        return player.getLocation().clone().add(off.x, off.y, off.z);
    }

    private boolean invisible(Player player) {
        return player.isInvisible() || player.hasPotionEffect(PotionEffectType.INVISIBILITY);
    }

    private boolean disabled(World world) {
        for (String name : config.getStringList("disabled-worlds")) {
            if (name.equalsIgnoreCase(world.getName())) return true;
        }
        return false;
    }

    private String placeholders(Player player, String input) {
        String out = Text.applyPlaceholders(input, player);
        if (out.contains("%lifestealcore_balance_formatted%")) {
            EconomyModule economy = plugin.modules().get(EconomyModule.class);
            String formatted = economy == null ? "0" : economy.service().format(economy.service().get(player.getUniqueId()));
            out = out.replace("%lifestealcore_balance_formatted%", formatted);
        }
        if (out.contains("%lifestealcore_team%")) {
            out = out.replace("%lifestealcore_team%", "None");
        }
        if (out.contains("%shards_value_formatted%")) {
            out = out.replace("%shards_value_formatted%", "0");
        }
        if (out.contains("%statistic_player_kills%")) {
            out = out.replace("%statistic_player_kills%", String.valueOf(player.getStatistic(Statistic.PLAYER_KILLS)));
        }
        if (out.contains("%statistic_deaths%")) {
            out = out.replace("%statistic_deaths%", String.valueOf(player.getStatistic(Statistic.DEATHS)));
        }
        if (out.contains("%statistic_time_played%")) {
            out = out.replace("%statistic_time_played%", playtime(player));
        }
        if (out.contains("%luckperms_prefix%") || out.contains("%luckperms_suffix%")) {
            String prefix = "";
            String suffix = "";
            try {
                LuckPerms api = Bukkit.getServicesManager().load(LuckPerms.class);
                if (api != null) {
                    var meta = api.getPlayerAdapter(Player.class).getUser(player).getCachedData().getMetaData();
                    if (meta.getPrefix() != null) prefix = meta.getPrefix();
                    if (meta.getSuffix() != null) suffix = meta.getSuffix();
                }
            } catch (Exception ignored) {
            }
            out = out.replace("%luckperms_prefix%", prefix).replace("%luckperms_suffix%", suffix);
        }
        return out;
    }

    private String playtime(Player player) {
        long seconds = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L;
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    private void applyDisplay() {
        background = parseBackground(cfg("display.background", "#00000000"));
        int light = Math.max(0, Math.min(15, config.getInt("display.brightness", 15)));
        brightness = new Display.Brightness(light, light);
        float distance = (float) config.getDouble("display.view-distance", 64);
        viewRange = distance <= 0 ? 1f : distance / 64f;
        scale = (float) config.getDouble("display.scale", 1.0);
    }

    private Color parseBackground(String hex) {
        String raw = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            if (raw.length() == 8) {
                int a = Integer.parseInt(raw.substring(0, 2), 16);
                int r = Integer.parseInt(raw.substring(2, 4), 16);
                int g = Integer.parseInt(raw.substring(4, 6), 16);
                int b = Integer.parseInt(raw.substring(6, 8), 16);
                return Color.fromARGB(a, r, g, b);
            }
            if (raw.length() == 6) {
                return Color.fromRGB(Integer.parseInt(raw, 16));
            }
        } catch (NumberFormatException ignored) {
        }
        return Color.fromARGB(0, 0, 0, 0);
    }

    private void remove(UUID uuid) {
        Tag tag = tags.remove(uuid);
        if (tag == null) return;
        if (tag.top != null) tag.top.remove();
        if (tag.bottom != null) tag.bottom.remove();
    }

    private void purgeWorld() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof TextDisplay display)) continue;
                if (display.getPersistentDataContainer().has(key, PersistentDataType.STRING)) display.remove();
            }
        }
    }

    private static final class Tag {
        private TextDisplay top;
        private TextDisplay bottom;
        private String topSerial = "";
        private String bottomSerial = "";
    }
}
