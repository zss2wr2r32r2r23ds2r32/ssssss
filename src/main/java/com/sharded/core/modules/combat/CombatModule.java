package com.sharded.core.modules.combat;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.koth.KothModule;
import com.sharded.core.modules.outpost.OutpostModule;
import com.sharded.core.modules.protect.ProtectModule;
import com.sharded.core.util.CombatWallTracker;
import com.sharded.core.util.CuboidRegion;
import com.sharded.core.util.RegionSetup;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Combat tagging with spawn pushback and logout punishment. */
public final class CombatModule extends Module implements CommandExecutor, TabCompleter {

    private final RegionSetup setup = new RegionSetup();
    private CuboidRegion region;
    private final Map<UUID, Long> taggedUntil = new HashMap<>();
    private final CombatWallTracker wallTracker = new CombatWallTracker();
    private final Set<UUID> wasTagged = new HashSet<>();
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
        for (Player player : Bukkit.getOnlinePlayers()) {
            wallTracker.clear(player);
        }
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
        Player player = event.getPlayer();
        wallTracker.clear(player);
        boolean tagged = isTagged(player);
        taggedUntil.remove(player.getUniqueId());
        wasTagged.remove(player.getUniqueId());
        if (!tagged) return;
        if (!config.getBoolean("kill-on-logout", true)) return;
        player.setHealth(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!isTagged(event.getPlayer())) return;
        ProtectModule protect = plugin.modules().get(ProtectModule.class);
        if (protect == null) return;
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null) return;

        CuboidRegion spawn = protect.region("spawn");
        if (spawn == null || !spawn.world().equals(to.getWorld().getName())) return;

        boolean toSpawn = spawn.contains(to);
        boolean fromSpawn = spawn.contains(from);
        if (!toSpawn) return;

        event.setCancelled(true);
        event.setTo(from);
        Location safe = fromSpawn ? ejectFromSpawn(event.getPlayer(), spawn, from) : from.clone();
        pushBack(event.getPlayer(), safe, protect, spawn);
    }

    private Location ejectFromSpawn(Player player, CuboidRegion spawn, Location inside) {
        int x = inside.getBlockX();
        int z = inside.getBlockZ();
        int cx = (spawn.minX() + spawn.maxX()) / 2;
        int cz = (spawn.minZ() + spawn.maxZ()) / 2;
        int dx = x - cx;
        int dz = z - cz;
        if (dx == 0 && dz == 0) dz = 1;
        double len = Math.sqrt(dx * dx + dz * dz);
        int outX = x + (int) Math.round(dx / len * 3);
        int outZ = z + (int) Math.round(dz / len * 3);
        Location out = new Location(inside.getWorld(), outX + 0.5, inside.getY(), outZ + 0.5, inside.getYaw(), inside.getPitch());
        if (spawn.contains(out)) {
            out.setX(x + (dx >= 0 ? spawn.maxX() - spawn.minX() + 2 : -2));
        }
        return out;
    }

    private void pushBack(Player player, Location safe, ProtectModule protect, CuboidRegion spawn) {
        player.teleport(safe);
        int cx = (spawn.minX() + spawn.maxX()) / 2;
        int cz = (spawn.minZ() + spawn.maxZ()) / 2;
        Vector push = safe.toVector().subtract(new Vector(cx, safe.getY(), cz));
        if (push.lengthSquared() < 0.01) {
            push = new Vector(0, 0, 1);
        }
        push.normalize().multiply(config.getDouble("pushback-strength", 1.2));
        push.setY(config.getDouble("pushback-y", 0.4));
        player.setVelocity(push);
        send(player, "pushback");
        if (config.getBoolean("red-glass-walls", true)) {
            wallTracker.showLocalSpawnWall(player, spawn, safe);
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = taggedUntil.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            if (entry.getValue() <= now) {
                Player expired = Bukkit.getPlayer(entry.getKey());
                if (expired != null) wallTracker.clear(expired);
                it.remove();
            }
        }

        KothModule koth = plugin.modules().get(KothModule.class);
        OutpostModule outpost = plugin.modules().get(OutpostModule.class);
        ProtectModule protect = plugin.modules().get(ProtectModule.class);

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            Long until = taggedUntil.get(id);
            boolean tagged = until != null && until > now;

            if (tagged) {
                wasTagged.add(id);
                if (protect != null && protect.inSpawn(player.getLocation())) {
                    CuboidRegion spawn = protect.region("spawn");
                    if (spawn != null) {
                        Location ejected = ejectFromSpawn(player, spawn, player.getLocation());
                        player.teleport(ejected);
                    }
                }
                if (eventActionBarActive(player, koth, outpost)) continue;
                long left = (until - now) / 1000L;
                String msg = config.getString("actionbar", "&cCombat &7| &f%seconds%s")
                        .replace("%seconds%", String.valueOf(left));
                player.sendActionBar(Text.c(msg));
                if (protect != null && config.getBoolean("red-glass-walls", true)) {
                    CuboidRegion spawn = protect.region("spawn");
                    if (spawn != null && spawn.world().equals(player.getWorld().getName())
                            && nearSpawnBorder(player.getLocation(), spawn, 10)) {
                        wallTracker.showLocalSpawnWall(player, spawn, player.getLocation());
                    }
                }
            } else if (wasTagged.remove(id)) {
                wallTracker.clear(player);
            }
        }
    }

    private boolean eventActionBarActive(Player player, KothModule koth, OutpostModule outpost) {
        if (koth != null && koth.isActive() && koth.isInside(player)) return true;
        if (outpost != null && outpost.isActive() && outpost.isInside(player)) return true;
        return false;
    }

    private boolean nearSpawnBorder(Location loc, CuboidRegion spawn, int margin) {
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        return x >= spawn.minX() - margin && x <= spawn.maxX() + margin
                && z >= spawn.minZ() - margin && z <= spawn.maxZ() + margin;
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
