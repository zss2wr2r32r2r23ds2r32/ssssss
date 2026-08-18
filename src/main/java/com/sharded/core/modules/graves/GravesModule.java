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
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Graves: when a player dies, their items are stored in a grave shown as a
 * floating player head marker (no world blocks are replaced), with holograms
 * showing their name, despawn timer and stored XP.
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

    private final Map<String, Grave> gravesByLocation = new LinkedHashMap<>();
    private NamespacedKey hologramKey;
    private NamespacedKey graveMarkerKey;
    /** Legacy key for cleaning up old player-head blocks. */
    private NamespacedKey graveBlockKey;
    private BukkitTask tickTask;

    public GravesModule(ShardedCore plugin) {
        super(plugin, "graves");
    }

    @Override
    protected void onEnable() {
        hologramKey = new NamespacedKey(plugin, "grave_hologram");
        graveMarkerKey = new NamespacedKey(plugin, "grave_marker");
        graveBlockKey = new NamespacedKey(plugin, "grave_block");
        registerCommand("graves", this);
        loadGraves();
        for (Grave grave : List.copyOf(gravesByLocation.values())) {
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
        for (Grave grave : gravesByLocation.values()) {
            despawnHolograms(grave);
            despawnMarker(grave);
        }
        saveGraves();
        gravesByLocation.clear();
    }

    /* ----------------------------- creation ----------------------------- */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        if (event.getKeepInventory()) return;
        if (!player.hasPermission("sharded.graves.use")) return;
        List<String> worlds = config.getStringList("enabled-worlds");
        if (!worlds.isEmpty() && !worlds.contains(player.getWorld().getName())) return;

        List<ItemStack> items = new ArrayList<>();
        for (ItemStack drop : event.getDrops()) {
            if (drop != null && !drop.getType().isAir()) items.add(drop.clone());
        }
        if (items.isEmpty()) {
            for (ItemStack stack : player.getInventory().getContents()) {
                if (stack != null && !stack.getType().isAir()) items.add(stack.clone());
            }
            ItemStack off = player.getInventory().getItemInOffHand();
            if (off != null && !off.getType().isAir()) items.add(off.clone());
        }
        int xp = config.getBoolean("store-xp", true) ? event.getDroppedExp() : 0;
        if (items.isEmpty() && xp <= 0) return;

        Location location = findGraveLocation(player.getLocation());
        if (location == null) return;

        event.getDrops().clear();
        player.getInventory().clear();
        if (config.getBoolean("store-xp", true)) event.setDroppedExp(0);

        long lifetime = config.getLong("expire-seconds", 300L);
        Grave grave = new Grave(UUID.randomUUID(), player.getUniqueId(), player.getName(), location,
                items, xp, xp <= 0, System.currentTimeMillis(), System.currentTimeMillis() + lifetime * 1000L);
        gravesByLocation.put(locationKey(location), grave);
        spawnMarker(grave);
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
        int x = deathLocation.getBlockX();
        int z = deathLocation.getBlockZ();
        int y = Math.max(deathLocation.getBlockY(), world.getHighestBlockYAt(x, z));
        Location grave = new Location(world, x + 0.5, y + 0.15, z + 0.5);
        for (int attempt = 0; attempt < 5; attempt++) {
            if (!gravesByLocation.containsKey(locationKey(grave))) return grave;
            grave = grave.clone().add(0, 0.5, 0);
        }
        return grave;
    }

    private ItemStack playerHead(UUID owner) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
        head.setItemMeta(meta);
        return head;
    }

    private void spawnMarker(Grave grave) {
        if (grave.markerEntityId != null) {
            Entity existing = Bukkit.getEntity(grave.markerEntityId);
            if (existing != null && !existing.isDead()) return;
        }
        World world = grave.location.getWorld();
        if (world == null || !grave.location.isChunkLoaded()) return;

        ArmorStand stand = world.spawn(grave.location, ArmorStand.class, entity -> {
            entity.setInvisible(true);
            entity.setMarker(true);
            entity.setSmall(true);
            entity.setGravity(false);
            entity.setBasePlate(false);
            entity.setArms(false);
            entity.setPersistent(false);
            entity.getEquipment().setHelmet(playerHead(grave.owner));
            entity.getPersistentDataContainer().set(graveMarkerKey, PersistentDataType.STRING, grave.id.toString());
        });
        grave.markerEntityId = stand.getUniqueId();
    }

    private void despawnMarker(Grave grave) {
        if (grave.markerEntityId == null) return;
        Entity entity = Bukkit.getEntity(grave.markerEntityId);
        if (entity != null) entity.remove();
        grave.markerEntityId = null;
    }

    private Grave graveFromEntity(Entity entity) {
        if (!(entity instanceof ArmorStand stand)) return null;
        String id = stand.getPersistentDataContainer().get(graveMarkerKey, PersistentDataType.STRING);
        if (id == null) return null;
        for (Grave grave : gravesByLocation.values()) {
            if (grave.id.toString().equals(id)) return grave;
        }
        return null;
    }

    /** Removes legacy player-head blocks from older versions. */
    private void cleanupLegacyBlock(Location location) {
        Block block = location.getBlock();
        if (block.getType() != Material.PLAYER_HEAD) return;
        if (!(block.getState() instanceof Skull skull)) return;
        String id = skull.getPersistentDataContainer().get(graveBlockKey, PersistentDataType.STRING);
        if (id != null) block.setType(Material.AIR, false);
    }

    private void restoreGraveInWorld(Grave grave) {
        cleanupLegacyBlock(grave.location);
        spawnMarker(grave);
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
            Location lineLocation = grave.location.clone().add(0, baseHeight - i * 0.28, 0);
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
        for (Entity entity : world.getNearbyEntities(location, 2, 3, 2)) {
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
        if (gravesByLocation.isEmpty()) return;
        for (Grave grave : List.copyOf(gravesByLocation.values())) {
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
        for (Grave grave : gravesByLocation.values()) {
            if (grave.location.getWorld() == null || !grave.location.getWorld().equals(event.getWorld())) continue;
            if (grave.location.getBlockX() >> 4 != event.getChunk().getX()
                    || grave.location.getBlockZ() >> 4 != event.getChunk().getZ()) continue;
            if (!grave.hologramsSpawned || grave.markerEntityId == null
                    || Bukkit.getEntity(grave.markerEntityId) == null) {
                restoreGraveInWorld(grave);
            }
        }
    }

    /* ----------------------------- opening ----------------------------- */

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Grave grave = graveFromEntity(event.getRightClicked());
        if (grave == null) return;
        event.setCancelled(true);
        openGrave(event.getPlayer(), grave);
    }

    private void openGrave(Player player, Grave grave) {
        int size = Math.min(54, Math.max(9, ((Math.max(grave.items.size(), 1) + 8) / 9) * 9));
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
                grave.location.getWorld().dropItemNaturally(grave.location, item);
            }
        }
        removeGrave(grave, false);
        Player owner = Bukkit.getPlayer(grave.owner);
        if (owner != null) send(owner, "grave-expired");
        saveGraves();
    }

    private void removeGrave(Grave grave, boolean dropContents) {
        despawnHolograms(grave);
        despawnMarker(grave);
        cleanupLegacyBlock(grave.location);
        if (dropContents && grave.location.getWorld() != null && grave.location.isChunkLoaded()) {
            for (ItemStack item : grave.items) {
                grave.location.getWorld().dropItemNaturally(grave.location, item);
            }
        }
        gravesByLocation.remove(locationKey(grave.location));
    }

    /* ----------------------------- admin cmd ----------------------------- */

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("sharded.graves.admin")) {
            send(sender, "no-permission");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("purge")) {
            int count = gravesByLocation.size();
            for (Grave grave : List.copyOf(gravesByLocation.values())) {
                removeGrave(grave, true);
            }
            saveGraves();
            send(sender, "purged", "%count%", String.valueOf(count));
            return true;
        }
        send(sender, "list-header", "%count%", String.valueOf(gravesByLocation.size()));
        for (Grave grave : gravesByLocation.values()) {
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

    private String locationKey(Location location) {
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
                double x = graveSection.getDouble("x", graveSection.getInt("x"));
                double y = graveSection.getDouble("y", graveSection.getInt("y"));
                double z = graveSection.getDouble("z", graveSection.getInt("z"));
                Location location = new Location(world, x, y, z);
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
                String markerId = graveSection.getString("marker-entity-id");
                if (markerId != null) {
                    try {
                        grave.markerEntityId = UUID.fromString(markerId);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                gravesByLocation.put(locationKey(location), grave);
            } catch (Exception e) {
                plugin.getLogger().warning("Skipping corrupt grave entry '" + key + "': " + e.getMessage());
            }
        }
    }

    private void saveGraves() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Grave grave : gravesByLocation.values()) {
            String path = "graves." + grave.id;
            yaml.set(path + ".owner", grave.owner.toString());
            yaml.set(path + ".owner-name", grave.ownerName);
            yaml.set(path + ".world", grave.location.getWorld() == null ? "" : grave.location.getWorld().getName());
            yaml.set(path + ".x", grave.location.getX());
            yaml.set(path + ".y", grave.location.getY());
            yaml.set(path + ".z", grave.location.getZ());
            yaml.set(path + ".xp", grave.xp);
            yaml.set(path + ".xp-claimed", grave.xpClaimed);
            yaml.set(path + ".created-at", grave.createdAt);
            yaml.set(path + ".expires-at", grave.expiresAt);
            yaml.set(path + ".items", ItemSerializer.toBase64(grave.items.toArray(new ItemStack[0])));
            if (grave.markerEntityId != null) {
                yaml.set(path + ".marker-entity-id", grave.markerEntityId.toString());
            }
        }
        try {
            yaml.save(new File(moduleFolder(), "graves-data.yml"));
        } catch (Exception e) {
            plugin.getLogger().severe("Could not save graves: " + e.getMessage());
        }
    }
}
