package dev.sharded.core.grave;

import dev.sharded.core.ShardedCore;
import dev.sharded.core.util.Locations;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class GraveManager implements Listener {
    private final ShardedCore plugin;
    private final NamespacedKey graveKey;
    private final Map<UUID, Grave> graves = new HashMap<>();
    private File file;

    public GraveManager(ShardedCore plugin) {
        this.plugin = plugin;
        this.graveKey = new NamespacedKey(plugin, "grave-id");
    }

    public void load() {
        graves.clear();
        file = new File(plugin.getDataFolder(), "graves.yml");
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("graves");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            Location location = Locations.read(section.getConfigurationSection("location"));
            if (location == null) {
                continue;
            }
            List<ItemStack> items = (List<ItemStack>) section.getList("contents", List.of());
            Grave grave = new Grave(
                    UUID.fromString(id),
                    UUID.fromString(section.getString("owner")),
                    section.getString("owner-name", "Unknown"),
                    location,
                    items.toArray(ItemStack[]::new),
                    parseUuid(section.getString("stand")),
                    parseUuid(section.getString("click")),
                    section.getLong("created", System.currentTimeMillis())
            );
            graves.put(grave.id(), grave);
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Grave grave : graves.values()) {
            if (grave.removed() || grave.empty()) {
                continue;
            }
            String path = "graves." + grave.id();
            yaml.set(path + ".owner", grave.owner().toString());
            yaml.set(path + ".owner-name", grave.ownerName());
            yaml.set(path + ".created", grave.createdAt());
            yaml.set(path + ".stand", grave.standId() == null ? null : grave.standId().toString());
            yaml.set(path + ".click", grave.clickId() == null ? null : grave.clickId().toString());
            Locations.write(yaml.createSection(path + ".location"), grave.location());
            yaml.set(path + ".contents", List.of(grave.contents()));
        }
        try {
            yaml.save(file);
        } catch (IOException ignored) {
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.getConfig().getBoolean("graves.enabled", true)) {
            return;
        }
        Player player = event.getEntity();
        List<ItemStack> drops = new ArrayList<>(event.getDrops());
        if (drops.isEmpty()) {
            return;
        }
        event.getDrops().clear();
        Location location = safe(player.getLocation());
        UUID id = UUID.randomUUID();
        ArmorStand stand = spawnStand(player, location);
        Interaction click = spawnClick(location);
        stand.getPersistentDataContainer().set(graveKey, PersistentDataType.STRING, id.toString());
        click.getPersistentDataContainer().set(graveKey, PersistentDataType.STRING, id.toString());
        Grave grave = new Grave(id, player.getUniqueId(), player.getName(), location,
                drops.toArray(ItemStack[]::new), stand.getUniqueId(), click.getUniqueId(), System.currentTimeMillis());
        graves.put(id, grave);
        save();
        player.sendMessage(plugin.messages().get(
                "graves.created",
                "%x%", String.valueOf(location.getBlockX()),
                "%y%", String.valueOf(location.getBlockY()),
                "%z%", String.valueOf(location.getBlockZ())
        ));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        openFrom(event.getPlayer(), event.getRightClicked(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        openFrom(event.getPlayer(), event.getRightClicked(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onManipulate(PlayerArmorStandManipulateEvent event) {
        if (graveId(event.getRightClicked()) != null) {
            event.setCancelled(true);
            open(event.getPlayer(), graveId(event.getRightClicked()));
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (graveId(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof GraveInventory holder)) {
            return;
        }
        Grave grave = holder.grave();
        grave.contents(event.getInventory().getContents());
        boolean emptied = grave.empty() || expired(grave);
        if (emptied) {
            remove(grave);
            if (event.getPlayer() instanceof Player player) {
                player.sendMessage(plugin.messages().get("graves.recovered"));
            }
        } else {
            save();
        }
    }

    private void openFrom(Player player, Entity entity, org.bukkit.event.Cancellable event) {
        UUID id = graveId(entity);
        if (id == null) {
            return;
        }
        event.setCancelled(true);
        open(player, id);
    }

    private void open(Player player, UUID id) {
        Grave grave = graves.get(id);
        if (grave == null || grave.removed()) {
            return;
        }
        if (expired(grave)) {
            remove(grave);
            player.sendMessage(plugin.messages().get("graves.expired"));
            return;
        }
        if (!grave.owner().equals(player.getUniqueId()) && !player.hasPermission("shardedcore.admin")) {
            long age = System.currentTimeMillis() - grave.createdAt();
            if (age < 5 * 60 * 1000L) {
                player.sendMessage(plugin.messages().get("graves.not-yours"));
                return;
            }
        }
        // Graves stay clickable after the first open. Only emptying or expiry removes them.
        GraveInventory holder = new GraveInventory(grave);
        Inventory inventory = Bukkit.createInventory(holder, 54, Component.text(grave.ownerName() + "'s Grave"));
        holder.inventory(inventory);
        ItemStack[] contents = grave.contents();
        if (contents != null) {
            for (int i = 0; i < Math.min(contents.length, inventory.getSize()); i++) {
                inventory.setItem(i, contents[i]);
            }
        }
        player.openInventory(inventory);
    }

    private void remove(Grave grave) {
        grave.removed(true);
        graves.remove(grave.id());
        removeEntity(grave.location().getWorld(), grave.standId());
        removeEntity(grave.location().getWorld(), grave.clickId());
        save();
    }

    private void removeEntity(World world, UUID entityId) {
        if (world == null || entityId == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(entityId);
        if (entity != null) {
            entity.remove();
        }
    }

    private boolean expired(Grave grave) {
        int minutes = plugin.getConfig().getInt("graves.expire-minutes", 60);
        return minutes > 0 && System.currentTimeMillis() - grave.createdAt() > minutes * 60_000L;
    }

    private UUID graveId(Entity entity) {
        if (entity == null) {
            return null;
        }
        String raw = entity.getPersistentDataContainer().get(graveKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private ArmorStand spawnStand(Player player, Location location) {
        return location.getWorld().spawn(location.clone().add(0, -0.2, 0), ArmorStand.class, stand -> {
            stand.setInvisible(false);
            stand.setSmall(true);
            stand.setMarker(false);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setCanPickupItems(false);
            stand.setPersistent(true);
            stand.setRemoveWhenFarAway(false);
            stand.setDisabledSlots(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
                    EquipmentSlot.HAND, EquipmentSlot.OFF_HAND);
            String title = plugin.getConfig().getString("graves.hologram", "{player}'s Grave")
                    .replace("{player}", player.getName());
            stand.customName(Component.text(title, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
            stand.setCustomNameVisible(true);
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(player);
                skull.setItemMeta(meta);
            }
            stand.getEquipment().setHelmet(skull);
        });
    }

    private Interaction spawnClick(Location location) {
        return location.getWorld().spawn(location, Interaction.class, interaction -> {
            interaction.setInteractionWidth(1.1f);
            interaction.setInteractionHeight(1.6f);
            interaction.setResponsive(true);
            interaction.setPersistent(true);
        });
    }

    private static Location safe(Location location) {
        Location clone = location.clone();
        if (clone.getY() < clone.getWorld().getMinHeight()) {
            clone.setY(clone.getWorld().getMinHeight() + 1);
        }
        return clone;
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
