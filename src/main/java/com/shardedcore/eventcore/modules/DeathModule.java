package com.shardedcore.eventcore.modules;

import com.shardedcore.eventcore.ShardedEventCore;
import com.shardedcore.eventcore.module.EventModule;
import com.shardedcore.eventcore.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns a death into a spectator plus a lootable, glowing head.
 *
 * <p>The victim's items are held in memory keyed by their UUID and stamped onto
 * the dropped head via the persistent data container. Right-clicking that head
 * releases the stash, which keeps a kill's loot in one tidy pickup instead of a
 * cloud of item entities that has to be ticked.</p>
 */
public final class DeathModule extends EventModule {

    private final Map<UUID, List<ItemStack>> stashes = new HashMap<>();

    public DeathModule(ShardedEventCore plugin) {
        super(plugin, "death", "Spectator on death plus glowing lootable player heads.");
    }

    @Override
    protected void onModuleDisable() {
        stashes.clear();
    }

    public void clearStashes() {
        stashes.clear();
    }

    // ------------------------------------------------------------------ death

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        plugin.state().markDead(victim.getUniqueId());

        if (config().raw().getBoolean("head.enabled", true)) {
            stashDrops(victim, event);
        }
        if (config().raw().getBoolean("keep-level", true)) {
            event.setKeepLevel(true);
            event.setDroppedExp(0);
        }

        GameModule game = plugin.modules().byType(GameModule.class);
        if (game != null && game.isEnabled()) {
            game.onPlayerDied(victim);
        }
    }

    /**
     * Moves the drop list into a stash and replaces it with a single head item.
     */
    private void stashDrops(Player victim, PlayerDeathEvent event) {
        List<ItemStack> carried = new ArrayList<>(event.getDrops().size());
        for (ItemStack stack : event.getDrops()) {
            if (stack != null && !stack.getType().isAir()) {
                carried.add(stack.clone());
            }
        }
        event.getDrops().clear();
        stashes.put(victim.getUniqueId(), carried);

        Location where = config().raw().getBoolean("head.drop-at-death-location", true)
                ? victim.getLocation()
                : victim.getWorld().getSpawnLocation();
        dropHead(victim, where);
    }

    private void dropHead(Player victim, Location where) {
        ItemStack head = buildHead(victim);
        Item entity = where.getWorld().dropItemNaturally(where, head);
        if (config().raw().getBoolean("head.glow", true)) {
            entity.setGlowing(true);
        }
        if (config().raw().getBoolean("head.never-despawn", true)) {
            entity.setUnlimitedLifetime(true);
        }
        if (config().raw().getBoolean("head.show-custom-name", true)) {
            entity.customName(head.getItemMeta() == null ? null : head.getItemMeta().displayName());
            entity.setCustomNameVisible(true);
        }
    }

    public ItemStack buildHead(Player victim) {
        ConfigurationSection section = config().raw().getConfigurationSection("head");
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) {
            return head;
        }
        meta.setOwningPlayer(victim);

        Map<String, String> placeholders = Map.of("%player%", victim.getName());
        String name = section == null ? "&#AD4EFF&l%player%" : section.getString("name", "&#AD4EFF&l%player%");
        meta.displayName(Text.parse(name, placeholders));

        List<String> lore = section == null ? List.of() : section.getStringList("lore");
        if (!lore.isEmpty()) {
            List<Component> rendered = Text.parseLore(lore, placeholders);
            if (!rendered.isEmpty()) {
                meta.lore(rendered);
            }
        }
        if (section == null || section.getBoolean("glow", true)) {
            meta.setEnchantmentGlintOverride(true);
        }
        meta.getPersistentDataContainer().set(plugin.deathHeadKey(), PersistentDataType.STRING,
                victim.getUniqueId().toString());
        head.setItemMeta(meta);
        return head;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!config().raw().getBoolean("spectator-on-death", true)) {
            return;
        }
        Player player = event.getPlayer();
        // Gamemode changes made inside the respawn event do not survive it.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && plugin.state().isDead(player.getUniqueId())) {
                player.setGameMode(GameMode.SPECTATOR);
            }
        });
    }

    // ---------------------------------------------------------------- release

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack held = event.getItem();
        if (held == null || held.getType() != Material.PLAYER_HEAD) {
            return;
        }
        org.bukkit.inventory.meta.ItemMeta meta = held.getItemMeta();
        if (meta == null) {
            return;
        }
        String owner = meta.getPersistentDataContainer()
                .get(plugin.deathHeadKey(), PersistentDataType.STRING);
        if (owner == null) {
            return;
        }
        event.setCancelled(true);
        release(event.getPlayer(), held, event.getHand(), owner);
    }

    private void release(Player opener, ItemStack head, org.bukkit.inventory.EquipmentSlot hand, String rawOwner) {
        UUID owner;
        try {
            owner = UUID.fromString(rawOwner);
        } catch (IllegalArgumentException exception) {
            return;
        }
        List<ItemStack> stash = stashes.remove(owner);
        if (stash == null || stash.isEmpty()) {
            plugin.messages().send(opener, "death.head-empty");
            if (config().raw().getBoolean("release.consume-empty-head", true)) {
                consume(opener, head, hand);
            }
            return;
        }

        Location where = opener.getLocation();
        for (ItemStack stack : stash) {
            where.getWorld().dropItemNaturally(where, stack);
        }
        if (config().raw().getBoolean("release.consume-head", true)) {
            consume(opener, head, hand);
        }
        plugin.messages().send(opener, "death.head-released", "%items%", Integer.toString(stash.size()));
    }

    private void consume(Player player, ItemStack head, org.bukkit.inventory.EquipmentSlot hand) {
        int amount = head.getAmount();
        if (amount > 1) {
            head.setAmount(amount - 1);
            player.updateInventory();
            return;
        }
        if (hand == org.bukkit.inventory.EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(null);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
        player.updateInventory();
    }
}
