package com.shardedcore.eventcore.modules;

import com.shardedcore.eventcore.ShardedEventCore;
import com.shardedcore.eventcore.event.EventMode;
import com.shardedcore.eventcore.event.Setting;
import com.shardedcore.eventcore.module.EventModule;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Enforces the PvP, locator-bar, spawn-protection and pre-start rules.
 *
 * <p>All checks are ordered cheapest first: phase and toggle lookups are enum
 * array reads, and the squared-distance spawn check only runs once those pass.
 * Nothing here allocates on the common "allowed" path.</p>
 */
public final class ProtectionModule extends EventModule {

    private final Map<UUID, Long> lastNotice = new HashMap<>();

    public ProtectionModule(ShardedEventCore plugin) {
        super(plugin, "protection", "PvP, locator bar, spawn protection and pre-start lockdown.");
    }

    @Override
    protected void onModuleEnable() {
        applyWorldRules();
    }

    @Override
    protected void onModuleDisable() {
        lastNotice.clear();
    }

    @Override
    protected void onConfigReload() {
        applyWorldRules();
    }

    // ------------------------------------------------------------ world rules

    /**
     * Pushes the selected mode's PvP and locator-bar toggles onto the worlds.
     *
     * <p>The vanilla game rules are kept in step with the toggles so other
     * plugins reading them see the truth, but the enforcement that actually
     * matters happens in the damage handler below.</p>
     */
    public void applyWorldRules() {
        EventMode mode = plugin.state().selected();
        if (mode == null) {
            return;
        }
        boolean pvp = plugin.state().toggleValue(mode, Setting.PVP) && plugin.state().running();
        boolean locatorBar = plugin.state().toggleValue(mode, Setting.LOCATOR_BAR);
        boolean applyPvpRule = config().raw().getBoolean("apply-pvp-game-rule", true);
        boolean applyLocatorBar = config().raw().getBoolean("locator-bar.apply-game-rule", true);

        for (World world : targetWorlds()) {
            if (applyPvpRule) {
                world.setGameRule(GameRules.PVP, pvp);
            }
            if (applyLocatorBar) {
                world.setGameRule(GameRules.LOCATOR_BAR, locatorBar);
            }
        }
    }

    private List<World> targetWorlds() {
        List<String> names = config().raw().getStringList("worlds");
        if (names.isEmpty()) {
            return Bukkit.getWorlds();
        }
        List<World> out = new java.util.ArrayList<>(names.size());
        for (String name : names) {
            World world = Bukkit.getWorld(name);
            if (world != null) {
                out.add(world);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ checks

    private boolean lockActive() {
        return plugin.state().locked() && config().raw().getBoolean("lobby-lock.enabled", true);
    }

    private boolean pvpAllowed() {
        return plugin.state().running() && plugin.state().toggleValue(Setting.PVP);
    }

    private boolean exempt(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return true;
        }
        String permission = config().raw().getString("bypass-permission", "shardedcore.bypass");
        return !permission.isBlank() && player.hasPermission(permission);
    }

    /** True when the player stands inside the protected bubble around the spawn. */
    private boolean inSpawnProtection(Player player) {
        if (!plugin.state().toggleValue(Setting.SPAWN_PROTECTION)) {
            return false;
        }
        ConfigurationSection protection = config().raw().getConfigurationSection("spawn-protection");
        if (protection != null && protection.getBoolean("only-before-start", false)
                && plugin.state().running()) {
            return false;
        }
        SpawnModule spawnModule = plugin.modules().byType(SpawnModule.class);
        if (spawnModule == null || !spawnModule.isEnabled()) {
            return false;
        }
        Location spawn = spawnModule.resolveActiveSpawn();
        if (spawn == null || spawn.getWorld() == null) {
            return false;
        }
        Location at = player.getLocation();
        if (at.getWorld() != spawn.getWorld()) {
            return false;
        }
        ConfigurationSection section = config().raw().getConfigurationSection("spawn-protection");
        double radius = section == null ? 16.0D : section.getDouble("radius", 16.0D);
        double vertical = section == null ? 24.0D : section.getDouble("vertical-radius", 24.0D);
        if (Math.abs(at.getY() - spawn.getY()) > vertical) {
            return false;
        }
        double dx = at.getX() - spawn.getX();
        double dz = at.getZ() - spawn.getZ();
        return dx * dx + dz * dz <= radius * radius;
    }

    private boolean spawnProtectionBlocks(String key) {
        ConfigurationSection section = config().raw().getConfigurationSection("spawn-protection");
        return section == null || section.getBoolean(key, true);
    }

    private boolean lockBlocks(String key) {
        ConfigurationSection section = config().raw().getConfigurationSection("lobby-lock");
        return section == null || section.getBoolean(key, true);
    }

    /** Rate-limited feedback so a player mashing a block does not flood chat. */
    private void notice(Player player, String messageKey) {
        long cooldown = config().raw().getLong("notice-cooldown-millis", 1500L);
        long now = System.currentTimeMillis();
        Long previous = lastNotice.get(player.getUniqueId());
        if (previous != null && now - previous < cooldown) {
            return;
        }
        lastNotice.put(player.getUniqueId(), now);
        plugin.messages().send(player, messageKey);
    }

    // ------------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (exempt(player)) {
            return;
        }
        if (lockActive() && lockBlocks("block-break")) {
            event.setCancelled(true);
            notice(player, "protection.locked-break");
            return;
        }
        if (inSpawnProtection(player) && spawnProtectionBlocks("block-break")) {
            event.setCancelled(true);
            notice(player, "protection.spawn-break");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (exempt(player)) {
            return;
        }
        if (lockActive() && lockBlocks("block-place")) {
            event.setCancelled(true);
            notice(player, "protection.locked-place");
            return;
        }
        if (inSpawnProtection(player) && spawnProtectionBlocks("block-place")) {
            event.setCancelled(true);
            notice(player, "protection.spawn-place");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        Player player = event.getPlayer();
        if (player == null || exempt(player)) {
            return;
        }
        if ((lockActive() && lockBlocks("block-place"))
                || (inSpawnProtection(player) && spawnProtectionBlocks("block-place"))) {
            event.setCancelled(true);
            notice(player, "protection.locked-place");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        if (exempt(player)) {
            return;
        }
        if ((lockActive() && lockBlocks("block-place"))
                || (inSpawnProtection(player) && spawnProtectionBlocks("block-place"))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (victim.getGameMode() == GameMode.CREATIVE || victim.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (lockActive() && lockBlocks("block-damage")) {
            event.setCancelled(true);
            return;
        }
        if (inSpawnProtection(victim) && spawnProtectionBlocks("block-damage")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(event);
        boolean explosive = isTrackedExplosion(event);
        if (attacker == null && !explosive) {
            return;
        }
        if (attacker != null) {
            if (attacker.getUniqueId().equals(victim.getUniqueId())
                    && config().raw().getBoolean("allow-self-damage", true)) {
                return;
            }
            if (exempt(attacker)) {
                return;
            }
        }

        if (!pvpAllowed()) {
            event.setCancelled(true);
            if (attacker != null) {
                notice(attacker, "protection.pvp-disabled");
            }
            return;
        }
        if (inSpawnProtection(victim) && spawnProtectionBlocks("block-pvp")) {
            event.setCancelled(true);
            if (attacker != null) {
                notice(attacker, "protection.spawn-pvp");
            }
        }
    }

    /** Resolves the player behind a hit, following projectiles to their shooter. */
    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player direct) {
            return direct;
        }
        if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            return shooter instanceof Player player ? player : null;
        }
        return null;
    }

    /**
     * Crystal and TNT damage carries no recoverable player owner, so it is gated
     * on the PvP toggle as a class instead of on an attacker.
     */
    private boolean isTrackedExplosion(EntityDamageByEntityEvent event) {
        if (!config().raw().getBoolean("treat-explosions-as-pvp", true)) {
            return false;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        return cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        if (lockActive() && lockBlocks("block-hunger")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (exempt(event.getPlayer())) {
            return;
        }
        if (lockActive() && lockBlocks("block-item-drop")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        lastNotice.remove(event.getPlayer().getUniqueId());
    }
}
