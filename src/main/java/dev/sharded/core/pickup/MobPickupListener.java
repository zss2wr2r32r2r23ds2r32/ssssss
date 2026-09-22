package dev.sharded.core.pickup;

import dev.sharded.core.ShardedCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class MobPickupListener implements Listener {
    private final ShardedCore plugin;
    private final NamespacedKey typeKey;

    public MobPickupListener(ShardedCore plugin) {
        this.plugin = plugin;
        this.typeKey = new NamespacedKey(plugin, "captured_type");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!plugin.getConfig().getBoolean("pickup.enabled", true)) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("shardedcore.pickup")) {
            return;
        }
        if (plugin.getConfig().getBoolean("pickup.require-sneak", true) && !player.isSneaking()) {
            return;
        }
        if (!(event.getRightClicked() instanceof LivingEntity living) || living instanceof Player) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() != Material.AIR) {
            return;
        }
        if (isBlocked(living.getType())) {
            player.sendMessage(plugin.messages().get("pickup.failed"));
            return;
        }
        ItemStack captured = capture(living);
        if (captured == null) {
            player.sendMessage(plugin.messages().get("pickup.failed"));
            return;
        }
        event.setCancelled(true);
        living.remove();
        player.getInventory().setItemInMainHand(captured);
        player.sendMessage(plugin.messages().get("pickup.captured", "%mob%", pretty(living.getType())));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlace(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        ItemStack item = event.getItem();
        if (!isCaptured(item)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        Location place = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0, 0.5);
        place.setYaw(player.getLocation().getYaw());
        if (plugin.spawns().isInSpawnRegion(place) && !player.hasPermission("shardedcore.pickup.bypass")) {
            player.sendMessage(plugin.messages().get("pickup.spawn-blocked"));
            return;
        }
        Entity spawned = spawnCaptured(item, place);
        if (spawned == null) {
            player.sendMessage(plugin.messages().get("pickup.failed"));
            return;
        }
        if (item.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        } else {
            item.setAmount(item.getAmount() - 1);
        }
        player.sendMessage(plugin.messages().get("pickup.placed", "%mob%", pretty(spawned.getType())));
    }

    private boolean isBlocked(EntityType type) {
        Set<String> blocked = plugin.getConfig().getStringList("pickup.blocked-types").stream()
                .map(s -> s.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return blocked.contains(type.name());
    }

    private boolean isCaptured(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(typeKey, PersistentDataType.STRING);
    }

    private ItemStack capture(LivingEntity entity) {
        Material material = eggMaterial(entity.getType());
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        meta.displayName(Component.text("Captured " + pretty(entity.getType()), NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Right-click to place. Cannot be placed in spawn.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, entity.getType().name());
        item.setItemMeta(meta);
        return item;
    }

    private Entity spawnCaptured(ItemStack item, Location location) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || location.getWorld() == null) {
            return null;
        }
        String typeName = meta.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        if (typeName == null) {
            return null;
        }
        EntityType type;
        try {
            type = EntityType.valueOf(typeName);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        return location.getWorld().spawnEntity(location, type);
    }

    private static Material eggMaterial(EntityType type) {
        Material match = Material.matchMaterial(type.name() + "_SPAWN_EGG");
        return match != null ? match : Material.SADDLE;
    }

    private static String pretty(EntityType type) {
        String raw = type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }
}
