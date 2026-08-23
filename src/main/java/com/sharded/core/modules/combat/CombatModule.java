package com.sharded.core.modules.combat;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.protect.ProtectModule;
import com.sharded.core.util.CuboidRegion;
import com.sharded.core.util.RegionSetup;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Combat tagging with spawn pushback and logout punishment. */
public final class CombatModule extends Module implements CommandExecutor, TabCompleter {

    private final RegionSetup setup = new RegionSetup();
    private CuboidRegion region;
    private final Map<UUID, Long> taggedUntil = new HashMap<>();
    private int tickTask = -1;

    public CombatModule(ShardedCore plugin) {
        super(plugin, "combat");
    }

    @Override
    protected void onEnable() {
        region = CuboidRegion.fromSection(config.getConfigurationSection("region"));
        registerCommand("combat", this);
        tickTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, 10L, 10L);
    }

    @Override
    protected void onDisable() {
        if (tickTask >= 0) Bukkit.getScheduler().cancelTask(tickTask);
    }

    public boolean isTagged(Player player) {
        Long until = taggedUntil.get(player.getUniqueId());
        return until != null && System.currentTimeMillis() < until;
    }

    private int tagSeconds() {
        return config.getInt("tag-seconds", 15);
    }

    private void tag(Player player) {
        taggedUntil.put(player.getUniqueId(), System.currentTimeMillis() + tagSeconds() * 1000L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.combat.admin")) {
            send(sender, "no-permission");
            return true;
        }
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (sub.equals("pos1")) {
            setup.setPos1(player, player.getLocation());
            send(player, "pos1-set");
            return true;
        }
        if (sub.equals("pos2")) {
            setup.setPos2(player, player.getLocation());
            send(player, "pos2-set");
            return true;
        }
        if (sub.equals("setregion")) {
            CuboidRegion built = setup.build(player);
            if (built == null) {
                send(player, "need-positions");
                return true;
            }
            region = built;
            built.write(config.createSection("region"));
            saveConfig();
            send(player, "region-set");
            return true;
        }
        send(player, "usage");
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        if (event.getDamager() instanceof Player p) attacker = p;
        else if (event.getDamager() instanceof org.bukkit.entity.Projectile proj
                && proj.getShooter() instanceof Player p) attacker = p;
        if (attacker != null) {
            tag(attacker);
            tag(victim);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        if (!isTagged(event.getPlayer())) return;
        if (!config.getBoolean("kill-on-logout", true)) return;
        event.getPlayer().setHealth(0);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!isTagged(event.getPlayer())) return;
        ProtectModule protect = plugin.modules().get(ProtectModule.class);
        if (protect == null || region == null) return;
        if (!region.contains(event.getTo())) return;
        if (protect.inSpawn(event.getTo())) {
            pushBack(event.getPlayer(), event.getFrom());
        }
    }

    private void pushBack(Player player, Location from) {
        player.teleport(from);
        Vector push = from.toVector().subtract(player.getLocation().toVector()).normalize().multiply(0.8);
        push.setY(0.35);
        player.setVelocity(push);
        send(player, "pushback");
        showWalls(player);
    }

    private void showWalls(Player player) {
        if (region == null || !config.getBoolean("red-glass-walls", true)) return;
        org.bukkit.block.data.BlockData pane = Material.RED_STAINED_GLASS_PANE.createBlockData();
        int y = player.getLocation().getBlockY();
        for (int x = region.minX(); x <= region.maxX(); x++) {
            player.sendBlockChange(new Location(player.getWorld(), x, y, region.minZ()), pane);
            player.sendBlockChange(new Location(player.getWorld(), x, y, region.maxZ()), pane);
        }
        for (int z = region.minZ(); z <= region.maxZ(); z++) {
            player.sendBlockChange(new Location(player.getWorld(), region.minX(), y, z), pane);
            player.sendBlockChange(new Location(player.getWorld(), region.maxX(), y, z), pane);
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        taggedUntil.entrySet().removeIf(e -> e.getValue() <= now);
        for (Player player : Bukkit.getOnlinePlayers()) {
            Long until = taggedUntil.get(player.getUniqueId());
            if (until == null || until <= now) continue;
            long left = (until - now) / 1000L;
            String msg = config.getString("actionbar", "&cCombat &7| &f%seconds%s")
                    .replace("%seconds%", String.valueOf(left));
            player.sendActionBar(Text.c(msg));
        }
    }

    private void saveConfig() {
        try {
            config.save(new File(moduleFolder(), "config.yml"));
        } catch (Exception e) {
            plugin.getLogger().warning("[combat] Could not save config: " + e.getMessage());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("sharded.combat.admin")) return List.of();
        if (args.length == 1) return TabCompleteHelper.filter(args[0], "pos1", "pos2", "setregion");
        return List.of();
    }
}
