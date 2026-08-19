package com.sharded.core.modules.graves;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.ItemSerializer;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Graves: when a player dies, their items are stored in a grave shown as the
 * player's head, with a floating hologram showing their name, a despawn timer
 * and the stored XP. Right-click the head to open the grave and take the loot.
 */
public final class GravesModule extends Module implements CommandExecutor, TabCompleter {

    private static final class GraveHolder implements InventoryHolder {
        private final Grave grave;
        private Inventory inventory;

        private GraveHolder(Grave grave) {
            this.grave = grave;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final Map<String, Grave> gravesByBlock = new LinkedHashMap<>();
    private NamespacedKey hologramKey;
    private BukkitTask tickTask;

    public GravesModule(ShardedCore plugin) {
        super(plugin, "graves");
    }

    @Override
    protected void onEnable() {
        hologramKey = new NamespacedKey(plugin, "grave_hologram");
        registerCommand("graves", this);
        loadGraves();
        for (Grave grave : List.copyOf(gravesByBlock.values())) {
            if (grave.location.getWorld() != null && grave.location.isChunkLoaded()) {
                restoreGraveInWorld(grave);
            }
        }
        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    @Override
    protected void onDisable() {
        if (tickTask != null) tickTask.cancel();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof GraveHolder) {
                player.closeInventory();
            }
        }
        for (Grave grave : gravesByBlock.values()) {
            despawnHolograms(grave);
        }
        saveGraves();
        gravesByBlock.clear();
    }

    /* ----------------------------- creation ----------------------------- */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        if (event.getKeepInventory()) return;
        if (!player.hasPermission("sharded.graves.use")) return;
        List<String> worlds = config.getStringList("enabled-worlds");
        if (!worlds.isEmpty() && !worlds.contains(player.getWorld().getName())) return;
        if (event.getDrops().isEmpty() && event.getDroppedExp() <= 0) return;

        List<ItemStack> items = new ArrayList<>();
        for (ItemStack drop : event.getDrops()) {
            if (drop != null && !drop.getType().isAir()) items.add(drop.clone());
        }
        int xp = config.getBoolean("store-xp", true) ? event.getDroppedExp() : 0;
        if (items.isEmpty() && xp <= 0) return;

        Location location = findGraveLocation(player.getLocation());
        if (location == null) return;

        event.getDrops().clear();
        if (config.getBoolean("store-xp", true)) event.setDroppedExp(0);

        long lifetime = config.getLong("expire-seconds", 300L);
        Grave grave = new Grave(UUID.randomUUID(), player.getUniqueId(), player.getName(), location,
                items, xp, xp <= 0, System.currentTimeMillis(), System.currentTimeMillis() + lifetime * 1000L);
        gravesByBlock.put(blockKey(location), grave);
        placeGraveBlock(grave);
        spawnHolograms(grave);
        saveGraves();

        send(player, "grave-created",
                "%x%", String.valueOf(location.getBlockX()),
                "%y%", String.valueOf(location.getBlockY()),
                "%z%", String.valueOf(location.getBlockZ()),
                "%time%", Text.time(lifetime));
    }

    private Location findGraveLocation(Location deathLocation) {
        World world = deathLocation.getWorld();
        if (world == null) return null;
        int y = Math.max(world.getMinHeight() + 1, Math.min(world.getMaxHeight() - 2, deathLocation.getBlockY()));
        Location base = new Location(world, deathLocation.getBlockX(), y, deathLocation.getBlockZ());
        // Find the first air-ish block at or above the death point.
        for (int offset = 0; offset < 10 && base.getBlockY() + offset < world.getMaxHeight() - 1; offset++) {
            Location candidate = base.clone().add(0, offset, 0);
            Material type = candidate.getBlock().getType();
            if ((type.isAir() || !type.isSolid()) && !gravesByBlock.containsKey(blockKey(candidate))) {
                return candidate;
            }
        }
        return base;
    }

    private void placeGraveBlock(Grave grave) {
        Block block = grave.location.getBlock();
        block.setType(Material.PLAYER_HEAD, false);
        if (block.getState() instanceof Skull skull) {
            skull.setOwningPlayer(Bukkit.getOfflinePlayer(grave.owner));
            skull.update(true, false);
        }
    }

    /** Re-applies block + holograms for a grave loaded from disk. */
    private void restoreGraveInWorld(Grave grave) {
        if (grave.location.getBlock().getType() != Material.PLAYER_HEAD) {
            placeGraveBlock(grave);
        }
        cleanupStrayHolograms(grave.location);
        spawnHolograms(grave);
    }

    /* ----------------------------- holograms ----------------------------- */

    private void spawnHolograms(Grave grave) {
        if (grave.hologramsSpawned) return;
        World world = grave.location.getWorld();
        if (world == null || !grave.location.isChunkLoaded()) return;

        String[] lines = {
                raw("hologram-name", "%player%", grave.ownerName),
                timerLine(grave),
                raw("hologram-xp", "%xp%", String.valueOf(grave.xp))
        };
        double baseHeight = config.getDouble("hologram-height", 1.6);
        for (int i = 0; i < lines.length; i++) {
            Location lineLocation = grave.location.clone().add(0.5, baseHeight - i * 0.28, 0.5);
            TextDisplay display = world.spawn(lineLocation, TextDisplay.class, entity -> {
                entity.setBillboard(Display.Billboard.CENTER);
                entity.setPersistent(false);
                entity.setShadowed(true);
                entity.setSeeThrough(false);
                entity.getPersistentDataContainer().set(hologramKey, PersistentDataType.STRING, grave.id.toString());
            });
            display.text(Text.c(lines[i]));
            grave.hologramIds.add(display.getUniqueId());
        }
        grave.hologramsSpawned = true;
    }

    private void despawnHolograms(Grave grave) {
        for (UUID id : grave.hologramIds) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) entity.remove();
        }
        grave.hologramIds.clear();
        grave.hologramsSpawned = false;
    }

    private void cleanupStrayHolograms(Location location) {
        World world = location.getWorld();
        if (world == null) return;
        for (Entity entity : world.getNearbyEntities(location.clone().add(0.5, 1, 0.5), 2, 3, 2)) {
            if (entity instanceof TextDisplay
                    && entity.getPersistentDataContainer().has(hologramKey, PersistentDataType.STRING)) {
                entity.remove();
            }
        }
    }

    private String timerLine(Grave grave) {
        return raw("hologram-timer", "%time%", Text.time(grave.secondsLeft()));
    }

    private void tick() {
        if (gravesByBlock.isEmpty()) return;
        for (Grave grave : List.copyOf(gravesByBlock.values())) {
            if (grave.isExpired()) {
                expireGrave(grave);
                continue;
            }
            if (!grave.hologramsSpawned) continue;
            if (grave.hologramIds.size() >= 2) {
                Entity entity = Bukkit.getEntity(grave.hologramIds.get(1));
                if (entity instanceof TextDisplay display) {
                    display.text(Text.c(timerLine(grave)));
                }
            }
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Grave grave : gravesByBlock.values()) {
            if (!grave.hologramsSpawned
                    && grave.location.getWorld() != null
                    && grave.location.getWorld().equals(event.getWorld())
                    && grave.location.getBlockX() >> 4 == event.getChunk().getX()
                    && grave.location.getBlockZ() >> 4 == event.getChunk().getZ()) {
                restoreGraveInWorld(grave);
            }
        }
    }

    /* ----------------------------- opening ----------------------------- */

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        Grave grave = gravesByBlock.get(blockKey(event.getClickedBlock().getLocation()));
        if (grave == null) return;
        event.setCancelled(true);
        openGrave(event.getPlayer(), grave);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Grave grave = gravesByBlock.get(blockKey(event.getBlock().getLocation()));
        if (grave == null) return;
        event.setCancelled(true);
        openGrave(event.getPlayer(), grave);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> gravesByBlock.containsKey(blockKey(block.getLocation())));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> gravesByBlock.containsKey(blockKey(block.getLocation())));
    }

    private boolean canOpen(Player player, Grave grave) {
        if (player.getUniqueId().equals(grave.owner)) return true;
        if (player.hasPermission("sharded.graves.bypass")) return true;
        long protectSeconds = config.getLong("protect-seconds", -1L);
        if (protectSeconds < 0) return false; // protected forever
        return System.currentTimeMillis() >= grave.createdAt + protectSeconds * 1000L;
    }

    private void openGrave(Player player, Grave grave) {
        if (!canOpen(player, grave)) {
            send(player, "not-your-grave", "%player%", grave.ownerName);
            return;
        }
        int size = Math.min(54, Math.max(9, ((grave.items.size() + 8) / 9) * 9));
        GraveHolder holder = new GraveHolder(grave);
        Inventory inventory = Bukkit.createInventory(holder, size,
                Text.c(Text.apply(config.getString("gui-title", "&8Grave of &f%player%"), "%player%", grave.ownerName)));
        holder.inventory = inventory;
        for (int i = 0; i < grave.items.size() && i < size; i++) {
            inventory.setItem(i, grave.items.get(i));
        }
        if (!grave.xpClaimed && grave.xp > 0) {
            player.giveExp(grave.xp);
            grave.xpClaimed = true;
            send(player, "xp-claimed", "%xp%", String.valueOf(grave.xp));
            updateXpHologram(grave);
        }
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.7f, 0.8f);
    }

    private void updateXpHologram(Grave grave) {
        if (grave.hologramIds.size() >= 3) {
            Entity entity = Bukkit.getEntity(grave.hologramIds.get(2));
            if (entity instanceof TextDisplay display) {
                display.text(Text.c(raw("hologram-xp", "%xp%", "0")));
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof GraveHolder holder)) return;
        Grave grave = holder.grave;
        grave.items.clear();
        for (ItemStack item : event.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) grave.items.add(item.clone());
        }
        if (grave.items.isEmpty()) {
            removeGrave(grave, false);
            if (event.getPlayer() instanceof Player player) {
                send(player, "grave-emptied", "%player%", grave.ownerName);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
            }
        }
        saveGraves();
    }

    /* ----------------------------- removal ----------------------------- */

    private void expireGrave(Grave grave) {
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            if (viewer.getOpenInventory().getTopInventory().getHolder() instanceof GraveHolder holder
                    && holder.grave == grave) {
                viewer.closeInventory();
            }
        }
        boolean dropItems = config.getBoolean("drop-items-on-expire", true);
        if (dropItems && grave.location.getWorld() != null && grave.location.isChunkLoaded()) {
            for (ItemStack item : grave.items) {
                grave.location.getWorld().dropItemNaturally(grave.location.clone().add(0.5, 0.5, 0.5), item);
            }
        }
        removeGrave(grave, false);
        Player owner = Bukkit.getPlayer(grave.owner);
        if (owner != null) send(owner, "grave-expired");
        saveGraves();
    }

    private void removeGrave(Grave grave, boolean dropContents) {
        despawnHolograms(grave);
        if (grave.location.getWorld() != null && grave.location.isChunkLoaded()
                && grave.location.getBlock().getType() == Material.PLAYER_HEAD) {
            grave.location.getBlock().setType(Material.AIR, false);
        }
        if (dropContents && grave.location.getWorld() != null && grave.location.isChunkLoaded()) {
            for (ItemStack item : grave.items) {
                grave.location.getWorld().dropItemNaturally(grave.location.clone().add(0.5, 0.5, 0.5), item);
            }
        }
        gravesByBlock.remove(blockKey(grave.location));
    }

    /* ----------------------------- admin cmd ----------------------------- */

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("sharded.graves.admin")) {
            send(sender, "no-permission");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("purge")) {
            int count = gravesByBlock.size();
            for (Grave grave : List.copyOf(gravesByBlock.values())) {
                removeGrave(grave, true);
            }
            saveGraves();
            send(sender, "purged", "%count%", String.valueOf(count));
            return true;
        }
        send(sender, "list-header", "%count%", String.valueOf(gravesByBlock.size()));
        for (Grave grave : gravesByBlock.values()) {
            send(sender, "list-entry",
                    "%player%", grave.ownerName,
                    "%x%", String.valueOf(grave.location.getBlockX()),
                    "%y%", String.valueOf(grave.location.getBlockY()),
                    "%z%", String.valueOf(grave.location.getBlockZ()),
                    "%time%", Text.time(grave.secondsLeft()));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("sharded.graves.admin")) return List.of();
        if (args.length == 1) {
            return TabCompleteHelper.filter(args[0], "list", "purge");
        }
        return List.of();
    }

    /* ----------------------------- persistence ----------------------------- */

    private String blockKey(Location location) {
        return (location.getWorld() == null ? "?" : location.getWorld().getName())
                + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private void loadGraves() {
        File file = new File(moduleFolder(), "graves-data.yml");
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("graves");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                ConfigurationSection graveSection = section.getConfigurationSection(key);
                if (graveSection == null) continue;
                World world = Bukkit.getWorld(graveSection.getString("world", ""));
                if (world == null) continue;
                Location location = new Location(world,
                        graveSection.getInt("x"), graveSection.getInt("y"), graveSection.getInt("z"));
                List<ItemStack> items = new ArrayList<>();
                for (ItemStack item : ItemSerializer.fromBase64(graveSection.getString("items", ""))) {
                    if (item != null) items.add(item);
                }
                Grave grave = new Grave(UUID.fromString(key),
                        UUID.fromString(graveSection.getString("owner", "")),
                        graveSection.getString("owner-name", "Unknown"),
                        location, items,
                        graveSection.getInt("xp", 0),
                        graveSection.getBoolean("xp-claimed", false),
                        graveSection.getLong("created-at", System.currentTimeMillis()),
                        graveSection.getLong("expires-at", System.currentTimeMillis()));
                gravesByBlock.put(blockKey(location), grave);
            } catch (Exception e) {
                plugin.getLogger().warning("Skipping corrupt grave entry '" + key + "': " + e.getMessage());
            }
        }
    }

    private void saveGraves() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Grave grave : gravesByBlock.values()) {
            String path = "graves." + grave.id;
            yaml.set(path + ".owner", grave.owner.toString());
            yaml.set(path + ".owner-name", grave.ownerName);
            yaml.set(path + ".world", grave.location.getWorld() == null ? "" : grave.location.getWorld().getName());
            yaml.set(path + ".x", grave.location.getBlockX());
            yaml.set(path + ".y", grave.location.getBlockY());
            yaml.set(path + ".z", grave.location.getBlockZ());
            yaml.set(path + ".xp", grave.xp);
            yaml.set(path + ".xp-claimed", grave.xpClaimed);
            yaml.set(path + ".created-at", grave.createdAt);
            yaml.set(path + ".expires-at", grave.expiresAt);
            yaml.set(path + ".items", ItemSerializer.toBase64(grave.items.toArray(new ItemStack[0])));
        }
        try {
            yaml.save(new File(moduleFolder(), "graves-data.yml"));
        } catch (Exception e) {
            plugin.getLogger().severe("Could not save graves: " + e.getMessage());
        }
    }
}
